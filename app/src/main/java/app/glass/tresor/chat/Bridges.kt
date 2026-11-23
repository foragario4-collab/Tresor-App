package app.glass.tresor.chat

import android.content.Context
import java.io.File

/**
 * Tor-Brücken: Damit dein Internetanbieter nicht erkennt, dass du Tor benutzt.
 *
 * - „Snowflake“ (Standard): Der Tor-Verkehr läuft über WebRTC-Verbindungen zu freiwilligen Helfern
 *   und sieht aus wie ein Videoanruf. Kein Konto, keine Einrichtung.
 * - „Eigene Brücken“: Brücken-Zeilen von bridges.torproject.org (obfs4, webtunnel, snowflake).
 *
 * Die Brücken laufen über die Bibliothek IPtProxy (Guardian Project / Tor-Projekt).
 * Sie wird per Reflection angesprochen – fehlt sie, läuft Tor einfach ohne Brücken weiter.
 */
object Bridges {
    const val OFF = "off"
    const val SNOWFLAKE = "snowflake"
    const val CUSTOM = "custom"

    // Snowflake-Zugang (Stand Ende 2025, wie im Tor Browser)
    private const val BROKER = "https://bespoke-strudel-c243cc.netlify.app"
    private const val FRONTS = "vuejs.org"
    private const val AMP = "https://cdn.ampproject.org/"
    private const val ICE = "stun:stun.l.google.com:19302,stun:stun.antisip.com:3478,stun:stun.bluesip.net:3478," +
        "stun:stun.dus.net:3478,stun:stun.epygi.com:3478,stun:stun.sonetel.com:3478,stun:stun.uls.co.za:3478," +
        "stun:stun.voipgate.com:3478,stun:stun.voys.nl:3478"

    private val SNOWFLAKE_LINES = listOf(
        "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=$BROKER fronts=$FRONTS ice=$ICE utls-imitate=hellorandomizedalpn",
        "snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=$BROKER fronts=$FRONTS ice=$ICE utls-imitate=hellorandomizedalpn",
    )

    private val KNOWN = setOf("snowflake", "obfs4", "webtunnel", "meek_lite")

    @Volatile
    private var controller: Any? = null
    private val started = mutableSetOf<String>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun mode(ctx: Context): String = prefs(ctx).getString("torBridges", OFF) ?: OFF
    fun customLines(ctx: Context): String = prefs(ctx).getString("torBridgeLines", "") ?: ""

    fun setMode(ctx: Context, mode: String, custom: String? = null) {
        val e = prefs(ctx).edit().putString("torBridges", mode)
        if (custom != null) e.putString("torBridgeLines", custom.trim())
        e.apply()
    }

    /** Ist die Brücken-Bibliothek in dieser App-Version vorhanden? */
    val available: Boolean by lazy {
        try { Class.forName("IPtProxy.Controller"); true } catch (e: Throwable) { false }
    }

    /** Brücken-Zeilen aus dem Textfeld: eine pro Zeile, „Bridge “ am Anfang ist erlaubt. */
    fun parse(text: String): List<String> = text.lines()
        .map { it.trim().removePrefix("Bridge ").removePrefix("bridge ").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .filter { l -> val t = l.substringBefore(' '); t in KNOWN }

    /** Prüft eingefügte Brücken. null = in Ordnung, sonst Fehlermeldung. */
    fun check(text: String): String? {
        val raw = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (raw.isEmpty()) return "Bitte mindestens eine Brücke einfügen."
        val ok = parse(text)
        if (ok.size < raw.size) return "Nur obfs4-, webtunnel- und snowflake-Brücken werden unterstützt (eine pro Zeile)."
        return null
    }

    /**
     * Zeilen für die Tor-Konfiguration. Startet dafür die nötigen Brücken-Programme.
     * Leer, wenn Brücken aus sind oder nicht gestartet werden konnten.
     */
    @Synchronized
    fun torrc(ctx: Context): String {
        val lines = when (mode(ctx)) {
            SNOWFLAKE -> SNOWFLAKE_LINES
            CUSTOM -> parse(customLines(ctx))
            else -> emptyList()
        }
        if (lines.isEmpty() || !available) { stopAll(); return "" }
        val methods = lines.map { it.substringBefore(' ') }.toSet()
        val ports = HashMap<String, Int>()
        for (m in methods) {
            val p = start(ctx, m)
            if (p > 0) ports[m] = p
        }
        // Nicht mehr gebrauchte Brücken beenden
        (started - methods).toList().forEach { stop(it) }
        if (ports.isEmpty()) return ""
        val sb = StringBuilder("UseBridges 1\n")
        ports.forEach { (m, p) -> sb.append("ClientTransportPlugin ").append(m).append(" socks5 127.0.0.1:").append(p).append('\n') }
        lines.filter { it.substringBefore(' ') in ports }.forEach { sb.append("Bridge ").append(it).append('\n') }
        return sb.toString()
    }

    private fun controller(ctx: Context): Any? {
        controller?.let { return it }
        return try {
            val cls = Class.forName("IPtProxy.Controller")
            val events = Class.forName("IPtProxy.OnTransportEvents")
            val listener = java.lang.reflect.Proxy.newProxyInstance(events.classLoader, arrayOf(events)) { _, m, _ ->
                when (m.name) {
                    "hashCode" -> 0
                    "equals" -> false
                    "toString" -> "Tresor-Brücken"
                    else -> null
                }
            }
            val dir = File(ctx.noBackupFilesDir, "pt").apply { mkdirs() }
            val types = arrayOf(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, String::class.java, events)
            val c = try {
                cls.getConstructor(*types).newInstance(dir.absolutePath, false, false, "ERROR", listener)
            } catch (e: NoSuchMethodException) {
                Class.forName("IPtProxy.IPtProxy").getMethod("newController", *types).invoke(null, dir.absolutePath, false, false, "ERROR", listener)
            } ?: return null
            // Standardwerte für Snowflake (falls eine Brücken-Zeile sie nicht selbst mitbringt)
            set(c, "setSnowflakeBrokerUrl", BROKER)
            set(c, "setSnowflakeFrontDomains", FRONTS)
            set(c, "setSnowflakeIceServers", ICE)
            set(c, "setSnowflakeAmpCacheUrl", AMP)
            controller = c
            c
        } catch (e: Throwable) {
            null
        }
    }

    private fun set(c: Any, name: String, v: String) {
        try { c.javaClass.getMethod(name, String::class.java).invoke(c, v) } catch (_: Throwable) {}
    }

    private fun start(ctx: Context, method: String): Int {
        val c = controller(ctx) ?: return 0
        return try {
            if (method !in started) {
                c.javaClass.getMethod("start", String::class.java, String::class.java).invoke(c, method, "")
                started.add(method)
            }
            (c.javaClass.getMethod("port", String::class.java).invoke(c, method) as Number).toInt()
        } catch (e: Throwable) {
            started.remove(method)
            0
        }
    }

    private fun stop(method: String) {
        val c = controller ?: return
        try { c.javaClass.getMethod("stop", String::class.java).invoke(c, method) } catch (_: Throwable) {}
        started.remove(method)
    }

    @Synchronized
    fun stopAll() {
        started.toList().forEach { stop(it) }
    }
}
