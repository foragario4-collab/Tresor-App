package app.glass.tresor.disguise

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Kleiner Bild-Lader für die Tarn-Apps (nur Arbeitsspeicher, keine Dateien). */
object NetImages {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    fun load(url: String, maxSide: Int = 900): Bitmap? {
        cache.get(url)?.let { return it }
        if (!url.startsWith("https://")) return null
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) News")
        try {
            val bytes = c.inputStream.use { inp ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 8 * 1024 * 1024) return null
                }
                out.toByteArray()
            }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) return null
            var sample = 1
            while (o.outWidth / (sample * 2) >= maxSide || o.outHeight / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            cache.put(url, bmp)
            return bmp
        } finally {
            c.disconnect()
        }
    }
}

@Composable
fun NetImage(url: String, modifier: Modifier = Modifier, placeholder: Color = Color.White.copy(alpha = 0.06f)) {
    var bmp by remember(url) { mutableStateOf(NetImages.cached(url)) }
    LaunchedEffect(url) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) { try { NetImages.load(url) } catch (e: Exception) { null } }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(placeholder))
}
