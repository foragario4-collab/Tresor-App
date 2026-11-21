package app.glass.tresor.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.glass.tresor.R
import org.torproject.jni.TorService
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Hält Tor im Hintergrund am Laufen, damit dein Handy für Kontakte erreichbar ist –
 * auch wenn der Tresor gesperrt ist. Der Dienst sieht nur verschlüsselte Pakete.
 *
 * Eingehend:  Tor-Onion-Dienst (Port 80) → 127.0.0.1:<Port des Tresors> → Posteingang
 * Ausgehend:  Postausgang → SOCKS5 (Tor) → .onion-Adresse des Kontakts
 */
class ChatService : Service() {

    companion object {
        private const val CH_SERVICE = "sync"
        private const val CH_MSG = "inbox"
        private const val ID_FG = 7001
        private const val ID_MSG = 7002
        private const val ACTION_RELOAD = "reload"
        private const val ACTION_KICK = "kick"
        private const val MAX_FRAME = 8 * 1024 * 1024

        /** „Ich bin wieder da“ – kein Inhalt, nur das Zeichen, dass dieses Handy jetzt erreichbar ist. */
        const val PING = 0x50494E47

        /** Wartezeiten zwischen Zustellversuchen an einen Kontakt, der gerade nicht erreichbar ist. */
        private val BACKOFF = longArrayOf(10_000, 20_000, 40_000, 80_000, 160_000, 300_000)

        /** Für die Anzeige im Chat: aus / verbindet / bereit. */
        @Volatile
        var state: String = "Aus"

        @Volatile
        var running = false

        /** SOCKS-Port von Tor (für Anrufe), -1 = noch nicht bereit. */
        @Volatile
        var socksPort: Int = -1

        /** Tor auch ohne Chat laufen lassen (Browser im Tor-Modus). */
        @Volatile
        var forBrowser = false

        @Volatile
        private var instance: ChatService? = null

        /** Tor-Steuerung: aktuelle Wege (Circuits) – für die Anzeige der Knoten. */
        fun torInfo(key: String): String? = try {
            instance?.tor?.torControlConnection?.getInfo(key)
        } catch (e: Exception) {
            null
        }

        /** Neue Tor-Wege anfordern (neue Identität im Browser). */
        fun newIdentity(): Boolean = try {
            instance?.tor?.torControlConnection?.signal("NEWNYM"); true
        } catch (e: Exception) {
            false
        }

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ChatService::class.java))
            } catch (_: Exception) {
            }
        }

        /** Neu starten, z. B. wenn ein Tresor neu für den Chat angemeldet wurde. */
        fun reload(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ChatService::class.java).setAction(ACTION_RELOAD))
            } catch (_: Exception) {
            }
        }

        /** Postausgang sofort versuchen. */
        fun kick(ctx: Context) {
            if (!running) {
                start(ctx); return
            }
            try {
                ctx.startService(Intent(ctx, ChatService::class.java).setAction(ACTION_KICK))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ChatService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    @Volatile
    private var tor: TorService? = null

    @Volatile
    private var bound = false
    private val listeners = ConcurrentHashMap<String, ServerSocket>()
    private val exec = Executors.newCachedThreadPool()
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val signal: java.lang.Object = java.lang.Object()
    private var loop: Thread? = null

    /** Pro Kontakt-Adresse: wann der nächste Versuch fällig ist und wie oft es schon nicht ging. */
    private val nextTry = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var lastAnnounce = 0L
    @Volatile
    private var wasReady = false
    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            tor = (binder as? TorService.LocalBinder)?.service
            wake()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tor = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels()
        val n = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("Synchronisierung aktiv")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID_FG, n, type)
        running = true
        instance = this
        state = "Verbindet …"
        exec.execute { startTor() }
        loop = Thread { runLoop() }.apply { isDaemon = true; start() }
        watchNetwork()
    }

    /** Internet wieder da (WLAN/Mobil gewechselt) → sofort zustellen und Kontakten Bescheid geben. */
    private fun watchNetwork() {
        try {
            val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    retryAllNow()
                    lastAnnounce = 0L
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    /** Alle Wartezeiten zurücksetzen und den Postausgang sofort abarbeiten. */
    private fun retryAllNow() {
        nextTry.clear()
        failures.clear()
        wake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELOAD -> exec.execute { restartTor() }
            ACTION_KICK -> retryAllNow()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        state = "Aus"
        socksPort = -1
        wake()
        netCallback?.let { cb -> try { getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } catch (_: Exception) {} }
        netCallback = null
        listeners.values.forEach { try { it.close() } catch (_: Exception) {} }
        listeners.clear()
        stopTor()
        exec.shutdownNow()
        super.onDestroy()
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Synchronisierung", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Neue Nachrichten", NotificationManager.IMPORTANCE_DEFAULT))
    }

    // ───────────────────────── Tor ─────────────────────────

    @Synchronized
    private fun startTor() {
        val reg = ChatSpool.registry(this)
        if (reg.isEmpty() && !forBrowser) {
            stopSelf(); return
        }
        // Onion-Dienste in die Tor-Konfiguration schreiben (eine Adresse pro Tresor)
        File(filesDir, "tor").mkdirs()
        val sb = StringBuilder()
        reg.forEach {
            sb.append("HiddenServiceDir ").append(ChatSpool.hsDir(this, it.slot).absolutePath).append('\n')
            sb.append("HiddenServicePort 80 127.0.0.1:").append(it.port).append('\n')
        }
        TorService.getTorrc(this).writeText(sb.toString())
        startListeners(reg)
        bound = bindService(Intent(this, TorService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    @Synchronized
    private fun stopTor() {
        if (bound) {
            try { unbindService(conn) } catch (_: Exception) {}
            bound = false
        }
        try { stopService(Intent(this, TorService::class.java)) } catch (_: Exception) {}
        tor = null
    }

    private fun restartTor() {
        state = "Verbindet …"
        stopTor()
        try { Thread.sleep(2500) } catch (_: InterruptedException) {}
        startTor()
    }

    private fun torSocksPort(): Int = try {
        tor?.socksPort ?: -1
    } catch (e: Exception) {
        -1
    }

    // ───────────────────────── Eingehend ─────────────────────────

    private fun startListeners(reg: List<ChatSpool.Entry>) {
        reg.forEach { e ->
            if (listeners.containsKey(e.slot)) return@forEach
            try {
                val ss = ServerSocket(e.port, 50, InetAddress.getByName("127.0.0.1"))
                listeners[e.slot] = ss
                exec.execute {
                    while (running && !ss.isClosed) {
                        try {
                            val s = ss.accept()
                            exec.execute { receive(s, e.slot) }
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun receive(s: Socket, slot: String) {
        var handedOver = false
        try {
            s.soTimeout = 120_000
            val inp = DataInputStream(s.getInputStream())
            val first = inp.readInt()
            if (first == PING) {
                // Ein Kontakt ist wieder online → wartende Nachrichten jetzt sofort schicken
                s.getOutputStream().apply { write(1); flush() }
                retryAllNow()
                return
            }
            if (first == CallProto.MAGIC) {
                // Anruf: Verbindung bleibt offen und geht an die Anruf-Steuerung
                val len = inp.readInt()
                if (len <= 0 || len > CallProto.MAX_HANDSHAKE) return
                val frame = ByteArray(len)
                inp.readFully(frame)
                if (ChatSpool.isBlocked(this, slot, frame)) {
                    // Entfernter Kontakt ruft an → still abweisen, kein Klingeln
                    java.io.DataOutputStream(s.getOutputStream()).apply { writeInt(CallProto.DECLINE); flush() }
                    return
                }
                handedOver = true
                Calls.onIncoming(this, slot, s, frame)
                return
            }
            val len = first
            if (len <= 0 || len > MAX_FRAME) return
            val frame = ByteArray(len)
            inp.readFully(frame)
            val blocked = ChatSpool.isBlocked(this, slot, frame)
            // Bestätigen, damit der Absender nicht ewig neu sendet – Pakete entfernter Kontakte werden verworfen
            if (!blocked) ChatSpool.putInbox(this, slot, frame)
            val out = s.getOutputStream()
            out.write(1)
            out.flush()
            if (!blocked) notifyNew(slot)
        } catch (_: Exception) {
        } finally {
            if (!handedOver) try { s.close() } catch (_: Exception) {}
        }
    }

    private fun notifyNew(slot: String) {
        if (!app.glass.tresor.App.prefs.getBoolean("chatNotify", true)) return
        // Ist genau dieser Tresor offen, zeigt die App selbst ein Banner – keine zusätzliche Mitteilung
        val open = app.glass.tresor.App.vault?.slot
        if (open != null && (slot == open || slot.startsWith("$open-"))) return
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val pi = launch?.let {
                android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val n: Notification = NotificationCompat.Builder(this, CH_MSG)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentTitle("Neue Nachricht")
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .apply { if (pi != null) setContentIntent(pi) }
                .build()
            getSystemService(NotificationManager::class.java).notify(ID_MSG, n)
        } catch (_: Exception) {
        }
    }

    // ───────────────────────── Ausgehend ─────────────────────────

    private fun wake() = synchronized(signal) { signal.notifyAll() }

    private fun runLoop() {
        while (running) {
            val port = torSocksPort()
            socksPort = port
            var waitMs = if (port > 0) 30_000L else 3_000L
            if (port > 0) {
                state = "Bereit"
                if (!wasReady) {
                    // Gerade online gekommen: sofort alles versuchen und den Kontakten Bescheid geben
                    wasReady = true
                    nextTry.clear(); failures.clear()
                    lastAnnounce = 0L
                }
                announce(port)
                val now = System.currentTimeMillis()
                // Pro Kontakt getrennt und gleichzeitig – ein offline-Kontakt hält die anderen nicht auf
                ChatSpool.outbox(this).groupBy { it.to }.forEach { (onion, list) ->
                    if (!running) return@forEach
                    val due = nextTry[onion] ?: 0L
                    if (due > now) { waitMs = minOf(waitMs, due - now); return@forEach }
                    if (!inFlight.add(onion)) return@forEach
                    exec.execute { deliver(port, onion, list) }
                }
            } else {
                wasReady = false
                if (tor != null) state = "Verbindet …"
            }
            synchronized(signal) {
                try { signal.wait(waitMs.coerceIn(1_000L, 30_000L)) } catch (_: InterruptedException) {}
            }
        }
    }

    /** Alle wartenden Pakete an einen Kontakt in Reihenfolge zustellen; beim ersten Fehler später erneut. */
    private fun deliver(port: Int, onion: String, list: List<ChatSpool.Outgoing>) {
        try {
            for (o in list) {
                if (!running) return
                if (!o.file.exists()) continue   // inzwischen zurückgezogen
                if (send(port, onion, o.frame)) {
                    ChatSpool.markSent(this, o)
                } else {
                    val n = (failures[onion] ?: 0) + 1
                    failures[onion] = n
                    nextTry[onion] = System.currentTimeMillis() + BACKOFF[minOf(n - 1, BACKOFF.size - 1)]
                    return
                }
            }
            failures.remove(onion); nextTry.remove(onion)
        } finally {
            inFlight.remove(onion)
            wake()
        }
    }

    /**
     * „Ich bin wieder da“: kurzes Zeichen an alle Kontakte, höchstens alle 2 Minuten.
     * Kontakte mit wartenden Nachrichten schicken sie dann sofort.
     */
    private fun announce(port: Int) {
        val now = System.currentTimeMillis()
        if (now - lastAnnounce < 120_000L) return
        lastAnnounce = now
        val peers = try { ChatSpool.peers(this) } catch (e: Exception) { emptySet() }
        peers.forEach { onion -> exec.execute { ping(port, onion) } }
    }

    private fun ping(socks: Int, onion: String): Boolean = try {
        Socket().use { s ->
            if (!socksConnect(s, socks, onion)) return false
            s.soTimeout = 30_000
            val out = DataOutputStream(s.getOutputStream())
            out.writeInt(PING)
            out.flush()
            s.getInputStream().read() == 1
        }
    } catch (e: Exception) {
        false
    }

    /** SOCKS5-Verbindung über Tor zu <onion>:80 aufbauen. */
    private fun socksConnect(s: Socket, socks: Int, onion: String): Boolean {
        s.connect(InetSocketAddress("127.0.0.1", socks), 10_000)
        s.soTimeout = 120_000
        val inp = DataInputStream(s.getInputStream())
        val out = DataOutputStream(s.getOutputStream())
        out.write(byteArrayOf(5, 1, 0)); out.flush()
        val greet = ByteArray(2); inp.readFully(greet)
        if (greet[0].toInt() != 5 || greet[1].toInt() != 0) return false
        val host = onion.toByteArray(Charsets.US_ASCII)
        out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()))
        out.write(host)
        out.write(byteArrayOf(0, 80))
        out.flush()
        val head = ByteArray(4); inp.readFully(head)
        if (head[1].toInt() != 0) return false
        when (head[3].toInt()) {
            1 -> inp.readFully(ByteArray(4 + 2))
            3 -> inp.readFully(ByteArray((inp.readUnsignedByte()) + 2))
            4 -> inp.readFully(ByteArray(16 + 2))
            else -> return false
        }
        return true
    }

    /** Verbindet über Tor (SOCKS5) mit der .onion-Adresse und überträgt ein Paket. */
    private fun send(socks: Int, onion: String, frame: ByteArray): Boolean {
        return try {
            Socket().use { s ->
                if (!socksConnect(s, socks, onion)) return false
                val inp = DataInputStream(s.getInputStream())
                val out = DataOutputStream(s.getOutputStream())
                // Paket: Länge + Inhalt, dann Bestätigung abwarten
                out.writeInt(frame.size)
                out.write(frame)
                out.flush()
                inp.read() == 1
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** Nach einem Neustart des Handys den Chat wieder erreichbar machen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        app.glass.tresor.App.init(context)
        if (app.glass.tresor.App.prefs.getBoolean("chatBackground", true) && ChatSpool.registry(context).isNotEmpty()) {
            ChatService.start(context)
        }
    }
}
