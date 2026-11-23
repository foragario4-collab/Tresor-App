package app.glass.tresor.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Media
import app.glass.tresor.vault.Vault
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Bereitet Fotos und Videos für den Chat vor.
 * Fotos werden neu gespeichert (max. 1600 px, JPEG) – dabei fallen Ort, Kamera-Daten usw. weg.
 * Videos gehen unverändert raus (höchstens 20 MB), mit kleinem Vorschaubild.
 */
object MediaPrep {
    class Prepared(val kind: String, val data: ByteArray, val w: Int, val h: Int, val dur: Long, val thumb: ByteArray?)

    /** Ergebnis oder Fehlermeldung zum Anzeigen. */
    class Result(val ok: Prepared?, val error: String?)

    private const val MAX_SIDE = 1600
    private const val THUMB_SIDE = 360

    private fun tooBig(size: Long) = "Video ist zu groß (${size / (1024 * 1024)} MB). Im Chat gehen Videos bis ${ChatEngine.MAX_MEDIA_BYTES / (1024 * 1024)} MB."

    private fun image(open: () -> InputStream): Result {
        val bmp = Media.decodeSampled(open, MAX_SIDE) ?: return Result(null, "Foto konnte nicht gelesen werden")
        return try {
            val data = Media.jpeg(bmp, 82)
            val thumb = scaled(bmp, THUMB_SIDE)?.let { t -> Media.jpeg(t, 70).also { if (t !== bmp) t.recycle() } }
            Result(Prepared(Msg.KIND_IMAGE, data, bmp.width, bmp.height, 0, thumb), null)
        } finally {
            bmp.recycle()
        }
    }

    private fun scaled(b: Bitmap, side: Int): Bitmap? = try {
        val longest = maxOf(b.width, b.height)
        if (longest <= side) b else Bitmap.createScaledBitmap(b, (b.width * side / longest).coerceAtLeast(1), (b.height * side / longest).coerceAtLeast(1), true)
    } catch (e: Throwable) {
        null
    }

    /** Aus dem Tresor (Datei bleibt dabei nur im Arbeitsspeicher entschlüsselt). */
    fun fromVault(v: Vault, fid: String, item: Item): Result = try {
        when (item.type) {
            ItemType.IMAGE -> {
                val bytes = ByteArrayOutputStream().also { v.exportTo(fid, item.id, it) }.toByteArray()
                image { ByteArrayInputStream(bytes) }
            }
            ItemType.VIDEO -> {
                if (item.size > ChatEngine.MAX_MEDIA_BYTES) Result(null, tooBig(item.size))
                else {
                    val bytes = ByteArrayOutputStream().also { v.exportTo(fid, item.id, it) }.toByteArray()
                    Result(Prepared(Msg.KIND_VIDEO, bytes, item.width, item.height, item.durationMs, v.readThumb(fid, item.id)), null)
                }
            }
            else -> Result(null, "Nur Fotos und Videos")
        }
    } catch (e: OutOfMemoryError) {
        Result(null, "Zu wenig Speicher für diese Datei")
    } catch (e: Exception) {
        Result(null, "Datei konnte nicht gelesen werden")
    }

    /** Aus der Galerie des Handys (Auswahl über den System-Fotoauswähler, ohne Berechtigung). */
    fun fromUri(ctx: Context, uri: Uri): Result = try {
        val meta = Media.meta(ctx, uri)
        val open = { ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("nicht lesbar") }
        when {
            meta.mime.startsWith("image/") -> image(open)
            meta.mime.startsWith("video/") -> {
                if (meta.size > ChatEngine.MAX_MEDIA_BYTES) Result(null, tooBig(meta.size))
                else {
                    val bytes = open().use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > ChatEngine.MAX_MEDIA_BYTES) throw IllegalArgumentException("zu groß")
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    }
                    val vi = Media.videoInfo(ctx, uri)
                    val thumb = vi.thumb?.let { b -> scaled(b, THUMB_SIDE)?.let { t -> Media.jpeg(t, 70).also { if (t !== b) t.recycle() } }.also { b.recycle() } }
                    Result(Prepared(Msg.KIND_VIDEO, bytes, vi.w, vi.h, vi.durationMs, thumb), null)
                }
            }
            else -> Result(null, "Nur Fotos und Videos")
        }
    } catch (e: IllegalArgumentException) {
        Result(null, tooBig(ChatEngine.MAX_MEDIA_BYTES + 1L))
    } catch (e: OutOfMemoryError) {
        Result(null, "Zu wenig Speicher für diese Datei")
    } catch (e: Exception) {
        Result(null, "Datei konnte nicht gelesen werden")
    }
}

