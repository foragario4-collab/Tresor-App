package app.glass.tresor.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import app.glass.tresor.App
import app.glass.tresor.R
import app.glass.tresor.crypto.Crypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

enum class CallPhase { IDLE, CONNECTING, RINGING_OUT, INCOMING, ACTIVE, ENDED }

/**
 * Sprachanrufe direkt von Handy zu Handy über Tor – ohne Server.
 * Ein Anruf gleichzeitig. Eingehende Anrufe klingeln auch bei gesperrtem Tresor;
 * angenommen werden können sie erst nach dem Entsperren (vorher kennt die App den Anrufer nicht).
 */
object Calls {
    private const val RING_TIMEOUT_MS = 45_000L
    private const val CH_CALL = "calls"
    private const val ID_CALL = 7003

    var phase by mutableStateOf(CallPhase.IDLE)
        private set
    var peerName by mutableStateOf("")
        private set
    var startedAt by mutableStateOf(0L)
        private set
    var muted by mutableStateOf(false)
        private set
    var speaker by mutableStateOf(false)
        private set
    var endText by mutableStateOf("")
        private set

    /** Erhöht sich, wenn ein Anruf auf das Entsperren wartet. */
    var pendingTick by mutableStateOf(0)
        private set

    /** Läuft gerade ein Gespräch (oder wird aufgebaut)? Dann z. B. keine Bewegungssperre. */
    val busy get() = phase == CallPhase.CONNECTING || phase == CallPhase.RINGING_OUT || phase == CallPhase.ACTIVE

    private class Pending(val slot: String, val socket: Socket, val frame: ByteArray, val at: Long)

    private val lock = Any()
    @Volatile private var gen = 0
    @Volatile private var pending: Pending? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var channel: SecureChannel? = null
    @Volatile private var audio: CallAudio? = null
    @Volatile private var engine: ChatEngine? = null
    @Volatile private var contactId: String? = null
    @Volatile private var incomingOffer: CallProto.ParsedOffer? = null
    @Volatile private var myKeys: ChatCrypto.KeyPair? = null
    @Volatile private var outgoing = false
    @Volatile private var watcher: Thread? = null
    private var wake: PowerManager.WakeLock? = null
    private val seen = ArrayDeque<String>()
    private val sendQueue = LinkedBlockingDeque<ByteArray>()

    private val ctx: Context get() = App.ctx

    // ───────────────────────── Anrufen ─────────────────────────

    fun call(e: ChatEngine, c: Contact) {
        val keys = e.keysForCall() ?: return
        val myGen: Int
        synchronized(lock) {
            if (phase != CallPhase.IDLE && phase != CallPhase.ENDED || pending != null) {
                App.toast("Es läuft bereits ein Anruf"); return
            }
            myGen = ++gen
            engine = e; contactId = c.id; myKeys = keys; outgoing = true
            peerName = c.name; muted = false; speaker = false; endText = ""
            phase = CallPhase.CONNECTING
        }
        Thread({ dial(myGen, keys, c) }, "call-dial").start()
    }

