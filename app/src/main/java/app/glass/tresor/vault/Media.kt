package app.glass.tresor.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Vorschaubilder nur im Arbeitsspeicher – beim Sperren sofort weg. */
object ThumbCache {
    private val cache = object : LruCache<String, ImageBitmap>(400) {}

    private fun key(slot: String, fid: String, iid: String) = "$slot/$fid/$iid"
    fun get(slot: String, fid: String, iid: String): ImageBitmap? = cache.get(key(slot, fid, iid))
    fun put(slot: String, fid: String, iid: String, b: ImageBitmap) {
        cache.put(key(slot, fid, iid), b)
    }

    fun evict(slot: String, fid: String, iid: String) {
        cache.remove(key(slot, fid, iid))
    }

    fun evictFolder(slot: String, fid: String) {
        cache.snapshot().keys.filter { it.startsWith("$slot/$fid/") }.forEach { cache.remove(it) }
    }

    fun clearSlot(slot: String) {
        cache.snapshot().keys.filter { it.startsWith("$slot/") }.forEach { cache.remove(it) }
    }

    fun clearAll() = cache.evictAll()
}

object Media {
    data class Meta(val name: String, val mime: String, val size: Long)

    fun meta(ctx: Context, uri: Uri): Meta {
        var name = "Datei"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) c.getString(ni)?.let { name = it }
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
        } catch (_: Exception) {
        }
        var mime = ctx.contentResolver.getType(uri)
        if (mime == null || mime == "application/octet-stream") {
            val ext = name.substringAfterLast('.', "").lowercase()
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: mime ?: "application/octet-stream"
        }
        return Meta(name, mime, size)
    }

    fun typeOf(mime: String): ItemType = when {
        mime.startsWith("image/") -> ItemType.IMAGE
        mime.startsWith("video/") -> ItemType.VIDEO
        mime.startsWith("audio/") || mime == "application/ogg" -> ItemType.AUDIO
        else -> ItemType.FILE
    }

    private fun exifRotation(open: () -> InputStream): Int = try {
        open().use { s ->
            when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
        }
    } catch (e: Exception) {
        0
    }

    /** Dekodiert verkleinert und richtig gedreht. */
    fun decodeSampled(open: () -> InputStream, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rot = exifRotation(open)
            var out = bmp
            val longest = maxOf(bmp.width, bmp.height)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                out = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
                if (out !== bmp) bmp.recycle()
            }
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                val r = Bitmap.createBitmap(out, 0, 0, out.width, out.height, m, true)
                if (r !== out) out.recycle()
                out = r
            }
            out
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun imageSize(open: () -> InputStream): Pair<Int, Int> = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, b) }
        val rot = exifRotation(open)
        if (rot == 90 || rot == 270) b.outHeight to b.outWidth else b.outWidth to b.outHeight
    } catch (e: Exception) {
        0 to 0
    }

    fun jpeg(b: Bitmap, q: Int = 82): ByteArray {
        val bos = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q, bos)
        return bos.toByteArray()
    }

    data class VideoInfo(val thumb: Bitmap?, val durationMs: Long, val w: Int, val h: Int)

    fun videoInfo(ctx: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val frame = try {
                r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
            } catch (e: Exception) {
                null
            }
            VideoInfo(frame, dur, w, h)
        } catch (e: Exception) {
            VideoInfo(null, 0, 0, 0)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Liest Datei vom System, verschlüsselt sie direkt in den Ordner und erzeugt die Vorschau. */
    class AudioInfo(val durationMs: Long, val title: String?, val artist: String, val cover: Bitmap?)

    fun audioInfo(ctx: Context, uri: Uri): AudioInfo {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            val artist = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim() ?: ""
            val cover = r.embeddedPicture?.let { pic ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pic, 0, pic.size, o)
                var sample = 1
                while (maxOf(o.outWidth, o.outHeight) / sample > 960) sample *= 2
                BitmapFactory.decodeByteArray(pic, 0, pic.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            AudioInfo(dur, title, artist, cover)
        } catch (e: Exception) {
            AudioInfo(0, null, "", null)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    fun importUri(ctx: Context, vault: Vault, fid: String, uri: Uri, forcedName: String? = null): Item? {
        val m = meta(ctx, uri)
        val type = typeOf(m.mime)
        val name = forcedName ?: m.name
        var w = 0
        var h = 0
        var dur = 0L
        var thumb: ByteArray? = null
        var title: String? = null
        var artist = ""
        val open = { ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("nicht lesbar") }
        when (type) {
            ItemType.IMAGE -> {
                val sz = imageSize(open); w = sz.first; h = sz.second
                decodeSampled(open, 480)?.let { thumb = jpeg(it); it.recycle() }
            }
            ItemType.VIDEO -> {
                val vi = videoInfo(ctx, uri)
                w = vi.w; h = vi.h; dur = vi.durationMs
                vi.thumb?.let { b ->
                    val longest = maxOf(b.width, b.height)
                    val s = if (longest > 480) Bitmap.createScaledBitmap(b, b.width * 480 / longest, b.height * 480 / longest, true) else b
                    thumb = jpeg(s)
                    if (s !== b) s.recycle()
                    b.recycle()
                }
            }
            ItemType.AUDIO -> {
                // Musik: Länge, Titel, Interpret und Cover aus der Datei lesen
                val ai = audioInfo(ctx, uri)
                dur = ai.durationMs; title = ai.title; artist = ai.artist
                ai.cover?.let { b ->
                    val longest = maxOf(b.width, b.height)
                    val s = if (longest > 480) Bitmap.createScaledBitmap(b, b.width * 480 / longest, b.height * 480 / longest, true) else b
                    thumb = jpeg(s)
                    if (s !== b) s.recycle()
                    b.recycle()
                }
            }
            else -> {}
        }
        val now = System.currentTimeMillis()
        val meta = Item(
            id = vault.newItemId(), name = if (forcedName == null && !title.isNullOrBlank()) title!! else name,
            mime = m.mime, type = type,
            size = m.size, created = now, modified = now, width = w, height = h,
            durationMs = dur, artist = artist
        )
        return try {
            open().use { input -> vault.importStream(fid, input, meta, thumb) }
        } catch (e: Exception) {
            vault.dataFile(fid, meta.id).delete()
            null
        }
    }
}
