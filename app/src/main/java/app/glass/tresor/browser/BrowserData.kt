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

    private fun read(v: Vault): JSONObject = try {
        v.readBlob("browser")?.let { JSONObject(String(it)) } ?: JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    fun bookmarks(v: Vault): List<Bookmark> = try {
        val arr = read(v).optJSONArray("bm") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Bookmark(o.getString("t"), o.getString("u")) } }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveBookmarks(v: Vault, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        v.writeBlob("browser", read(v).put("bm", arr).toString().toByteArray())
    }

    /** Seiten, auf denen der Werbeblocker aus ist (verschlüsselt im Tresor, verrät sonst den Verlauf). */
    fun adOffSites(v: Vault): Set<String> = try {
        val arr = read(v).optJSONArray("noab") ?: JSONArray()
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    fun saveAdOffSites(v: Vault, sites: Set<String>) {
        val arr = JSONArray()
        sites.forEach { arr.put(it) }
        v.writeBlob("browser", read(v).put("noab", arr).toString().toByteArray())
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
