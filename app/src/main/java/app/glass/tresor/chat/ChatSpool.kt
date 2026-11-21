package app.glass.tresor.chat

import android.content.Context
import app.glass.tresor.crypto.Crypto
import app.glass.tresor.crypto.HwKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Was der Chat-Dienst braucht, während der Tresor gesperrt ist.
 * Alles hier ist mit dem Hardware-Schlüssel des Geräts verschlüsselt (ohne dieses Handy unlesbar).
 * Nachrichten liegen hier ohnehin nur Ende-zu-Ende-verschlüsselt – der Dienst kann sie nie lesen.
 *
 *  chat/registry.bin          → welche Tresore einen Chat haben + lokaler Port
 *  chat/<slot>/inbox/<id>.bin → empfangene, noch ungelesene Pakete
 *  chat/<slot>/outbox/<id>.bin → zu sendende Pakete (Ziel-Adresse + Paket)
 *  chat/<slot>/sent/<id>      → Zustellbestätigung, wird beim Entsperren übernommen
 */
object ChatSpool {
    data class Entry(val slot: String, val port: Int)

    private fun root(ctx: Context) = File(ctx.filesDir, "chat")
    private fun registryFile(ctx: Context) = File(root(ctx), "registry.bin")
    fun slotDir(ctx: Context, slot: String) = File(root(ctx), slot)
    fun hsDir(ctx: Context, slot: String) = File(File(ctx.filesDir, "tor"), "hs_$slot")

    private fun seal(b: ByteArray) = HwKeys.pepperSeal(b)
    private fun open(b: ByteArray) = HwKeys.pepperOpen(b)