    private fun dial(myGen: Int, keys: ChatCrypto.KeyPair, c: Contact) {
        // Tor muss laufen
        if (!ChatService.running) ChatService.start(ctx)
        var port = ChatService.socksPort
        val until = System.currentTimeMillis() + 40_000
        while (port <= 0 && System.currentTimeMillis() < until && gen == myGen) {
            Thread.sleep(500); port = ChatService.socksPort
        }
        if (gen != myGen) return
        if (port <= 0) { end(myGen, "Tor ist noch nicht bereit"); return }

        val s = try {
            Socks.connect(port, c.onion, 80, 60_000)
        } catch (ex: Exception) {
            end(myGen, "Nicht erreichbar – ist das andere Handy online?"); return
        }
        synchronized(lock) {
            if (gen != myGen) { try { s.close() } catch (_: Exception) {}; return }
            socket = s
        }
        val offer = CallProto.makeOffer(keys, c.pub)
        try {
            val out = DataOutputStream(s.getOutputStream())
            val inp = DataInputStream(s.getInputStream())
            out.writeInt(CallProto.MAGIC)
            out.writeInt(offer.frame.size)
            out.write(offer.frame)
            out.flush()
            s.soTimeout = (RING_TIMEOUT_MS + 15_000).toInt()
            var answer: ByteArray? = null
            while (answer == null) {
                when (val st = inp.readInt()) {
                    CallProto.RINGING -> if (gen == myGen) phase = CallPhase.RINGING_OUT
                    CallProto.DECLINE -> { end(myGen, "Nicht angenommen"); return }
                    in 1..CallProto.MAX_HANDSHAKE -> answer = ByteArray(st).also { inp.readFully(it) }
                    else -> { end(myGen, "Verbindung fehlgeschlagen"); return }
                }
            }
            val peerEph = CallProto.parseAnswer(keys, c.pub, offer.callId, answer)
            val (sk, rk) = CallProto.sessionKeys(offer.eph, peerEph, offer.callId, keys.pub, c.pub, iAmCaller = true)
            startActive(myGen, SecureChannel(s, sk, rk))
        } catch (ex: SocketTimeoutException) {
            end(myGen, "Keine Antwort")
        } catch (ex: Exception) {
            end(myGen, if (gen == myGen) "Verbindung fehlgeschlagen" else "")
        } finally {
            Crypto.wipe(offer.eph.priv)
        }
    }

    // ───────────────────────── Angerufen werden ─────────────────────────

    /** Vom Chat-Dienst: jemand ruft an. Der Anrufer ist noch unbekannt (Tresor evtl. gesperrt). */
    fun onIncoming(c: Context, slot: String, s: Socket, frame: ByteArray) {
        val out = try { DataOutputStream(s.getOutputStream()) } catch (e: Exception) { close(s); return }
        synchronized(lock) {
            if (phase != CallPhase.IDLE && phase != CallPhase.ENDED || pending != null) {
                try { out.writeInt(CallProto.DECLINE); out.flush() } catch (_: Exception) {}
                close(s); return
            }
            try {
                out.writeInt(CallProto.RINGING); out.flush()
                s.soTimeout = 0
            } catch (e: Exception) {
                close(s); return
            }
            pending = Pending(slot, s, frame, System.currentTimeMillis())
            pendingTick++
        }
        // Ist genau dieser Tresor gerade offen, klingelt der Anruf-Bildschirm selbst – sonst eine Mitteilung
        if (App.vault?.slot != slot) notifyIncoming(c)
        val p = pending
        watcher = Thread({ watch(p, s) }, "call-watch").apply { start() }
        Thread({
            try { Thread.sleep(RING_TIMEOUT_MS) } catch (_: InterruptedException) {}
            missedIfStill(p)
        }, "call-ring").start()
    }

    /** Merkt, wenn der Anrufer auflegt, bevor angenommen wurde. Liest nichts vom eigentlichen Gespräch. */
    private fun watch(p: Pending?, s: Socket) {
        try {
            s.soTimeout = 1000
            val inp = s.getInputStream()
            while (true) {
                val waiting = synchronized(lock) { pending === p || (phase == CallPhase.INCOMING && socket === s) }
                if (!waiting) return
                try {
                    inp.read() // Vor der Antwort schickt der Anrufer nichts: alles hier heißt "aufgelegt"
                    break
                } catch (e: SocketTimeoutException) {
                    continue
                }
            }
        } catch (_: Exception) {
        }
        var g = -1
        synchronized(lock) {
            if (p != null && pending === p) {
                pending = null; close(s); cancelNotification(); return
            }
            if (phase == CallPhase.INCOMING && socket === s) g = gen
        }
        if (g >= 0) end(g, "Verpasst", missed = true)
    }

    private fun missedIfStill(p: Pending?) {
        var missed = false
        val g: Int
        synchronized(lock) {
            g = gen
            if (p != null && pending === p) {
                pending = null
                decline(p.socket)
                cancelNotification()
                return
            }
            missed = phase == CallPhase.INCOMING && p != null && socket === p.socket
        }
        if (missed) end(g, "Verpasst", missed = true)
    }

