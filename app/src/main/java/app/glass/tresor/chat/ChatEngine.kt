package app.glass.tresor.chat

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.glass.tresor.App
import app.glass.tresor.crypto.Crypto
import app.glass.tresor.vault.Vault
import org.json.JSONArray
import org.json.JSONObject

/** Einstellungen, die bei beiden Gesprächspartnern immer gleich sein müssen. */
data class ChatSettings(
    val deleteBoth: Boolean = false,   // Kontakt entfernen / Verlauf löschen gilt für beide
    val autoDeleteSec: Long = 0,       // 0 = aus, sonst Nachrichten nach so vielen Sekunden löschen
    val calls: Boolean = true,
    val voice: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().put("db", deleteBoth).put("ad", autoDeleteSec).put("ca", calls).put("vo", voice)

    companion object {
        val AUTO_DELETE = listOf(0L, 3600L, 86_400L, 7 * 86_400L)
        fun fromJson(o: JSONObject?): ChatSettings = if (o == null) ChatSettings() else ChatSettings(
            o.optBoolean("db", false),
            o.optLong("ad", 0).takeIf { it in AUTO_DELETE } ?: 0,
            o.optBoolean("ca", true),
            o.optBoolean("vo", true),
        )

        fun autoDeleteLabel(sec: Long) = when (sec) {
            0L -> "Aus"
            3600L -> "1 Stunde"
            86_400L -> "1 Tag"
            7 * 86_400L -> "1 Woche"
            else -> "${sec / 3600} Std."
        }
    }

    /** Kurze Beschreibung der Unterschiede, z. B. für das Zustimmungs-Fenster. */
    fun changesFrom(old: ChatSettings): List<String> = buildList {
        if (deleteBoth != old.deleteBoth) add("Löschen für beide: " + if (deleteBoth) "an" else "aus")
        if (autoDeleteSec != old.autoDeleteSec) add("Selbstlöschend: " + autoDeleteLabel(autoDeleteSec))
        if (calls != old.calls) add("Anrufe: " + if (calls) "erlaubt" else "aus")
        if (voice != old.voice) add("Sprachnachrichten: " + if (voice) "erlaubt" else "aus")
    }
}

data class Proposal(val id: String, val settings: ChatSettings)

data class Contact(
    val id: String,
    val name: String,
    val onion: String,
    val pub: ByteArray,
    val accepted: Boolean,   // false = Kontaktanfrage, noch nicht angenommen
    val added: Long,
    val settings: ChatSettings = ChatSettings(),
    val proposalOut: Proposal? = null,   // ich habe eine Änderung vorgeschlagen, warte auf Antwort
    val proposalIn: Proposal? = null,    // der andere hat etwas vorgeschlagen, ich muss antworten
    val removedByPeer: Boolean = false,  // der andere hat mich entfernt
    val peerPre: ChatCrypto.Pre? = null, // aktueller wechselnder Empfangsschlüssel des anderen (Version 2)
    val verified: Boolean = false,       // Sicherheitsnummer verglichen bzw. QR-Code persönlich gescannt
    val nick: String = "",               // eigene Angaben zum Kontakt – nur für dich, werden nie verschickt
    val birthday: String = "",
    val note: String = "",
    val fav: Boolean = false,            // Favorit im Telefon
) {
    /** Solange ihr euch nicht einig seid (oder man entfernt wurde), ruht der Chat. */
    val paused get() = proposalOut != null || proposalIn != null || removedByPeer
}

data class Msg(
    val id: String,
    val contact: String,
    val mine: Boolean,
    val text: String,
    val ts: Long,
    val state: Int,          // 0 = wartet, 1 = zugestellt, 2 = empfangen
    val kind: String = KIND_TEXT,
    val dur: Long = 0,       // Länge der Sprachnachricht in ms
) {
    companion object {
        const val KIND_TEXT = "text"
        const val KIND_VOICE = "voice"
        const val KIND_CALL = "call"
        const val KIND_SYS = "sys"
    }
}

/** Eigener wechselnder Empfangsschlüssel. retired = seit wann ersetzt (0 = aktuell). */
class MyPre(val id: String, val priv: ByteArray, val pub: ByteArray, val created: Long, var retired: Long = 0)

