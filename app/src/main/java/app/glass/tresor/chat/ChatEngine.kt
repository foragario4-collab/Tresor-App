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

data class Contact(
    val id: String,
    val name: String,
    val onion: String,
    val pub: ByteArray,
    val accepted: Boolean,   // false = Kontaktanfrage, noch nicht angenommen
    val added: Long,
)

data class Msg(
    val id: String,
    val contact: String,
    val mine: Boolean,
    val text: String,
    val ts: Long,
    val state: Int,          // 0 = wartet, 1 = zugestellt, 2 = empfangen
)

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
 * Chat eines Tresors. Identität, Kontakte und Verlauf liegen verschlüsselt im Tresor
 * (Blob "chat"). Jeder Tresor hat eine eigene, unabhängige Chat-Identität.
 */
class ChatEngine private constructor(val vault: Vault) {
    companion object {
        @Volatile
        private var current: ChatEngine? = null

        /** Chat des gerade offenen Tresors (null, wenn noch nicht eingerichtet). */
        fun of(v: Vault): ChatEngine {
            current?.let { if (it.vault === v && v.isUnlocked) return it }
            return ChatEngine(v).also { it.load(); current = it }
        }

        /** Beim Sperren: Schlüssel aus dem Speicher löschen. */
        fun close() {
            current?.keys?.let { Crypto.wipe(it.priv) }
            current?.keys = null
            current = null
        }

        /** Chat-Daten eines Tresors entfernen (beim Löschen des Tresors). */
        fun forget(slot: String) {
            current = null
            ChatSpool.unregister(App.ctx, slot)
            if (ChatSpool.registry(App.ctx).isEmpty()) ChatService.stop(App.ctx) else ChatService.reload(App.ctx)
        }
    }

    private var keys by mutableStateOf<ChatCrypto.KeyPair?>(null)
    var myName by mutableStateOf("")
    var onion by mutableStateOf<String?>(null)
    val contacts = mutableStateListOf<Contact>()
    val messages = mutableStateListOf<Msg>()
    val isReady get() = keys != null

    private val ctx: Context get() = App.ctx

