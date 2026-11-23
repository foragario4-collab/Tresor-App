package app.glass.tresor.vault

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** Ein gespeichertes Passwort. */
data class PwEntry(
    val id: String,
    val title: String,
    val user: String = "",
    val pass: String = "",
    val url: String = "",
    val note: String = "",
    val modified: Long = System.currentTimeMillis(),
)

/** Ein Kalender-Termin. [date] = JJJJ-MM-TT, [time] = HH:MM oder leer (ganztägig). */
data class CalEvent(
    val id: String,
    val title: String,
    val date: String,
    val time: String = "",
    val note: String = "",
    val color: Int = 0,
)

/** Eine Sprachmemo-Aufnahme (der Ton liegt getrennt verschlüsselt). */
data class Memo(val id: String, val name: String, val ts: Long, val dur: Long)

/**
 * Kalender, Passwörter und Sprachmemos: liegen als verschlüsselte Datenblöcke im jeweiligen Tresor
 * (Hauptschlüssel des Tresors). Jeder Tresor hat seine eigenen – in den anderen unsichtbar.
 */
object Organizer {
    private const val PW = "org-pw"
    private const val CAL = "org-cal"
    private const val MEMOS = "org-memos"
    private fun memoBlob(id: String) = "memo-$id"

    private val rnd = SecureRandom()

    fun newId(): String {
        val b = ByteArray(9)
        rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun arr(v: Vault, name: String): JSONArray = try {
        v.readBlob(name)?.let { JSONArray(String(it, Charsets.UTF_8)) } ?: JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }

    private fun write(v: Vault, name: String, a: JSONArray) = v.writeBlob(name, a.toString().toByteArray(Charsets.UTF_8))

    // ───── Passwörter ─────

    fun passwords(v: Vault): List<PwEntry> {
        val a = arr(v, PW)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            PwEntry(
                o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("t"), o.optString("u"), o.optString("p"),
                o.optString("w"), o.optString("n"), o.optLong("m", 0L)
            )
        }.sortedBy { it.title.lowercase() }
    }

    fun savePasswords(v: Vault, list: List<PwEntry>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("t", it.title).put("u", it.user).put("p", it.pass).put("w", it.url).put("n", it.note).put("m", it.modified))
        }
        write(v, PW, a)
    }

    fun putPassword(v: Vault, e: PwEntry) = savePasswords(v, passwords(v).filter { it.id != e.id } + e)
    fun deletePassword(v: Vault, id: String) = savePasswords(v, passwords(v).filter { it.id != id })

    /** Starkes Zufallspasswort (ohne leicht verwechselbare Zeichen wie l/1/O/0). */
    fun generatePassword(length: Int = 20, symbols: Boolean = true): String {
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "23456789"
        val sym = "!#%&*+-=?@_"
        val sets = if (symbols) listOf(lower, upper, digits, sym) else listOf(lower, upper, digits)
        val all = sets.joinToString("")
        val n = length.coerceIn(8, 64)
        while (true) {
            val pw = CharArray(n) { all[rnd.nextInt(all.length)] }
            // Jede Zeichenart mindestens einmal
            if (sets.all { s -> pw.any { it in s } }) return String(pw)
        }
    }

    /** Stärke 0 (sehr schwach) … 4 (sehr stark), grob nach Länge und Zeichenvielfalt. */
    fun strength(pw: String): Int {
        if (pw.isEmpty()) return 0
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 20
        val bits = pw.length * Math.log(pool.coerceAtLeast(1).toDouble()) / Math.log(2.0)
        val unique = pw.toSet().size
        val b = if (unique <= 2) bits / 4 else bits
        return when {
            b < 28 -> 0
            b < 40 -> 1
            b < 60 -> 2
            b < 80 -> 3
            else -> 4
        }
    }

    // ───── Kalender ─────

    fun events(v: Vault): List<CalEvent> {
        val a = arr(v, CAL)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            CalEvent(
                o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("t"), o.optString("d"),
                o.optString("h"), o.optString("n"), o.optInt("c", 0)
            )
        }.sortedWith(compareBy({ it.date }, { it.time.ifEmpty { "00:00" } }, { it.title.lowercase() }))
    }

    fun saveEvents(v: Vault, list: List<CalEvent>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("d", it.date).put("h", it.time).put("n", it.note).put("c", it.color)) }
        write(v, CAL, a)
    }

    fun putEvent(v: Vault, e: CalEvent) = saveEvents(v, events(v).filter { it.id != e.id } + e)
    fun deleteEvent(v: Vault, id: String) = saveEvents(v, events(v).filter { it.id != id })

    /** Eingabe „1830“ / „18:30“ / „9“ → „18:30“ / „09:00“, ungültig → null, leer → "". */
    fun normTime(s: String): String? {
        val d = s.filter { it.isDigit() }
        if (d.isEmpty()) return ""
        val (h, m) = when (d.length) {
            1, 2 -> d.toInt() to 0
            3 -> d.substring(0, 1).toInt() to d.substring(1).toInt()
            4 -> d.substring(0, 2).toInt() to d.substring(2).toInt()
            else -> return null
        }
        if (h > 23 || m > 59) return null
        return "%02d:%02d".format(h, m)
    }

    // ───── Sprachmemos ─────

    fun memos(v: Vault): List<Memo> {
        val a = arr(v, MEMOS)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Memo(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("n"), o.optLong("ts"), o.optLong("d"))
        }.sortedByDescending { it.ts }
    }

    private fun saveMemos(v: Vault, list: List<Memo>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("ts", it.ts).put("d", it.dur)) }
        write(v, MEMOS, a)
    }

    /** Neue Aufnahme speichern: erst der Ton, dann der Eintrag (so gibt es nie einen Eintrag ohne Ton). */
    fun addMemo(v: Vault, audio: ByteArray, dur: Long): Memo {
        val list = memos(v)
        var n = list.size + 1
        while (list.any { it.name == "Aufnahme $n" }) n++
        val m = Memo(newId(), "Aufnahme $n", System.currentTimeMillis(), dur)
        v.writeBlob(memoBlob(m.id), audio)
        saveMemos(v, list + m)
        return m
    }

    fun renameMemo(v: Vault, id: String, name: String) =
        saveMemos(v, memos(v).map { if (it.id == id) it.copy(name = name.trim().take(60).ifEmpty { it.name }) else it })

    fun deleteMemo(v: Vault, id: String) {
        saveMemos(v, memos(v).filter { it.id != id })
        v.deleteBlob(memoBlob(id))
    }

    fun memoAudio(v: Vault, id: String): ByteArray? = v.readBlob(memoBlob(id))
}