    /** Nach dem Entsperren: Anruf entschlüsseln und anzeigen, wenn er an diesen Tresor geht. */
    fun resolveIncoming(e: ChatEngine) {
        val p = pending ?: return
        if (p.slot != e.vault.slot) return
        val keys = e.keysForCall() ?: return
        val offer = try { CallProto.parseOffer(keys, p.frame) } catch (ex: Exception) { null }
        val contact = offer?.let { o -> e.contacts.firstOrNull { it.accepted && it.pub.contentEquals(o.senderPub) } }
        val id = offer?.let { Crypto.b64(it.callId) }
        val fresh = offer != null && kotlin.math.abs(System.currentTimeMillis() - offer.ts) <= CallProto.OFFER_MAX_AGE_MS
        synchronized(lock) {
            if (pending !== p) return
            pending = null
            if (offer == null || contact == null || !fresh || id == null || id in seen) {
                decline(p.socket); cancelNotification(); return
            }
            seen.addLast(id); while (seen.size > 50) seen.removeFirst()
            gen++
            socket = p.socket; incomingOffer = offer; myKeys = keys; engine = e; contactId = contact.id; outgoing = false
            peerName = contact.name; muted = false; speaker = false; endText = ""
            phase = CallPhase.INCOMING
        }
    }

    fun accept() {
        val myGen: Int
        val s: Socket
        val offer: CallProto.ParsedOffer
        val keys: ChatCrypto.KeyPair
        synchronized(lock) {
            if (phase != CallPhase.INCOMING) return
            s = socket ?: return
            offer = incomingOffer ?: return
            keys = myKeys ?: return
            myGen = gen
            phase = CallPhase.CONNECTING
        }
        cancelNotification()
        Thread({
            try {
                // Wächter beenden, bevor das Gespräch Daten liest
                watcher?.join(2500)
                val (eph, frame) = CallProto.makeAnswer(keys, offer.senderPub, offer.callId)
                val out = DataOutputStream(s.getOutputStream())
                out.writeInt(frame.size); out.write(frame); out.flush()
                val (sk, rk) = CallProto.sessionKeys(eph, offer.ephPub, offer.callId, offer.senderPub, keys.pub, iAmCaller = false)
                Crypto.wipe(eph.priv)
                startActive(myGen, SecureChannel(s, sk, rk))
            } catch (ex: Exception) {
                end(myGen, "Verbindung fehlgeschlagen")
            }
        }, "call-accept").start()
    }

    // ───────────────────────── Gespräch ─────────────────────────

    private fun startActive(myGen: Int, ch: SecureChannel) {
        val a: CallAudio
        synchronized(lock) {
            if (gen != myGen) { ch.close(); return }
            channel = ch
            sendQueue.clear()
            a = CallAudio(ctx) { f ->
                sendQueue.offerLast(f)
                while (sendQueue.size > 10) sendQueue.pollFirst() // Tor stockt → nicht endlos anstauen
            }
            audio = a
            startedAt = System.currentTimeMillis()
            phase = CallPhase.ACTIVE
        }
        a.start()
        updateWakeLock()
        // Senden: Sprache, sonst alle 2 s ein Lebenszeichen
        Thread({
            try {
                while (gen == myGen) {
                    val f = sendQueue.pollFirst(2, TimeUnit.SECONDS)
                    if (gen != myGen) break
                    if (f != null) ch.send(CallProto.T_AUDIO, f) else ch.send(CallProto.T_PING)
                }
            } catch (ex: Exception) {
                end(myGen, "Verbindung verloren")
            }
        }, "call-send").start()
        // Empfangen
        Thread({
            try {
                ch.setReadTimeout(20_000)
                while (gen == myGen) {
                    val (t, p) = ch.receive()
                    when (t) {
                        CallProto.T_AUDIO -> a.play(p)
                        CallProto.T_HANGUP -> { end(myGen, "Aufgelegt"); break }
                    }
                }
            } catch (ex: Exception) {
                end(myGen, "Verbindung verloren")
            }
        }, "call-recv").start()
    }

    fun toggleMute() {
        muted = !muted
        audio?.muted = muted
    }

    fun toggleSpeaker() {
        speaker = !speaker
        audio?.setSpeaker(speaker)
        updateWakeLock()
    }