    private fun load() {
        val b = vault.readBlob("chat") ?: return
        try {
            val o = JSONObject(String(b))
            keys = ChatCrypto.KeyPair(Crypto.unb64(o.getString("sk")), Crypto.unb64(o.getString("pk")))
            myName = o.optString("name")
            onion = o.optString("onion").ifEmpty { null }
            val cs = o.optJSONArray("contacts") ?: JSONArray()
            contacts.clear()
            for (i in 0 until cs.length()) cs.getJSONObject(i).let {
                contacts.add(Contact(it.getString("id"), it.getString("name"), it.getString("onion"), Crypto.unb64(it.getString("pub")), it.optBoolean("ok", true), it.optLong("added")))
            }
            val ms = o.optJSONArray("msgs") ?: JSONArray()
            messages.clear()
            for (i in 0 until ms.length()) ms.getJSONObject(i).let {
                messages.add(Msg(it.getString("id"), it.getString("c"), it.getBoolean("m"), it.getString("t"), it.getLong("ts"), it.getInt("s")))
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
            cs.put(JSONObject().put("id", it.id).put("name", it.name).put("onion", it.onion).put("pub", Crypto.b64(it.pub)).put("ok", it.accepted).put("added", it.added))
        }
        val ms = JSONArray()
        messages.toList().forEach {
            ms.put(JSONObject().put("id", it.id).put("c", it.contact).put("m", it.mine).put("t", it.text).put("ts", it.ts).put("s", it.state))
        }
        o.put("contacts", cs).put("msgs", ms)
        vault.writeBlob("chat", o.toString().toByteArray())
    }

    /** Chat für diesen Tresor einrichten: Schlüssel erzeugen, Tor-Adresse anmelden. */
    fun setUp(name: String) {
        if (keys == null) keys = ChatCrypto.newKeyPair()
        myName = name.trim().take(40)
        save()
        val isNew = ChatSpool.register(ctx, vault.slot)
        if (isNew && ChatService.running) ChatService.reload(ctx) else ChatService.start(ctx)
    }

    fun rename(name: String) {
        myName = name.trim().take(40); save()
    }

    /** Wird regelmäßig aufgerufen, solange der Chat offen ist. */
    fun sync() {
        val k = keys ?: return
        if (!ChatService.running && App.prefs.getBoolean("chatBackground", true)) ChatService.start(ctx)
        if (onion == null) {
            ChatSpool.onionOf(ctx, vault.slot)?.let { onion = it; save() }
        }
        var changed = false
        // Zustellbestätigungen übernehmen
        val sent = ChatSpool.drainSent(ctx, vault.slot)
        if (sent.isNotEmpty()) {
            for (i in messages.indices) {
                val m = messages[i]
                if (m.mine && m.state == 0 && m.id in sent) messages[i] = m.copy(state = 1)
            }
            changed = true
        }
        // Empfangene Pakete entschlüsseln
        for (frame in ChatSpool.drainInbox(ctx, vault.slot)) {
            try {
                val opened = ChatCrypto.open(k, frame)
                val p = JSONObject(String(opened.plain))
                val from = contacts.firstOrNull { it.pub.contentEquals(opened.senderPub) }
                when (p.getString("t")) {
                    "hello" -> {
                        val card = Card.decode(p.getString("card")) ?: continue
                        if (!card.pub.contentEquals(opened.senderPub)) continue
                        if (from == null) {
                            contacts.add(Contact(newId(), card.name.ifBlank { "Unbekannt" }, card.onion, card.pub, accepted = false, added = System.currentTimeMillis()))
                            changed = true
                        }
                    }
                    "msg" -> {
                        if (from == null) continue
                        val id = p.getString("id")
                        if (messages.none { it.id == id }) {
                            messages.add(Msg(id, from.id, false, p.getString("body").take(20_000), p.optLong("ts", System.currentTimeMillis()), 2))
                            changed = true
                        }
                    }
                }
            } catch (_: Exception) {
                // nicht für uns, beschädigt oder manipuliert → verwerfen
            }
        }
        if (changed) save()
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
    fun addContact(code: String): AddResult {
        val k = keys ?: return AddResult.NotReady
        val card = Card.decode(code) ?: return AddResult.Invalid
        if (card.pub.contentEquals(k.pub)) return AddResult.Self
        val existing = contacts.indexOfFirst { it.pub.contentEquals(card.pub) }
        if (existing >= 0) {
            val c = contacts[existing]
            if (c.accepted) return AddResult.Exists
            contacts[existing] = c.copy(accepted = true, onion = card.onion)
        } else {
            contacts.add(Contact(newId(), card.name.ifBlank { "Kontakt" }, card.onion, card.pub, accepted = true, added = System.currentTimeMillis()))
        }
        save()
        sendHello(card)
        return AddResult.Ok
    }

    private fun sendHello(to: Card) {
        val k = keys ?: return
        val mine = myCard() ?: return
        val plain = JSONObject().put("t", "hello").put("card", mine.encode()).toString().toByteArray()
        ChatSpool.putOutbox(ctx, vault.slot, "h" + newId(), to.onion, ChatCrypto.seal(k, to.pub, plain))
        ChatService.kick(ctx)
    }

    fun accept(c: Contact) {
        val i = contacts.indexOfFirst { it.id == c.id }
        if (i < 0) return
        contacts[i] = c.copy(accepted = true)
        save()
        sendHello(Card(c.onion, c.pub, c.name))
    }

    fun renameContact(c: Contact, name: String) {
        val i = contacts.indexOfFirst { it.id == c.id }
        if (i < 0) return
        contacts[i] = c.copy(name = name.trim().take(40).ifBlank { c.name })
        save()
    }

    fun removeContact(c: Contact) {
        contacts.removeAll { it.id == c.id }
        messages.filter { it.contact == c.id && it.mine && it.state == 0 }.forEach { ChatSpool.cancelOutgoing(ctx, vault.slot, it.id) }
        messages.removeAll { it.contact == c.id }
        save()
    }

    fun send(c: Contact, text: String) {
        val k = keys ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        val id = newId()
        val ts = System.currentTimeMillis()
        val plain = JSONObject().put("t", "msg").put("id", id).put("ts", ts).put("body", body).toString().toByteArray()
        ChatSpool.putOutbox(ctx, vault.slot, id, c.onion, ChatCrypto.seal(k, c.pub, plain))
        messages.add(Msg(id, c.id, true, body, ts, 0))
        save()
        ChatService.kick(ctx)
    }

    fun deleteMessage(m: Msg) {
        if (m.mine && m.state == 0) ChatSpool.cancelOutgoing(ctx, vault.slot, m.id)
        messages.removeAll { it.id == m.id }
        save()
    }

    fun clearThread(c: Contact) {
        messages.filter { it.contact == c.id && it.mine && it.state == 0 }.forEach { ChatSpool.cancelOutgoing(ctx, vault.slot, it.id) }
        messages.removeAll { it.contact == c.id }
        save()
    }

    fun safetyNumber(c: Contact): String = keys?.let { ChatCrypto.safetyNumber(it.pub, c.pub) } ?: ""

    fun thread(contactId: String) = messages.filter { it.contact == contactId }.sortedBy { it.ts }

    fun lastOf(contactId: String) = messages.filter { it.contact == contactId }.maxByOrNull { it.ts }
}
