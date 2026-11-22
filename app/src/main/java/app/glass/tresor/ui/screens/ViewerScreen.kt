package app.glass.tresor.ui.screens

import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import app.glass.tresor.App
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.EmptyState
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.dateFmt
import app.glass.tresor.ui.formatSize
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.openWith
import app.glass.tresor.ui.rememberExport
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.EncryptedDataSource
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Media
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(fid: String, iid: String, filter: ItemType?) {
    val vault = App.vault ?: return
    val scope = rememberCoroutineScope()
    val list = folderItems(vault, fid, filter).filter { it.type == ItemType.IMAGE || it.type == ItemType.VIDEO }
    if (list.isEmpty() || fid !in vault.unlockedFolders) {
        LaunchedEffect(Unit) { App.nav.pop() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val start = list.indexOfFirst { it.id == iid }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val export = rememberExport(vault)
    val current = list.getOrNull(pager.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { list[it].id }
        ) { page ->
            val item = list[page]
            if (item.type == ItemType.VIDEO) {
                VideoPage(vault, fid, item, active = pager.currentPage == page, onChrome = { chrome = it })
            } else {
                ImagePage(vault, fid, item, onTap = { chrome = !chrome }, onZoom = { if (pager.currentPage == page) zoomed = it })
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize()) {
                TopBar(onBack = { App.nav.pop() }) {
                    if (current != null) {
                        Text(
                            "${pager.currentPage + 1} / ${list.size}",
                            style = G.callout.copy(color = G.dim),
                            modifier = Modifier.glass(app.glass.tresor.ui.Pill).padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                if (current != null) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (current.type == ItemType.VIDEO) 110.dp else 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            current.name, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                        Text(
                            "${dateFmt.format(Date(current.created))} · ${formatSize(current.size)}",
                            style = G.footnote.copy(color = G.faint)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.glass(app.glass.tresor.ui.Pill, G.fillStrong).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GlassIconButton(Ic.export, "Exportieren", fill = Color.Transparent) { export(listOf(fid to current)) }
                            GlassIconButton(Ic.trash, "Löschen", tint = G.red, fill = Color.Transparent) { confirmDelete = true }
                        }
                    }
                }
            }
        }

        ConfirmSheet(
            visible = confirmDelete,
            title = "Löschen?",
            text = "Wird endgültig aus dem Tresor entfernt.",
            confirm = "Löschen",
            onDismiss = { confirmDelete = false }
        ) {
            val target = current ?: return@ConfirmSheet
            confirmDelete = false
            scope.launch {
                withContext(Dispatchers.IO) { vault.deleteItems(fid, setOf(target.id)) }
                if (list.size <= 1) App.nav.pop()
            }
        }
    }
}

/** Bild in voller Auflösung (verkleinert auf Bildschirmgröße), Zoom mit zwei Fingern, Doppeltipp. */
@Composable
private fun ImagePage(vault: Vault, fid: String, item: Item, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    var bmp by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        val b = withContext(Dispatchers.IO) {
            try {
                // Vorschau zuerst, damit sofort etwas da ist
                vault.readThumb(fid, item.id)?.let { t -> BitmapFactory.decodeByteArray(t, 0, t.size)?.asImageBitmap() }
                    ?.let { withContext(Dispatchers.Main) { if (bmp == null) bmp = it } }
                vault.reader(fid, item.id).use { r ->
                    val bytes = r.readAll()
                    try {
                        Media.decodeSampled({ bytes.inputStream() }, 2560)?.asImageBitmap()
                    } finally {
                        bytes.fill(0)
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
        if (b != null) bmp = b else if (bmp == null) failed = true
    }

    val scale = remember(item.id) { Animatable(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                offset = Offset.Zero
                                scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                onZoom(false)
                            } else {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                offset = (c - p) * 1.5f
                                scale.animateTo(2.5f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                onZoom(true)
                            }
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                // Eigene Geste: nur bei 2 Fingern oder schon gezoomt – sonst darf der Pager wischen.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale.value > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val ns = (scale.value * zoom).coerceIn(1f, 6f)
                            scope.launch { scale.snapTo(ns) }
                            val maxX = size.width * (ns - 1) / 2f
                            val maxY = size.height * (ns - 1) / 2f
                            offset = if (ns <= 1.01f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                            onZoom(ns > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(
                b, item.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    translationX = offset.x; translationY = offset.y
                }
            )
            failed -> Text("Bild konnte nicht geöffnet werden", style = G.callout.copy(color = G.dim))
            else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

/** Video direkt aus der verschlüsselten Datei – keine entschlüsselte Kopie auf dem Speicher. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(vault: Vault, fid: String, item: Item, active: Boolean, onChrome: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(ctx).build().apply {
            val factory = EncryptedDataSource.Factory { vault.reader(fid, item.id) }
            val src = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri("tresor://${item.id}"))
            setMediaSource(src)
            prepare()
        }
    }
    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }
    DisposableEffect(item.id) {
        onDispose { player.release() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> onChrome(v == android.view.View.VISIBLE) })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Andere Dateien: Text direkt anzeigen, sonst Infos + "Öffnen mit". */
@Composable
fun TextViewScreen(fid: String, iid: String) {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = vault.item(fid, iid)
    var text by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val export = rememberExport(vault)
    val isText = item != null && (item.mime.startsWith("text/") || item.mime == "application/json" ||
        item.name.substringAfterLast('.').lowercase() in setOf("txt", "md", "csv", "log", "json", "xml"))

    LaunchedEffect(iid) {
        if (item != null && isText && item.size in 0L..2_000_000L) {
            text = withContext(Dispatchers.IO) {
                try {
                    vault.reader(fid, iid).use { r ->
                        val b = r.readAll()
                        String(b).also { b.fill(0) }
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    GlassBackground {
        if (item == null) {
            TopBar(onBack = { App.nav.pop() })
            EmptyState(Ic.file, "Nicht gefunden", "Die Datei existiert nicht mehr.", Modifier.align(Alignment.Center))
            return@GlassBackground
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { App.nav.pop() }) {
                GlassIconButton(Ic.export, "Exportieren") { export(listOf(fid to item)) }
                GlassIconButton(Ic.trash, "Löschen", tint = G.red) { confirmDelete = true }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 40.dp)
            ) {
                Text(item.name, style = G.title3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${formatSize(item.size)} · ${dateFmt.format(Date(item.created))}", style = G.footnote)
                Spacer(Modifier.height(18.dp))
                val t = text
                if (t != null) {
                    Text(
                        t, style = G.callout.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                        modifier = Modifier.fillMaxWidth().glass(sq(20)).padding(16.dp)
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().glass(sq(24)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Ic.file, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(item.mime, style = G.footnote)
                        Spacer(Modifier.height(16.dp))
                        PillButton("Öffnen mit …", primary = true, modifier = Modifier.fillMaxWidth()) {
                            scope.launch { openWith(ctx, vault, fid, item) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Zum Öffnen wird kurz eine entschlüsselte Kopie an die andere App übergeben und danach gelöscht.",
                            style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        ConfirmSheet(
            visible = confirmDelete, title = "Datei löschen?", text = "Wird endgültig aus dem Tresor entfernt.",
            confirm = "Löschen", onDismiss = { confirmDelete = false }
        ) {
            confirmDelete = false
            scope.launch {
                withContext(Dispatchers.IO) { vault.deleteItems(fid, setOf(iid)) }
                App.nav.pop()
            }
        }
    }
}

