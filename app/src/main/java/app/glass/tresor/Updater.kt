package app.glass.tresor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.glass.tresor.ui.fileUri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-App-Updates über GitHub-Releases deines Repos.
 * GitHub Actions legt bei jedem Upload ein Release "v<Nummer>" mit der APK an.
 * Öffentliches Repo: funktioniert direkt. Privates Repo: in den Einstellungen
 * einen GitHub-Token (nur Lesezugriff) eintragen.
 */
object Updater {
    data class Release(val build: Int, val name: String, val notes: String, val assetUrl: String, val size: Long)

    val currentBuild get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME
    val repo: String get() = BuildConfig.GITHUB_REPO

    fun token(): String = App.prefs.getString("ghToken", "") ?: ""
    fun setToken(t: String) = App.prefs.edit().putString("ghToken", t.trim()).apply()

    private fun open(url: String, auth: Boolean, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Tresor-Updater")
        val t = token()
        if (auth && t.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $t")
        return c
    }

    /** Holt das neueste Release. null = keins gefunden. Wirft bei Netzwerk-/Zugriffsfehlern. */
    fun latest(): Release? {
        require(repo.isNotEmpty()) { "Kein Repo hinterlegt – bitte über GitHub Actions bauen." }
        val c = open("https://api.github.com/repos/$repo/releases/latest", true, "application/vnd.github+json")
        try {
            when (c.responseCode) {
                200 -> {}
                404 -> throw IllegalStateException(
                    if (token().isEmpty()) "Nicht gefunden. Ist das Repo privat? Dann Token eintragen." else "Kein Release gefunden."
                )
                401, 403 -> throw IllegalStateException("Kein Zugriff – Token prüfen.")
                else -> throw IllegalStateException("GitHub antwortet mit Fehler ${c.responseCode}.")
            }
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val build = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(
                        build, o.optString("name"), o.optString("body"),
                        a.getString("url"), a.optLong("size")
                    )
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** Lädt die APK herunter (Fortschritt 0..1). Weiterleitungen werden ohne Token verfolgt. */
    fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "Tresor-${r.build}.apk")
        var url = r.assetUrl
        var auth = true
        repeat(5) {
            val c = open(url, auth, "application/octet-stream")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    url = c.getHeaderField("Location") ?: throw IllegalStateException("Download fehlgeschlagen.")
                    auth = false // Weiterleitung zum Speicher von GitHub: ohne Token
                    return@repeat
                }
                if (code != 200) throw IllegalStateException("Download fehlgeschlagen ($code).")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                c.inputStream.use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
                return out
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Zu viele Weiterleitungen.")
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Einmalig nötig: "Unbekannte Apps installieren" für diese App erlauben. */
    fun openInstallPermission(ctx: Context) {
        App.suppressLock = true
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        )
    }

    fun install(ctx: Context, apk: File) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(fileUri(ctx, apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
