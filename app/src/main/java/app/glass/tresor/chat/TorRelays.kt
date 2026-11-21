package app.glass.tresor.chat

import java.util.Locale

/** Liest aus Tor, über welche Knoten (Relays) die Verbindungen gerade laufen – nur zur Anzeige. */
object TorRelays {
    data class Relay(val nick: String, val fingerprint: String, val ip: String?, val country: String?)
    data class Circuit(val id: String, val purpose: String, val relays: List<Relay>)

    /** query = Tor-GETINFO (in der App: ChatService.torInfo). */
    fun circuits(query: (String) -> String?): List<Circuit> {
        val raw = query("circuit-status") ?: return emptyList()
        val countries = HashMap<String, Pair<String?, String?>>()
        return raw.lines().mapNotNull { line -> parseLine(line) }
            .sortedBy { purposeOrder(it.second) }
            .take(6)
            .map { (head, purpose) ->
                val relays = head.second.map { (fp, nick) ->
                    val info = countries.getOrPut(fp) { lookup(fp, query) }
                    Relay(nick, fp, info.first, info.second)
                }
                Circuit(head.first, purpose, relays)
            }
    }

    /** Eine Zeile wie „7 BUILT $FP~nick,$FP~nick PURPOSE=GENERAL …“ zerlegen. */
    fun parseLine(line: String): Pair<Pair<String, List<Pair<String, String>>>, String>? {
        val parts = line.trim().split(' ')
        if (parts.size < 3 || parts[1] != "BUILT") return null
        val path = parts[2].split(',').mapNotNull { hop ->
            val h = hop.removePrefix("$")
            val fp = h.substringBefore('~').substringBefore('=')
            if (fp.length != 40) return@mapNotNull null
            val nick = if (h.contains('~')) h.substringAfter('~') else if (h.contains('=')) h.substringAfter('=') else fp.take(8)
            fp to nick
        }
        if (path.isEmpty()) return null
        val purpose = parts.firstOrNull { it.startsWith("PURPOSE=") }?.removePrefix("PURPOSE=") ?: "GENERAL"
        return (parts[0] to path) to purpose
    }

    private fun purposeOrder(p: String) = when {
        p == "GENERAL" -> 0
        p.startsWith("HS_") -> 1
        else -> 2
    }

    private fun lookup(fp: String, query: (String) -> String?): Pair<String?, String?> {
        val ns = try { query("ns/id/$fp") } catch (e: Exception) { null } ?: return null to null
        val r = ns.lines().firstOrNull { it.startsWith("r ") }?.split(' ') ?: return null to null
        val ip = r.getOrNull(6)
        val cc = ip?.let { try { query("ip-to-country/$it") } catch (e: Exception) { null } }?.trim()?.lowercase()
        return ip to cc?.takeIf { it.length == 2 && it != "??" }
    }

    fun purposeLabel(p: String): String = when {
        p == "GENERAL" -> "Verbindung ins Internet"
        p.startsWith("HS_SERVICE") -> "Deine Chat-Adresse"
        p.startsWith("HS_CLIENT") -> "Verbindung zu einem Kontakt"
        p == "CONFLUX_LINKED" -> "Verbindung ins Internet"
        else -> "Tor-Verbindung"
    }

    fun countryName(cc: String?): String = if (cc == null) "Unbekannt" else Locale("", cc.uppercase()).getDisplayCountry(Locale.GERMAN).ifBlank { cc.uppercase() }

    fun flag(cc: String?): String {
        if (cc == null || cc.length != 2) return "🏳"
        val a = Character.toChars(0x1F1E6 + (cc.uppercase()[0] - 'A'))
        val b = Character.toChars(0x1F1E6 + (cc.uppercase()[1] - 'A'))
        return String(a) + String(b)
    }

    fun roleLabel(i: Int, size: Int, purpose: String): String = when {
        i == 0 -> "Eingang (Guard)"
        i == size - 1 && purpose == "GENERAL" -> "Ausgang (Exit)"
        else -> "Mittelknoten"
    }
}