/** Visitenkarte im QR-Code: Onion-Adresse + öffentlicher Schlüssel + Anzeigename. */
data class Card(val onion: String, val pub: ByteArray, val name: String) {
    fun encode(): String {
        val json = JSONObject().put("v", 1).put("o", onion).put("k", Crypto.b64(pub)).put("n", name).toString()
        return "TRESOR1:" + Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    companion object {
        fun decode(s: String): Card? = try {
            val t = s.trim()
            require(t.startsWith("TRESOR1:"))
            val o = JSONObject(String(Base64.decode(t.removePrefix("TRESOR1:"), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            val onion = o.getString("o")
            val pub = Crypto.unb64(o.getString("k"))
            require(onion.endsWith(".onion") && onion.length in 40..80 && pub.size == 32)
            Card(onion, pub, o.optString("n").take(40))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Chat eines Tresors (bzw. eines Chat-Kontos). Identität, Kontakte und Verlauf liegen
 * verschlüsselt im Tresor. Jedes Konto hat eine eigene Tor-Adresse und eigene Kontakte.
 */
class ChatEngine private constructor(val vault: Vault, val account: String) {
    companion object {
        /** Sprachnachrichten: höchstens 5 Minuten / 3 MB. */
        const val MAX_VOICE_MS = 5 * 60_000L
        const val MAX_VOICE_BYTES = 3 * 1024 * 1024
        const val MAIN = "main"

        /** Neuer eigener Empfangsschlüssel nach so langer Zeit (nur während der Tresor offen ist). */
        const val PRE_ROTATE_MS = 24 * 3600_000L
        /** Ersetzte Empfangsschlüssel so lange behalten, damit verspätete Nachrichten noch ankommen. */
        const val PRE_KEEP_MS = 30 * 24 * 3600_000L

        /** Offene Konten des gerade entsperrten Tresors (Hauptkonto + entsperrte weitere Konten). */
        private val engines = java.util.concurrent.ConcurrentHashMap<String, ChatEngine>()

        /** Neueste Nachricht aus irgendeinem offenen Konto – für das Banner. */
        var bannerTick by mutableStateOf(0)
            private set
        @Volatile
        var bannerMsg: Pair<String, Msg>? = null
            private set

        /** Hauptkonto des gerade offenen Tresors. */
        fun of(v: Vault): ChatEngine = of(v, MAIN)

        fun of(v: Vault, account: String): ChatEngine {
            val key = v.slot + "/" + account
            engines[key]?.let { if (it.vault === v && it.available()) return it }
            engines.remove(key)?.wipeKeys()
            return ChatEngine(v, account).also { e -> e.load(); if (e.available()) engines[key] = e }
        }

        /** Alle Konten, die gerade offen sind (für Hintergrund-Abgleich, Anrufe, Banner). */
        fun openEngines(v: Vault, visibleOnly: Boolean = false): List<ChatEngine> {
            val list = mutableListOf(of(v, MAIN))
            v.folders.toList().filter { it.kind == app.glass.tresor.vault.FolderKind.CHAT && v.isFolderUnlocked(it.id) }
                .filter { !visibleOnly || !it.hidden || it.id in v.revealed }
                .forEach { list.add(of(v, it.id)) }
            return list.filter { it.isReady }
        }

        /** Konten, deren Ordner wieder gesperrt wurde, aus dem Speicher werfen. */
        fun prune(v: Vault) {
            engines.entries.removeIf { (_, e) ->
                val gone = e.vault.slot == v.slot && (e.vault !== v || !e.available())
                if (gone) e.wipeKeys()
                gone
            }
        }

        /** Beim Sperren: Schlüssel aller Konten aus dem Speicher löschen. */
        fun close() {
            engines.values.forEach { it.wipeKeys() }
            engines.clear()
        }

        /** Ein weiteres Konto endgültig entfernen (Tor-Adresse abmelden). */
        fun forgetAccount(v: Vault, account: String) {
            engines.remove(v.slot + "/" + account)?.wipeKeys()
            ChatSpool.unregister(App.ctx, spoolOf(v.slot, account))
            if (ChatSpool.registry(App.ctx).isEmpty()) ChatService.stop(App.ctx) else ChatService.reload(App.ctx)
        }

        /** Chat-Daten eines Tresors entfernen (beim Löschen des Tresors). */
        fun forget(slot: String) {
            engines.values.filter { it.vault.slot == slot }.forEach { it.wipeKeys() }
            engines.entries.removeIf { it.value.vault.slot == slot }
            ChatSpool.registry(App.ctx).filter { it.slot == slot || it.slot.startsWith("$slot-") }
                .forEach { ChatSpool.unregister(App.ctx, it.slot) }
            if (ChatSpool.registry(App.ctx).isEmpty()) ChatService.stop(App.ctx) else ChatService.reload(App.ctx)
        }

        /** Spool-Name: Hauptkonto = Tresor-Slot (wie bisher), weitere Konten "<slot>-<id>". */
        fun spoolOf(vaultSlot: String, account: String) = if (account == MAIN) vaultSlot else "$vaultSlot-$account"
    }

    /** Name im Hintergrund-Speicher (Posteingang, Tor-Adresse). */
    val spool = spoolOf(vault.slot, account)

    // Speicher: Hauptkonto im Tresor, weitere Konten im eigenen (evtl. geschützten) Ordner
    private fun readData(name: String): ByteArray? =
        if (account == MAIN) vault.readBlob(name) else vault.readFolderBlob(account, name)

    private fun writeData(name: String, data: ByteArray) =
        if (account == MAIN) vault.writeBlob(name, data) else vault.writeFolderBlob(account, name, data)

    private fun deleteData(name: String) =
        if (account == MAIN) vault.deleteBlob(name) else vault.deleteFolderBlob(account, name)

    /** Ist das Konto gerade zugänglich (Tresor offen, Konto-Ordner entsperrt)? */
    fun available(): Boolean = vault.isUnlocked && (account == MAIN || vault.isFolderUnlocked(account))

    private fun wipeKeys() {
        keys?.let { Crypto.wipe(it.priv) }
        keys = null
        myPres.forEach { Crypto.wipe(it.priv) }
        myPres.clear()
    }

    /** Anzeigename des Kontos (Ordnername bzw. „Hauptkonto“). */
    val accountName: String get() = if (account == MAIN) "Hauptkonto" else vault.folder(account)?.name ?: "Konto"

    private var keys by mutableStateOf<ChatCrypto.KeyPair?>(null)
    var myName by mutableStateOf("")
    var onion by mutableStateOf<String?>(null)
    val contacts = mutableStateListOf<Contact>()
    val messages = mutableStateListOf<Msg>()
    /** Öffentliche Schlüssel entfernter Kontakte – deren Pakete werden schon im Hintergrund verworfen. */
    private val blocked = mutableSetOf<String>()
    /** Eigene wechselnde Empfangsschlüssel (neuester = aktuell). */
    private val myPres = mutableListOf<MyPre>()
    val isReady get() = keys != null

    /** Neueste eingegangene Nachricht (für das Banner), Zähler löst die Anzeige aus. */
    var incomingTick by mutableStateOf(0)
        private set
    var lastIncoming: Msg? = null
        private set

    private val ctx: Context get() = App.ctx

    private fun load() {
        val b = readData("chat") ?: return
        try {
            val o = JSONObject(String(b))
            keys = ChatCrypto.KeyPair(Crypto.unb64(o.getString("sk")), Crypto.unb64(o.getString("pk")))
            myName = o.optString("name")
            onion = o.optString("onion").ifEmpty { null }
            val cs = o.optJSONArray("contacts") ?: JSONArray()
            contacts.clear()
            for (i in 0 until cs.length()) cs.getJSONObject(i).let {
                contacts.add(
                    Contact(
                        it.getString("id"), it.getString("name"), it.getString("onion"), Crypto.unb64(it.getString("pub")),
                        it.optBoolean("ok", true), it.optLong("added"),
                        ChatSettings.fromJson(it.optJSONObject("set")),
                        it.optJSONObject("pout")?.let { p -> Proposal(p.getString("id"), ChatSettings.fromJson(p.optJSONObject("s"))) },
                        it.optJSONObject("pin")?.let { p -> Proposal(p.getString("id"), ChatSettings.fromJson(p.optJSONObject("s"))) },
                        it.optBoolean("rm", false),
                        it.optJSONObject("pre")?.let { q -> ChatCrypto.Pre(q.getString("i"), Crypto.unb64(q.getString("k"))) },
                        it.optBoolean("ver", false),
                        it.optString("nk"),
                        it.optString("bd"),
                        it.optString("nt"),
                        it.optBoolean("fav", false),
                    )
                )
            }
            val ms = o.optJSONArray("msgs") ?: JSONArray()
            messages.clear()
            for (i in 0 until ms.length()) ms.getJSONObject(i).let {
                messages.add(Msg(it.getString("id"), it.getString("c"), it.getBoolean("m"), it.getString("t"), it.getLong("ts"), it.getInt("s"), it.optString("k", Msg.KIND_TEXT), it.optLong("d")))
            }
            blocked.clear()
            o.optJSONArray("blocked")?.let { a -> for (i in 0 until a.length()) blocked.add(a.getString(i)) }
            myPres.clear()
            o.optJSONArray("pres")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { q ->
                    myPres.add(MyPre(q.getString("i"), Crypto.unb64(q.getString("s")), Crypto.unb64(q.getString("p")), q.getLong("c"), q.optLong("r")))
                }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        val k = keys ?: return
        val o = JSONObject()
            .put("sk", Crypto.b64(k.priv)).put("pk", Crypto.b64(k.pub))
            .put("name", myName).put("onion", onion ?: "")
        val cs = JSONArray()
        contacts.toList().forEach {
            val j = JSONObject().put("id", it.id).put("name", it.name).put("onion", it.onion).put("pub", Crypto.b64(it.pub))
                .put("ok", it.accepted).put("added", it.added).put("set", it.settings.toJson()).put("rm", it.removedByPeer)
            it.proposalOut?.let { p -> j.put("pout", JSONObject().put("id", p.id).put("s", p.settings.toJson())) }
            it.proposalIn?.let { p -> j.put("pin", JSONObject().put("id", p.id).put("s", p.settings.toJson())) }
            it.peerPre?.let { q -> j.put("pre", JSONObject().put("i", q.id).put("k", Crypto.b64(q.pub))) }
            if (it.verified) j.put("ver", true)
            if (it.nick.isNotEmpty()) j.put("nk", it.nick)
            if (it.birthday.isNotEmpty()) j.put("bd", it.birthday)
            if (it.note.isNotEmpty()) j.put("nt", it.note)
            if (it.fav) j.put("fav", true)
            cs.put(j)
        }
        val ms = JSONArray()
        messages.toList().forEach {
            ms.put(JSONObject().put("id", it.id).put("c", it.contact).put("m", it.mine).put("t", it.text).put("ts", it.ts).put("s", it.state).put("k", it.kind).put("d", it.dur))
        }
        val bl = JSONArray()
        blocked.forEach { bl.put(it) }
        val pr = JSONArray()
        myPres.forEach { q -> pr.put(JSONObject().put("i", q.id).put("s", Crypto.b64(q.priv)).put("p", Crypto.b64(q.pub)).put("c", q.created).put("r", q.retired)) }
        o.put("contacts", cs).put("msgs", ms).put("blocked", bl).put("pres", pr)
        writeData("chat", o.toString().toByteArray())
        // Für den Hintergrund-Dienst: wessen Pakete sofort verworfen werden (ohne Vibration)
        ChatSpool.setBlocked(ctx, spool, blocked.toSet())
        // Für „Ich bin wieder da“: wem der Dienst Bescheid gibt, sobald er online ist
        ChatSpool.setPeers(ctx, spool, contacts.filter { it.accepted && !it.removedByPeer }.map { it.onion }.toSet())
    }

    /** Chat einrichten: Schlüssel erzeugen, Tor-Adresse anmelden. */
    fun setUp(name: String) {
        if (keys == null) keys = ChatCrypto.newKeyPair()
        myName = name.trim().take(40)
        save()
        val isNew = ChatSpool.register(ctx, spool)
        if (isNew && ChatService.running) ChatService.reload(ctx) else ChatService.start(ctx)
    }

    fun rename(name: String) {
        myName = name.trim().take(40); save()
    }

    private fun idx(contactId: String) = contacts.indexOfFirst { it.id == contactId }

    private fun update(contactId: String, f: (Contact) -> Contact) {
        val i = idx(contactId)
        if (i >= 0) contacts[i] = f(contacts[i])
    }

    private fun sys(contactId: String, text: String) {
        messages.add(Msg(newId(), contactId, false, text, System.currentTimeMillis(), 2, Msg.KIND_SYS))
    }

    private fun drop(contactId: String) {
        messages.filter { it.contact == contactId && it.mine && it.state == 0 }.forEach { ChatSpool.cancelOutgoing(ctx, spool, it.id) }
        messages.filter { it.contact == contactId && it.kind == Msg.KIND_VOICE }.forEach { deleteData(voiceBlob(it.id)) }
        messages.removeAll { it.contact == contactId }
    }

    /** Wird regelmäßig aufgerufen, solange der Tresor offen ist. */
    @Synchronized
    fun sync() {
        val k = keys ?: return
        if (!ChatService.running && App.prefs.getBoolean("chatBackground", true)) ChatService.start(ctx)
        if (onion == null) {
            ChatSpool.onionOf(ctx, spool)?.let { onion = it; save() }
        }
        var changed = false
        var newest: Msg? = null
        // Zustellbestätigungen übernehmen
        val sent = ChatSpool.drainSent(ctx, spool)
        if (sent.isNotEmpty()) {
            for (i in messages.indices) {
                val m = messages[i]
                if (m.mine && m.state == 0 && m.id in sent) messages[i] = m.copy(state = 1)
            }
            changed = true
        }
        // Empfangene Pakete entschlüsseln
        for (frame in ChatSpool.drainInbox(ctx, spool)) {
            try {
                val opened = ChatCrypto.openAny(k, frame) { id -> myPres.firstOrNull { it.id == id }?.priv }
                val p = JSONObject(String(opened.plain))
                val senderB64 = Crypto.b64(opened.senderPub)
                if (senderB64 in blocked) continue
                val from = contacts.firstOrNull { it.pub.contentEquals(opened.senderPub) }
                // Neuer wechselnder Empfangsschlüssel des anderen (steckt in jeder Nachricht)
                if (from != null && takePre(from, p.optJSONObject("pk"))) changed = true
                when (p.getString("t")) {
                    "pre" -> {}   // nur Schlüsselwechsel, oben schon übernommen
                    "hello" -> {
                        val card = Card.decode(p.getString("card")) ?: continue
                        if (!card.pub.contentEquals(opened.senderPub)) continue
                        if (from == null) {
                            contacts.add(Contact(newId(), card.name.ifBlank { "Unbekannt" }, card.onion, card.pub, accepted = false, added = System.currentTimeMillis()))
                            contacts.lastOrNull()?.let { takePre(it, p.optJSONObject("pk")) }
                            changed = true
                        } else if (from.removedByPeer) {
                            // Der andere hat mich wieder hinzugefügt
                            update(from.id) { it.copy(removedByPeer = false, onion = card.onion, settings = ChatSettings(), proposalIn = null, proposalOut = null) }
                            sys(from.id, "${from.name} hat dich wieder hinzugefügt")
                            changed = true
                        }
                    }
                    "voice" -> {
                        if (from == null || !from.accepted || !from.settings.voice) continue
                        val id = p.getString("id")
                        if (messages.none { it.id == id }) {
                            val audio = Crypto.unb64(p.getString("a"))
                            if (audio.isEmpty() || audio.size > MAX_VOICE_BYTES) continue
                            writeData(voiceBlob(id), audio)
                            val m = Msg(id, from.id, false, "Sprachnachricht", p.optLong("ts", System.currentTimeMillis()), 2, Msg.KIND_VOICE, p.optLong("dur").coerceIn(0, MAX_VOICE_MS))
                            messages.add(m); newest = m
                            changed = true
                        }
                    }
                    "msg" -> {
                        if (from == null) continue
                        val id = p.getString("id")
                        if (messages.none { it.id == id }) {
                            val m = Msg(id, from.id, false, p.getString("body").take(20_000), p.optLong("ts", System.currentTimeMillis()), 2)
                            messages.add(m); newest = m
                            changed = true
                        }
                    }
                    // ── gemeinsame Einstellungen ──
                    "cfg" -> {
                        if (from == null || !from.accepted) continue
                        val prop = Proposal(p.getString("id"), ChatSettings.fromJson(p.optJSONObject("s")))
                        // Ein Vorschlag des anderen ersetzt meinen offenen Vorschlag – ich bekomme das Fenster
                        update(from.id) { it.copy(proposalIn = prop, proposalOut = null) }
                        sys(from.id, "${from.name} möchte die Chat-Einstellungen ändern")
                        changed = true
                    }
                    "cfgok" -> {
                        if (from == null) continue
                        val s = ChatSettings.fromJson(p.optJSONObject("s"))
                        val id = p.getString("id")
                        update(from.id) {
                            it.copy(
                                settings = s,
                                proposalOut = if (it.proposalOut?.id == id) null else it.proposalOut,
                                proposalIn = if (it.proposalIn?.settings == s) null else it.proposalIn,
                            )
                        }
                        sys(from.id, "${from.name} hat zugestimmt – Einstellungen gelten jetzt für beide")
                        changed = true
                    }
                    "cfgno" -> {
                        if (from == null) continue
                        val id = p.getString("id")
                        val withdrawn = p.optBoolean("w", false)
                        update(from.id) {
                            if (withdrawn) it.copy(proposalIn = if (it.proposalIn?.id == id) null else it.proposalIn)
                            else it.copy(proposalOut = if (it.proposalOut?.id == id) null else it.proposalOut)
                        }
                        sys(from.id, if (withdrawn) "${from.name} hat den Vorschlag zurückgenommen" else "${from.name} hat abgelehnt – alles bleibt wie es war")
                        changed = true
                    }
                    // ── Entfernen / Löschen ──
                    "bye" -> {
                        if (from == null) continue
                        val del = p.optBoolean("del", false) && from.settings.deleteBoth
                        if (!from.accepted) {
                            contacts.removeAll { it.id == from.id }   // offene Anfrage einfach weg
                        } else {
                            if (del) drop(from.id)
                            update(from.id) { it.copy(removedByPeer = true, proposalIn = null, proposalOut = null) }
                            sys(from.id, if (del) "${from.name} hat dich entfernt und den Chat für beide gelöscht" else "${from.name} hat dich als Kontakt entfernt")
                        }
                        changed = true
                    }
                    "wipe" -> {
                        if (from == null || !from.settings.deleteBoth) continue
                        drop(from.id)
                        sys(from.id, "${from.name} hat den Verlauf für beide gelöscht")
                        changed = true
                    }
                }
            } catch (_: Exception) {
                // nicht für uns, beschädigt oder manipuliert → verwerfen
            }
        }
        if (pruneExpired()) changed = true
        if (rotatePre()) changed = true
        if (changed) save()
        newest?.let {
            lastIncoming = it; incomingTick++
            bannerMsg = account to it; bannerTick++
        }
    }

    // ───────────────────────── Wechselnde Schlüssel ─────────────────────────

    /** Übernimmt einen neuen Empfangsschlüssel des Kontakts. true = geändert. */
    private fun takePre(c: Contact, pk: JSONObject?): Boolean {
        if (pk == null) return false
        val id = pk.optString("i"); val pub = try { Crypto.unb64(pk.optString("k")) } catch (e: Exception) { return false }
        if (id.isEmpty() || id.length > 40 || pub.size != 32) return false
        if (c.peerPre?.id == id) return false
        update(c.id) { it.copy(peerPre = ChatCrypto.Pre(id, pub)) }
        return true
    }

    private fun currentPre(): MyPre? = myPres.lastOrNull { it.retired == 0L }

    /**
     * Täglich einen neuen eigenen Empfangsschlüssel erzeugen und den Kontakten mitteilen;
     * ersetzte Schlüssel nach der Frist endgültig löschen. true = geändert.
     */
    @Synchronized
    private fun rotatePre(): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        val cur = currentPre()
        if (cur == null || now - cur.created >= PRE_ROTATE_MS) {
            val kp = ChatCrypto.newKeyPair()
            cur?.retired = now
            myPres.add(MyPre(newId().take(16), kp.priv, kp.pub, now))
            changed = true
            // Allen Kontakten Bescheid geben (ältere App-Versionen ignorieren das einfach)
            contacts.filter { it.accepted && !it.removedByPeer }.forEach { c -> control(c, JSONObject().put("t", "pre")) }
        }
        val old = myPres.filter { it.retired > 0 && now - it.retired > PRE_KEEP_MS }
        if (old.isNotEmpty()) {
            old.forEach { Crypto.wipe(it.priv) }
            myPres.removeAll(old.toSet())
            changed = true
        }
        return changed
    }

    /**
     * Paket für einen Kontakt verschlüsseln. Unser aktueller Empfangsschlüssel reist immer mit.
     * Kennt der Kontakt Version 2 (er hat uns schon einen Empfangsschlüssel geschickt), wird mit
     * seinem wechselnden Schlüssel und aufgefüllter Größe verschlüsselt, sonst wie bisher.
     */
    private fun sealFor(pub: ByteArray, j: JSONObject): ByteArray {
        val k = keys ?: throw IllegalStateException("Chat nicht bereit")
        currentPre()?.let { q -> j.put("pk", JSONObject().put("i", q.id).put("k", Crypto.b64(q.pub))) }
        val plain = j.toString().toByteArray()
        val pre = contacts.firstOrNull { it.pub.contentEquals(pub) }?.peerPre
        return if (pre != null) ChatCrypto.seal2(k, pub, pre, plain) else ChatCrypto.seal(k, pub, plain)
    }

    /** Eigene Angaben zum Kontakt (Spitzname, Geburtstag, Notiz) – bleiben nur verschlüsselt im Tresor. */
    @Synchronized
    fun setContactInfo(c: Contact, nick: String, birthday: String, note: String) {
        update(c.id) { it.copy(nick = nick.trim().take(40), birthday = birthday.trim().take(20), note = note.trim().take(500)) }
        save()
    }

    /** Favorit im Telefon an/aus. */
    @Synchronized
    fun setFavorite(c: Contact, on: Boolean) {
        update(c.id) { it.copy(fav = on) }
        save()
    }

    /** Sicherheitsnummer verglichen → Kontakt als geprüft markieren (oder wieder zurücknehmen). */
    @Synchronized
    fun setVerified(c: Contact, v: Boolean) {
        update(c.id) { it.copy(verified = v) }
        save()
    }

    /** Selbstlöschende Nachrichten entfernen (bei beiden gleich eingestellt). */
    private fun pruneExpired(): Boolean {
        val now = System.currentTimeMillis()
        val expired = messages.filter { m ->
            val ttl = contacts.firstOrNull { it.id == m.contact }?.settings?.autoDeleteSec ?: 0
            ttl > 0 && m.kind != Msg.KIND_SYS && now - m.ts > ttl * 1000
        }
        if (expired.isEmpty()) return false
        expired.forEach { m ->
            if (m.mine && m.state == 0) ChatSpool.cancelOutgoing(ctx, spool, m.id)
            if (m.kind == Msg.KIND_VOICE) deleteData(voiceBlob(m.id))
        }
        val ids = expired.map { it.id }.toSet()
        messages.removeAll { it.id in ids }
        return true
    }

    private fun newId() = Crypto.b64(Crypto.random(12)).replace('/', '_').replace('+', '-')

    private fun myCard(): Card? {
        val k = keys ?: return null
        val o = onion ?: return null
        return Card(o, k.pub, myName)
    }

    fun myCode(): String? = myCard()?.encode()

    sealed interface AddResult {
        data object Ok : AddResult
        data object Invalid : AddResult
        data object Self : AddResult
        data object Exists : AddResult
        data object NotReady : AddResult
    }

    /** Kontakt aus gescanntem QR-Code / eingefügtem Text hinzufügen und ihm unsere Karte schicken. */
    @Synchronized
    fun addContact(code: String, scanned: Boolean = false): AddResult {
        val k = keys ?: return AddResult.NotReady
        val card = Card.decode(code) ?: return AddResult.Invalid
        if (card.pub.contentEquals(k.pub)) return AddResult.Self
        blocked.remove(Crypto.b64(card.pub))   // bewusst wieder hinzugefügt
        val existing = contacts.indexOfFirst { it.pub.contentEquals(card.pub) }
        if (existing >= 0) {
            val c = contacts[existing]
            if (c.accepted && !c.removedByPeer) return AddResult.Exists
            contacts[existing] = c.copy(accepted = true, onion = card.onion, removedByPeer = false, verified = c.verified || scanned)
        } else {
            contacts.add(Contact(newId(), card.name.ifBlank { "Kontakt" }, card.onion, card.pub, accepted = true, added = System.currentTimeMillis(), verified = scanned))
        }
        save()
        sendHello(card)
        return AddResult.Ok
    }

    private fun sendHello(to: Card) {
        val k = keys ?: return
        val mine = myCard() ?: return
        if (currentPre() == null) rotatePre()
        val frame = sealFor(to.pub, JSONObject().put("t", "hello").put("card", mine.encode()))
        ChatSpool.putOutbox(ctx, spool, "h" + newId(), to.onion, frame)
        ChatService.kick(ctx)
    }

    /** Steuer-Paket (Einstellungen, Entfernen …) an einen Kontakt. */
    private fun control(c: Contact, j: JSONObject) {
        val k = keys ?: return
        ChatSpool.putOutbox(ctx, spool, "x" + newId(), c.onion, sealFor(c.pub, j))
        ChatService.kick(ctx)
    }

    @Synchronized
    fun accept(c: Contact) {
        val i = idx(c.id)
        if (i < 0) return
        contacts[i] = c.copy(accepted = true)
        save()
        sendHello(Card(c.onion, c.pub, c.name))
    }

    @Synchronized
    fun renameContact(c: Contact, name: String) {
        update(c.id) { it.copy(name = name.trim().take(40).ifBlank { c.name }) }
        save()
    }

    /**
     * Kontakt entfernen. Der andere bekommt Bescheid; ist „Löschen für beide“ vereinbart,
     * wird der Chat auch bei ihm gelöscht. Seine Pakete werden danach still verworfen.
     */
    @Synchronized
    fun removeContact(c: Contact) {
        if (c.accepted && !c.removedByPeer) control(c, JSONObject().put("t", "bye").put("del", c.settings.deleteBoth))
        else if (!c.accepted) control(c, JSONObject().put("t", "bye").put("del", false))
        drop(c.id)
        contacts.removeAll { it.id == c.id }
        blocked.add(Crypto.b64(c.pub))
        save()
    }

    /** Verlauf löschen – bei „Löschen für beide“ auch beim anderen. */
    @Synchronized
    fun clearThread(c: Contact) {
        drop(c.id)
        if (c.settings.deleteBoth && c.accepted && !c.removedByPeer) control(c, JSONObject().put("t", "wipe"))
        save()
    }

    // ───────────────────────── Gemeinsame Einstellungen ─────────────────────────

    /** Änderung vorschlagen. Bis der andere zustimmt, ruht der Chat. */
    @Synchronized
    fun propose(c: Contact, s: ChatSettings) {
        val cur = contacts.getOrNull(idx(c.id)) ?: return
        if (s == cur.settings || cur.removedByPeer) return
        val prop = Proposal(newId(), s)
        update(c.id) { it.copy(proposalOut = prop) }
        control(cur, JSONObject().put("t", "cfg").put("id", prop.id).put("s", s.toJson()))
        sys(c.id, "Du hast vorgeschlagen: " + s.changesFrom(cur.settings).joinToString(", "))
        save()
    }

    @Synchronized
    fun withdraw(c: Contact) {
        val cur = contacts.getOrNull(idx(c.id)) ?: return
        val p = cur.proposalOut ?: return
        update(c.id) { it.copy(proposalOut = null) }
        control(cur, JSONObject().put("t", "cfgno").put("id", p.id).put("w", true))
        sys(c.id, "Vorschlag zurückgenommen")
        save()
    }

    @Synchronized
    fun answer(c: Contact, accept: Boolean) {
        val cur = contacts.getOrNull(idx(c.id)) ?: return
        val p = cur.proposalIn ?: return
        if (accept) {
            update(c.id) { it.copy(settings = p.settings, proposalIn = null) }
            control(cur, JSONObject().put("t", "cfgok").put("id", p.id).put("s", p.settings.toJson()))
            sys(c.id, "Angenommen – Einstellungen gelten jetzt für beide")
        } else {
            update(c.id) { it.copy(proposalIn = null) }
            control(cur, JSONObject().put("t", "cfgno").put("id", p.id))
            sys(c.id, "Abgelehnt – alles bleibt wie es war")
        }
        save()
    }

    // ───────────────────────── Senden ─────────────────────────

    @Synchronized
    fun send(c: Contact, text: String): Boolean {
        val k = keys ?: return false
        val cur = contacts.getOrNull(idx(c.id)) ?: return false
        if (cur.paused || !cur.accepted) return false
        val body = text.trim()
        if (body.isEmpty()) return false
        val id = newId()
        val ts = System.currentTimeMillis()
        val j = JSONObject().put("t", "msg").put("id", id).put("ts", ts).put("body", body)
        ChatSpool.putOutbox(ctx, spool, id, cur.onion, sealFor(cur.pub, j))
        messages.add(Msg(id, cur.id, true, body, ts, 0))
        save()
        ChatService.kick(ctx)
        return true
    }

    @Synchronized
    fun deleteMessage(m: Msg) {
        if (m.mine && m.state == 0) ChatSpool.cancelOutgoing(ctx, spool, m.id)
        if (m.kind == Msg.KIND_VOICE) deleteData(voiceBlob(m.id))
        messages.removeAll { it.id == m.id }
        save()
    }

    // ───────────────────────── Sprachnachrichten ─────────────────────────

    private fun voiceBlob(id: String) = "cv_$id"

    /** Sprachnachricht senden. Die Aufnahme liegt danach nur noch verschlüsselt im Tresor. */
    @Synchronized
    fun sendVoice(c: Contact, audio: ByteArray, durMs: Long): Boolean {
        val k = keys ?: return false
        val cur = contacts.getOrNull(idx(c.id)) ?: return false
        if (cur.paused || !cur.accepted || !cur.settings.voice) return false
        if (audio.isEmpty() || audio.size > MAX_VOICE_BYTES) return false
        val id = newId()
        val ts = System.currentTimeMillis()
        val dur = durMs.coerceIn(0, MAX_VOICE_MS)
        val j = JSONObject().put("t", "voice").put("id", id).put("ts", ts).put("dur", dur)
            .put("a", Crypto.b64(audio))
        writeData(voiceBlob(id), audio)
        ChatSpool.putOutbox(ctx, spool, id, cur.onion, sealFor(cur.pub, j))
        messages.add(Msg(id, cur.id, true, "Sprachnachricht", ts, 0, Msg.KIND_VOICE, dur))
        save()
        ChatService.kick(ctx)
        return true
    }

    fun voiceData(m: Msg): ByteArray? = readData(voiceBlob(m.id))

    // ───────────────────────── Anrufe ─────────────────────────

    internal fun keysForCall(): ChatCrypto.KeyPair? = keys

    /** Darf dieser Kontakt gerade anrufen bzw. angerufen werden? */
    fun callsAllowed(c: Contact): Boolean {
        val cur = contacts.firstOrNull { it.id == c.id } ?: return false
        return cur.accepted && !cur.paused && cur.settings.calls
    }

    /** Anruf im Verlauf vermerken (nur lokal, wird nicht verschickt). */
    @Synchronized
    fun logCall(contactId: String, mine: Boolean, text: String) {
        if (contacts.none { it.id == contactId }) return
        messages.add(Msg(newId(), contactId, mine, text, System.currentTimeMillis(), if (mine) 1 else 2, Msg.KIND_CALL))
        save()
    }

    fun safetyNumber(c: Contact): String = keys?.let { ChatCrypto.safetyNumber(it.pub, c.pub) } ?: ""

    fun thread(contactId: String) = messages.filter { it.contact == contactId }.sortedBy { it.ts }

    fun lastOf(contactId: String) = messages.filter { it.contact == contactId }.maxByOrNull { it.ts }
}
