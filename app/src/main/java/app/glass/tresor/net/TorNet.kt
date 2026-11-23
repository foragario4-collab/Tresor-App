package app.glass.tresor.net

import app.glass.tresor.chat.ChatService
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/**
 * Kleine Internet-Verbindungen der App (Update-Prüfung, Wetter, News, Bilder):
 * Läuft Tor gerade, gehen sie über Tor – auch die Namensauflösung (DNS).
 * So sieht der Anbieter nicht einmal, dass die App z. B. GitHub oder einen Wetterdienst fragt.
 * Läuft Tor nicht, geht es direkt (die Inhalte sind trotzdem per HTTPS verschlüsselt).
 */
object TorNet {
    /** Proxy zu Tor, wenn Tor erreichbar ist, sonst direkt. */
    fun proxy(): Proxy {
        val port = ChatService.socksPort
        if (port <= 0) return Proxy.NO_PROXY
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        } catch (e: Exception) {
            Proxy.NO_PROXY
        }
    }

    fun viaTor(): Boolean = proxy() != Proxy.NO_PROXY

    /** Verbindung öffnen – über Tor, wenn möglich. Über Tor etwas längere Zeitlimits. */
    fun open(url: String): HttpURLConnection {
        val p = proxy()
        val c = URL(url).openConnection(p) as HttpURLConnection
        if (p != Proxy.NO_PROXY) {
            c.connectTimeout = 30_000
            c.readTimeout = 60_000
        } else {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
        }
        return c
    }
}