    @Synchronized
    fun registry(ctx: Context): List<Entry> = try {
        val f = registryFile(ctx)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(String(open(f.readBytes())))
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Entry(o.getString("s"), o.getInt("p")) } }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun saveRegistry(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("s", it.slot).put("p", it.port)) }
        Crypto.writeAtomic(registryFile(ctx), seal(arr.toString().toByteArray()))
    }

    /** Meldet einen Tresor für den Chat an. Gibt true zurück, wenn er neu ist (Tor muss neu starten). */
    @Synchronized
    fun register(ctx: Context, slot: String): Boolean {
        val list = registry(ctx)
        if (list.any { it.slot == slot }) return false
        val used = list.map { it.port }.toSet()
        var port: Int
        do { port = 20000 + (java.security.SecureRandom().nextInt(40000)) } while (port in used)
        saveRegistry(ctx, list + Entry(slot, port))
        return true
    }

    @Synchronized
    fun unregister(ctx: Context, slot: String) {
        saveRegistry(ctx, registry(ctx).filterNot { it.slot == slot })
        slotDir(ctx, slot).deleteRecursively()
        hsDir(ctx, slot).deleteRecursively()
    }

    /** Eigene .onion-Adresse (erst vorhanden, nachdem Tor einmal gelaufen ist). */
    fun onionOf(ctx: Context, slot: String): String? = try {
        File(hsDir(ctx, slot), "hostname").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.endsWith(".onion") }
    } catch (e: Exception) {
        null
    }

    private fun dir(ctx: Context, slot: String, name: String) = File(slotDir(ctx, slot), name).apply { mkdirs() }

    fun putInbox(ctx: Context, slot: String, frame: ByteArray) {
        Crypto.writeAtomic(File(dir(ctx, slot, "inbox"), "${System.currentTimeMillis()}_${Crypto.b64(Crypto.random(6)).replace('/', '_')}.bin"), seal(frame))
    }

    /** Liest und entfernt alle empfangenen Pakete. */
    fun drainInbox(ctx: Context, slot: String): List<ByteArray> {
        val files = File(slotDir(ctx, slot), "inbox").listFiles()?.sortedBy { it.name } ?: return emptyList()
        return files.mapNotNull { f ->
            try {
                open(f.readBytes())
            } catch (e: Exception) {
                null
            } finally {
                f.delete()
            }
        }
    }

    data class Outgoing(val file: File, val slot: String, val id: String, val to: String, val frame: ByteArray)

    fun putOutbox(ctx: Context, slot: String, id: String, toOnion: String, frame: ByteArray) {
        val o = JSONObject().put("to", toOnion).put("f", Crypto.b64(frame))
        Crypto.writeAtomic(File(dir(ctx, slot, "outbox"), "$id.bin"), seal(o.toString().toByteArray()))
    }

    fun outbox(ctx: Context): List<Outgoing> = registry(ctx).flatMap { e ->
        (File(slotDir(ctx, e.slot), "outbox").listFiles() ?: emptyArray()).mapNotNull { f ->
            try {
                val o = JSONObject(String(open(f.readBytes())))
                Outgoing(f, e.slot, f.name.removeSuffix(".bin"), o.getString("to"), Crypto.unb64(o.getString("f")))
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun markSent(ctx: Context, o: Outgoing) {
        File(dir(ctx, o.slot, "sent"), o.id).writeText("1")
        o.file.delete()
    }

    fun cancelOutgoing(ctx: Context, slot: String, id: String) {
        File(File(slotDir(ctx, slot), "outbox"), "$id.bin").delete()
    }

    /** Liest und entfernt alle Zustellbestätigungen. */
    fun drainSent(ctx: Context, slot: String): Set<String> {
        val files = File(slotDir(ctx, slot), "sent").listFiles() ?: return emptySet()
        return files.map { it.name }.toSet().also { files.forEach { f -> f.delete() } }
    }

    fun hasPendingOutbox(ctx: Context) = outbox(ctx).isNotEmpty()

    // ───── Entfernte Kontakte (für den Dienst lesbar, mit Hardware-Schlüssel verschlüsselt) ─────

    private val blockedCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Set<String>>>()

    fun setBlocked(ctx: Context, slot: String, pubs: Set<String>) {
        try {
            val f = File(dir(ctx, slot, "cfg"), "blocked.bin")
            if (pubs.isEmpty()) { f.delete(); blockedCache.remove(slot); return }
            val arr = JSONArray(); pubs.forEach { arr.put(it) }
            Crypto.writeAtomic(f, seal(arr.toString().toByteArray()))
            blockedCache.remove(slot)
        } catch (_: Exception) {
        }
    }

    fun blocked(ctx: Context, slot: String): Set<String> {
        val f = File(File(slotDir(ctx, slot), "cfg"), "blocked.bin")
        if (!f.exists()) return emptySet()
        val m = f.lastModified()
        blockedCache[slot]?.let { if (it.first == m) return it.second }
        return try {
            val arr = JSONArray(String(open(f.readBytes())))
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            blockedCache[slot] = m to set
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    // ───── Kontakt-Adressen für „Ich bin wieder da“ (mit Hardware-Schlüssel verschlüsselt) ─────

    /** Tor-Adressen der bestätigten Kontakte – der Dienst meldet sich dort, sobald er wieder online ist. */
    fun setPeers(ctx: Context, slot: String, onions: Set<String>) {
        try {
            val f = File(dir(ctx, slot, "cfg"), "peers.bin")
            if (onions.isEmpty()) { f.delete(); return }
            val arr = JSONArray(); onions.sorted().forEach { arr.put(it) }
            val bytes = arr.toString().toByteArray()
            // Nur schreiben, wenn sich etwas geändert hat
            if (f.exists() && try { open(f.readBytes()).contentEquals(bytes) } catch (e: Exception) { false }) return
            Crypto.writeAtomic(f, seal(bytes))
        } catch (_: Exception) {
        }
    }

    /** Alle Kontakt-Adressen aller angemeldeten Tresore/Konten. */
    fun peers(ctx: Context): Set<String> = registry(ctx).flatMap { e ->
        val f = File(File(slotDir(ctx, e.slot), "cfg"), "peers.bin")
        if (!f.exists()) emptyList() else try {
            val arr = JSONArray(String(open(f.readBytes())))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (ex: Exception) {
            emptyList()
        }
    }.toSet()

    /** Absender eines Pakets (öffentlicher Schlüssel steht unverschlüsselt im Kopf, der Inhalt nicht). */
    fun senderOf(frame: ByteArray): String? = try {
        JSONObject(String(frame)).optString("f").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    fun isBlocked(ctx: Context, slot: String, frame: ByteArray): Boolean {
        val s = senderOf(frame) ?: return false
        return s in blocked(ctx, slot)
    }
}
