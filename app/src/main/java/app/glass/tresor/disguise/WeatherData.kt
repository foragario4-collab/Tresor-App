package app.glass.tresor.disguise

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Wetterdaten von Open-Meteo (kostenlos, ohne Konto, ohne Schlüssel).
 * Gespeichert wird nur die gewählte Stadt und die letzte Vorhersage – in den Daten der Tarn-Apps, nie im Tresor.
 */
object WeatherData {
    data class City(val name: String, val region: String, val lat: Double, val lon: Double, val gps: Boolean = false)
    data class Hour(val time: String, val temp: Double, val code: Int, val day: Boolean)
    data class Day(val date: String, val code: Int, val max: Double, val min: Double, val rain: Int)
    data class Now(
        val temp: Double, val feels: Double, val code: Int, val wind: Double, val humidity: Int, val day: Boolean,
        val hours: List<Hour>, val days: List<Day>, val sunrise: String, val sunset: String, val fetched: Long
    )

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("disguise_data", Context.MODE_PRIVATE)

    fun city(ctx: Context): City? = try {
        val o = JSONObject(prefs(ctx).getString("wxCity", null) ?: return null)
        City(o.getString("n"), o.optString("r"), o.getDouble("la"), o.getDouble("lo"), o.optBoolean("g"))
    } catch (e: Exception) {
        null
    }

    fun saveCity(ctx: Context, c: City) {
        val o = JSONObject().put("n", c.name).put("r", c.region).put("la", c.lat).put("lo", c.lon).put("g", c.gps)
        prefs(ctx).edit().putString("wxCity", o.toString()).remove("wxCache").apply()
    }

    fun cached(ctx: Context): Now? = try {
        prefs(ctx).getString("wxCache", null)?.let { parse(JSONObject(it), prefs(ctx).getLong("wxTime", 0)) }
    } catch (e: Exception) {
        null
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Wetter")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Städte suchen (Deutsch). */
    fun search(q: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=de&format=json&name=" + URLEncoder.encode(q.trim(), "UTF-8")
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val region = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            City(o.getString("name"), region, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** Vorhersage laden und speichern. */
    fun fetch(ctx: Context, c: City): Now {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(java.util.Locale.ROOT, c.lat)}&longitude=${"%.3f".format(java.util.Locale.ROOT, c.lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m,is_day" +
            "&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
        val txt = get(url)
        val now = System.currentTimeMillis()
        val r = parse(JSONObject(txt), now)
        prefs(ctx).edit().putString("wxCache", txt).putLong("wxTime", now).apply()
        return r
    }

    fun parse(o: JSONObject, fetched: Long): Now {
        val cur = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val d = o.getJSONObject("daily")
        val curTime = cur.optString("time") // z. B. 2026-11-21T14:15
        val hTimes = h.getJSONArray("time")
        var start = 0
        val curHour = curTime.take(13)
        for (i in 0 until hTimes.length()) if (hTimes.getString(i).take(13) == curHour) { start = i; break }
        val hours = (start until minOf(start + 24, hTimes.length())).map { i ->
            Hour(hTimes.getString(i).substringAfter('T').take(5), num(h.getJSONArray("temperature_2m"), i), h.getJSONArray("weather_code").optInt(i), h.optJSONArray("is_day")?.optInt(i, 1) != 0)
        }
        val dTimes = d.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            Day(dTimes.getString(i), d.getJSONArray("weather_code").optInt(i), num(d.getJSONArray("temperature_2m_max"), i), num(d.getJSONArray("temperature_2m_min"), i),
                d.optJSONArray("precipitation_probability_max")?.optInt(i, 0) ?: 0)
        }
        return Now(
            cur.optDouble("temperature_2m"), cur.optDouble("apparent_temperature"), cur.optInt("weather_code"), cur.optDouble("wind_speed_10m"),
            cur.optInt("relative_humidity_2m"), cur.optInt("is_day", 1) != 0, hours, days,
            d.optJSONArray("sunrise")?.optString(0)?.substringAfter('T') ?: "", d.optJSONArray("sunset")?.optString(0)?.substringAfter('T') ?: "", fetched
        )
    }

    private fun num(a: JSONArray, i: Int): Double = if (a.isNull(i)) 0.0 else a.optDouble(i)

    /** WMO-Wettercode → Beschreibung. */
    fun text(code: Int): String = when (code) {
        0 -> "Klar"
        1 -> "Überwiegend klar"
        2 -> "Teilweise bewölkt"
        3 -> "Bedeckt"
        45, 48 -> "Nebel"
        51, 53, 55 -> "Nieselregen"
        56, 57 -> "Gefrierender Niesel"
        61 -> "Leichter Regen"
        63 -> "Regen"
        65 -> "Starker Regen"
        66, 67 -> "Gefrierender Regen"
        71 -> "Leichter Schneefall"
        73 -> "Schneefall"
        75 -> "Starker Schneefall"
        77 -> "Schneegriesel"
        80, 81 -> "Regenschauer"
        82 -> "Heftige Schauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> "–"
    }

    enum class Kind { CLEAR, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

    fun kind(code: Int): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2 -> Kind.PARTLY
        3 -> Kind.CLOUD
        45, 48 -> Kind.FOG
        in 51..67, 80, 81, 82 -> Kind.RAIN
        in 71..77, 85, 86 -> Kind.SNOW
        in 95..99 -> Kind.STORM
        else -> Kind.CLOUD
    }

    /** Symbol für das Widget (Text). */
    fun symbol(code: Int, day: Boolean = true): String = when (kind(code)) {
        Kind.CLEAR -> if (day) "☀" else "☾"
        Kind.PARTLY -> "⛅"
        Kind.CLOUD -> "☁"
        Kind.FOG -> "≋"
        Kind.RAIN -> "☂"
        Kind.SNOW -> "❄"
        Kind.STORM -> "⚡"
    }

    fun deg(v: Double): String = "${Math.round(v)}°"
}