    /** Auflegen / Abbrechen / Ablehnen – je nach Zustand. */
    fun hangup() {
        val g = gen
        when (phase) {
            CallPhase.INCOMING -> {
                synchronized(lock) { socket?.let { decline(it) }; socket = null }
                end(g, "Abgelehnt")
            }
            CallPhase.ACTIVE -> {
                val ch = channel
                Thread({ try { ch?.send(CallProto.T_HANGUP) } catch (_: Exception) {}; end(g, "Beendet") }, "call-bye").start()
            }
            CallPhase.CONNECTING, CallPhase.RINGING_OUT -> end(g, "Abgebrochen")
            else -> {}
        }
    }

    /** Tresor wird gesperrt: laufendes Gespräch beenden (wartende Anrufe dürfen weiter klingeln). */
    fun onVaultLocked() {
        if (phase != CallPhase.IDLE && phase != CallPhase.ENDED) {
            val ch = channel
            if (phase == CallPhase.ACTIVE) Thread({ try { ch?.send(CallProto.T_HANGUP) } catch (_: Exception) {} }).start()
            if (phase == CallPhase.INCOMING) socket?.let { decline(it) }
            end(gen, "Beendet")
        }
    }

    private fun end(myGen: Int, reason: String, missed: Boolean = false) {
        val wasActive: Boolean
        val started: Long
        val e: ChatEngine?
        val cid: String?
        val out: Boolean
        synchronized(lock) {
            if (gen != myGen || phase == CallPhase.IDLE || phase == CallPhase.ENDED) return
            gen++
            wasActive = phase == CallPhase.ACTIVE
            started = startedAt
            e = engine; cid = contactId; out = outgoing
            audio?.stop(); audio = null
            channel?.close(); channel = null
            socket?.let { if (phase == CallPhase.INCOMING) decline(it) else close(it) }; socket = null
            incomingOffer = null; myKeys = null; engine = null
            sendQueue.clear()
            endText = reason
            phase = CallPhase.ENDED
        }
        releaseWakeLock()
        cancelNotification()
        // Im Verlauf vermerken (nur wenn der Tresor noch offen ist)
        try {
            if (e != null && cid != null && e.vault.isUnlocked) {
                when {
                    wasActive -> e.logCall(cid, out, "Anruf · " + duration(System.currentTimeMillis() - started))
                    missed -> e.logCall(cid, false, "Verpasster Anruf")
                }
            }
        } catch (_: Exception) {
        }
        val endedGen = gen
        Thread({
            try { Thread.sleep(1800) } catch (_: InterruptedException) {}
            synchronized(lock) { if (gen == endedGen && phase == CallPhase.ENDED) phase = CallPhase.IDLE }
        }, "call-end").start()
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun decline(s: Socket) {
        try { DataOutputStream(s.getOutputStream()).apply { writeInt(CallProto.DECLINE); flush() } } catch (_: Exception) {}
        close(s)
    }

    private fun close(s: Socket) {
        try { s.close() } catch (_: Exception) {}
    }

    // ───────────────────────── Bildschirm & Mitteilung ─────────────────────────

    /** Am Ohr: Bildschirm über den Näherungssensor ausschalten (nicht bei Lautsprecher). */
    private fun updateWakeLock() {
        try {
            if (phase == CallPhase.ACTIVE && !speaker) {
                if (wake == null) {
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        wake = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tresor:call").apply {
                            acquire(4 * 60 * 60 * 1000L)
                        }
                    }
                }
            } else releaseWakeLock()
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try { wake?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wake = null
    }

    private fun notifyIncoming(c: Context) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CH_CALL, "Anrufe", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
                )
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(ch)
            val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)
            val pi = launch?.let { PendingIntent.getActivity(c, 1, it, PendingIntent.FLAG_IMMUTABLE) }
            val n = NotificationCompat.Builder(c, CH_CALL)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentTitle("Eingehender Anruf")
                .setContentText("Öffnen und entsperren zum Annehmen")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setTimeoutAfter(RING_TIMEOUT_MS)
                .setAutoCancel(true)
                .apply { if (pi != null) setContentIntent(pi) }
                .build()
            nm.notify(ID_CALL, n)
        } catch (_: Exception) {
        }
    }

    private fun cancelNotification() {
        try { ctx.getSystemService(NotificationManager::class.java).cancel(ID_CALL) } catch (_: Exception) {}
    }
}
