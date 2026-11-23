package app.glass.tresor.ui.screens

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import app.glass.tresor.App
import app.glass.tresor.chat.Calls
import app.glass.tresor.chat.ChatEngine
import app.glass.tresor.chat.Msg
import app.glass.tresor.ui.G
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.Thumb
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Verkleinerte Chat-Bilder im Arbeitsspeicher (wird beim Sperren geleert). */
object ChatMediaCache {
    private val cache = object : LruCache<String, ImageBitmap>(40) {}
    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, b: ImageBitmap) { cache.put(id, b) }
    fun clear() = cache.evictAll()
}

private fun decode(bytes: ByteArray, maxSide: Int): ImageBitmap? = try {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
    var s = 1
    while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
} catch (e: Throwable) {
    null
}

/** Foto oder Video im Verlauf: Vorschau im richtigen Seitenverhältnis, Uhrzeit und Haken darüber. */
@Composable
fun MediaBubble(m: Msg, engine: ChatEngine, onOpen: () -> Unit, onLong: () -> Unit) {
    val bmp by produceState(ChatMediaCache.get(m.id), m.id) {
        if (value == null) value = withContext(Dispatchers.IO) {
            val bytes = if (m.kind == Msg.KIND_IMAGE) engine.mediaData(m) else engine.mediaThumb(m)
            bytes?.let { decode(it, 720) }?.also { ChatMediaCache.put(m.id, it) }
        }
    }
    val ratio = if (m.w > 0 && m.h > 0) (m.w.toFloat() / m.h).coerceIn(0.62f, 1.8f) else 1f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.width(240.dp).aspectRatio(ratio)
                .pressable(sq(20), scaleTo = 0.97f, highlight = 0.08f, haptic = Haptic.NONE, onLongClick = onLong) { onOpen() }
                .clip(sq(20))
                .background(if (m.mine) G.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f))
        ) {
            bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Icon(if (m.kind == Msg.KIND_IMAGE) Ic.photo else Ic.video, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center).size(30.dp))
            if (m.kind == Msg.KIND_VIDEO) {
                Box(
                    Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.play, "Video abspielen", tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
            // Untere Leiste: Länge (Video), Uhrzeit, Zustell-Haken
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (m.kind == Msg.KIND_VIDEO && m.dur > 0) Text(Calls.duration(m.dur), style = G.footnote.copy(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                Text(timeOf(m.ts), style = G.footnote.copy(fontSize = 11.sp, color = Color.White))
                if (m.mine) {
                    Spacer(Modifier.width(4.dp))
                    Icon(if (m.state == 0) Ic.clock else Ic.check, if (m.state == 0) "wartet" else "zugestellt", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** Hinweis, solange ein Foto/Video noch in Teilen ankommt. */
@Composable
fun IncomingMediaPill(engine: ChatEngine, contactId: String) {
    val list by produceState(engine.incoming(contactId), engine, contactId) {
        while (true) { value = engine.incoming(contactId); delay(1000) }
    }
    if (list.isEmpty()) return
    val (kind, p) = list.first()
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.glass(Pill).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(progress = { p }, color = Color.White, strokeWidth = 2.dp, trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                (if (kind == Msg.KIND_IMAGE) "Foto" else "Video") + " kommt an … ${(p * 100).toInt()} %" + if (list.size > 1) " (+${list.size - 1})" else "",
                style = G.footnote.copy(color = Color.White)
            )
        }
    }
}

/** Auswahl aus dem Tresor: die neuesten Fotos und Videos aus offenen Foto-Ordnern. */
@Composable
fun VaultMediaGrid(vault: Vault, onPick: (String, Item) -> Unit) {
    val recent = remember(vault) { vault.recentMedia(120).filter { it.second.type == ItemType.IMAGE || it.second.type == ItemType.VIDEO } }
    if (recent.isEmpty()) {
        Text("Im Tresor sind (in offenen Ordnern) noch keine Fotos oder Videos.", style = G.footnote, modifier = Modifier.padding(vertical = 10.dp))
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(4), Modifier.fillMaxWidth().height(250.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(recent, key = { it.first + "/" + it.second.id }) { (fid, item) ->
            Thumb(
                vault, fid, item,
                Modifier.aspectRatio(1f).clip(sq(10)).pressable(sq(10), scaleTo = 0.93f) { onPick(fid, item) }
            )
        }
    }
}

/** Foto groß (mit Zoom) oder Video abspielen. „Im Tresor sichern“ legt eine Kopie in einen Foto-Ordner. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewer(m: Msg, engine: ChatEngine, onClose: () -> Unit) {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    BackHandler { onClose() }
    val data by produceState<ByteArray?>(null, m.id) { value = withContext(Dispatchers.IO) { engine.mediaData(m) } }
    var saving by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        val bytes = data
        when {
            bytes == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(28.dp))
            m.kind == Msg.KIND_IMAGE -> ZoomImage(m.id, bytes)
            else -> {
                val player = remember(m.id) {
                    ExoPlayer.Builder(ctx).build().apply {
                        val src = ProgressiveMediaSource.Factory { ByteArrayDataSource(bytes) }.createMediaSource(MediaItem.fromUri("tresor://chat/${m.id}"))
                        setMediaSource(src)
                        prepare()
                        play()
                    }
                }
                DisposableEffect(m.id) { onDispose { player.release() } }
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            this.player = player
                            useController = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Obere Leiste
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.9f) { onClose() }.glass(CircleShape, Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.close, "Schließen", tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.pressable(Pill, enabled = bytes != null && !saving) {
                    val b = bytes ?: return@pressable
                    saving = true
                    scope.launch {
                        val msg = withContext(Dispatchers.IO) { saveToVault(vault, m, b, engine.mediaThumb(m)) }
                        App.toast(msg)
                        saving = false
                    }
                }.glass(Pill, Color.Black.copy(alpha = 0.35f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.lock, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Im Tresor sichern", style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** Kopie in einen offenen Foto-Ordner legen. Gibt den Hinweis-Text zurück. */
private fun saveToVault(vault: Vault, m: Msg, bytes: ByteArray, thumb: ByteArray?): String {
    val folder = vault.folders.firstOrNull { it.kind == FolderKind.MEDIA && !it.hidden && it.id in vault.unlockedFolders && !it.isProtected }
        ?: vault.folders.firstOrNull { it.kind == FolderKind.MEDIA && it.id in vault.unlockedFolders }
        ?: return "Kein offener Foto-Ordner"
    val now = System.currentTimeMillis()
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.GERMANY).format(java.util.Date(m.ts))
    val isImg = m.kind == Msg.KIND_IMAGE
    val meta = Item(
        id = vault.newItemId(), name = "Chat_$stamp" + if (isImg) ".jpg" else ".mp4",
        mime = if (isImg) "image/jpeg" else "video/mp4", type = if (isImg) ItemType.IMAGE else ItemType.VIDEO,
        size = bytes.size.toLong(), created = now, modified = now, width = m.w, height = m.h, durationMs = m.dur
    )
    val th = thumb ?: if (isImg) try {
        app.glass.tresor.vault.Media.decodeSampled({ ByteArrayInputStream(bytes) }, 480)?.let { b -> app.glass.tresor.vault.Media.jpeg(b).also { b.recycle() } }
    } catch (e: Throwable) { null } else null
    return try {
        vault.importStream(folder.id, ByteArrayInputStream(bytes), meta, th)
        "In „${folder.name}“ gesichert"
    } catch (e: Exception) {
        "Sichern fehlgeschlagen"
    }
}

@Composable
private fun ZoomImage(id: String, bytes: ByteArray) {
    val bmp by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.Default) { decode(bytes, 2400) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                it, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
        } ?: CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}
