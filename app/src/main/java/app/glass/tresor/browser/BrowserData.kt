package app.glass.tresor.browser

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.glass.tresor.App
import app.glass.tresor.vault.Vault
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val title: String, val url: String)

/**
 * Daten des privaten Browsers.
 * Lesezeichen liegen verschlüsselt im jeweiligen Tresor. Cookies, Cache und Website-Daten
 * werden beim Sperren komplett gelöscht – es bleibt kein Verlauf zurück.
 */
object BrowserData {
    @Volatile
    var used = false

    // Lesezeichen & Ausnahmen liegen verschlüsselt: Standard-Bereich im Tresor, geheime Bereiche im eigenen Ordner.
    // Ohne Tresor (Tarnung „Browser“) liegen sie in den Tarn-Einstellungen.
    private fun read(v: Vault?, area: String?): JSONObject = try {
        val raw = when {
            v == null -> App.dprefs.getString("browser", null)?.toByteArray()
            area == null -> v.readBlob("browser")
            else -> v.readFolderBlob(area, "browser")
        }
        raw?.let { JSONObject(String(it)) } ?: JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun write(v: Vault?, area: String?, o: JSONObject) {
        val b = o.toString().toByteArray()
        when {
            v == null -> App.dprefs.edit().putString("browser", String(b)).apply()
            area == null -> v.writeBlob("browser", b)
            else -> v.writeFolderBlob(area, "browser", b)
        }
    }

    fun bookmarks(v: Vault?, area: String? = null): List<Bookmark> = try {
        val arr = read(v, area).optJSONArray("bm") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Bookmark(o.getString("t"), o.getString("u")) } }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveBookmarks(v: Vault?, list: List<Bookmark>, area: String? = null) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        write(v, area, read(v, area).put("bm", arr))
    }

    /** Seiten, auf denen der Werbeblocker aus ist (verschlüsselt im Tresor, verrät sonst den Verlauf). */
    fun adOffSites(v: Vault?, area: String? = null): Set<String> = try {
        val arr = read(v, area).optJSONArray("noab") ?: JSONArray()
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    fun saveAdOffSites(v: Vault?, sites: Set<String>, area: String? = null) {
        val arr = JSONArray()
        sites.forEach { arr.put(it) }
        write(v, area, read(v, area).put("noab", arr))
    }

    /** Alles löschen, was der Browser hinterlassen hat. */
    fun clearAll() {
        if (!used) return
        used = false
        val run = Runnable {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {
            }
            try {
                File(App.ctx.cacheDir, "WebView").deleteRecursively()
                File(App.ctx.cacheDir, "org.chromium.android_webview").deleteRecursively()
            } catch (_: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else Handler(Looper.getMainLooper()).post(run)
    }
}
