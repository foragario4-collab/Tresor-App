package app.glass.tresor.disguise

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.glass.tresor.App
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.formatDuration
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tarnung „Musik“: ein echter Musikplayer für die normale Musik auf dem Handy.
 * Geheimer Zugang: den Code ins Suchfeld tippen und „Suchen“ drücken.
 */
private data class Song(val id: Long, val title: String, val artist: String, val durationMs: Long) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

private val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun MusicApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    val covers = remember { mutableMapOf<Long, ImageBitmap?>() }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        App.suppressLock = false
        allowed = ok
    }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setHandleAudioBecomingNoisy(true)
        }
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(allowed) {
        if (!allowed) return@LaunchedEffect
        songs = withContext(Dispatchers.IO) { loadSongs(ctx) }
        loaded = true
    }

    fun playList(list: List<Song>, start: Int) {
        player.setMediaItems(list.map { MediaItem.fromUri(it.uri) }, start, 0)
        player.prepare()
        player.play()
        current = list.getOrNull(start)
    }
    DisposableEffect(songs) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val u = mediaItem?.localConfiguration?.uri ?: return
                current = songs.firstOrNull { it.uri == u } ?: current
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun search() {
        val q = query.trim()
        // Geheimer Zugang – sieht aus wie eine normale Suche
        if (q.length in 6..12 && q.all { it.isDigit() }) {
            focus.clearFocus()
            scope.launch {
                if (App.tryUnlock(q, silent = true)) { player.pause(); query = "" }
            }
        } else focus.clearFocus()
    }

    val shown = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty() || q.all { it.isDigit() }) songs
        else songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    GlassBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 18.dp, bottom = 12.dp)) {
                    Text("Musik", style = G.largeTitle)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).glass(Pill).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Titel, Interpreten suchen", style = G.body.copy(fontSize = 16.sp, color = G.faint))
                            BasicTextField(
                                value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                                textStyle = G.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(G.accentText),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { search() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            Box(Modifier.size(28.dp).pressable(CircleShape, haptic = Haptic.NONE) { query = "" }, contentAlignment = Alignment.Center) {
                                Icon(Ic.close, "Leeren", tint = G.dim, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (allowed && shown.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row {
                            PillButton("Abspielen", icon = Ic.play, height = 42.dp, modifier = Modifier.weight(1f)) { playList(shown, 0) }
                            Spacer(Modifier.width(10.dp))
                            PillButton("Zufällig", icon = Ic.shuffle, height = 42.dp, modifier = Modifier.weight(1f)) {
                                val l = shown.shuffled(); playList(l, 0)
                            }
                        }
                    }
                }
            }
            if (!allowed) item {
                Column(Modifier.fillMaxWidth().glass(sq(24)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.music, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Deine Musik", style = G.headline)
                    Spacer(Modifier.height(4.dp))
                    Text("Erlaube den Zugriff auf Audiodateien, um deine Musik abzuspielen.", style = G.footnote, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PillButton("Zugriff erlauben", primary = true) { App.suppressLock = true; perm.launch(audioPermission) }
                }
            } else if (loaded && songs.isEmpty()) item {
                Text("Keine Musik auf diesem Gerät gefunden.", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(vertical = 24.dp))
            }
            items(shown, key = { it.id }) { s ->
                val active = current?.id == s.id
                Row(
                    Modifier.fillMaxWidth().pressable(sq(14), haptic = Haptic.NONE) { playList(shown, shown.indexOf(s)) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(s, covers, Modifier.size(48.dp).clip(sq(10)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = G.body.copy(fontSize = 16.sp, color = if (active) G.red else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatDuration(s.durationMs), style = G.footnote.copy(color = G.faint))
                }
            }
        }

        // Mini-Player
        val c = current
        if (c != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()
                    .glass(sq(20), G.sheet).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(c, covers, Modifier.size(44.dp).clip(sq(12)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.artist, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.85f) { if (player.isPlaying) player.pause() else player.play() }, contentAlignment = Alignment.Center) {
                    Icon(if (playing) Ic.pause else Ic.play, "Abspielen/Pause", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.85f) { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }, contentAlignment = Alignment.Center) {
                    Icon(Ic.next, "Weiter", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Cover(s: Song, cache: MutableMap<Long, ImageBitmap?>, modifier: Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(s.id) { mutableStateOf(cache[s.id]) }
    LaunchedEffect(s.id) {
        if (bmp == null && !cache.containsKey(s.id)) {
            val b = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.loadThumbnail(s.uri, Size(160, 160), null).asImageBitmap() } catch (e: Exception) { null }
            }
            cache[s.id] = b
            bmp = b
        }
    }
    Box(modifier.background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Ic.music, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

private fun loadSongs(ctx: android.content.Context): List<Song> {
    val out = mutableListOf<Song>()
    val proj = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
    )
    try {
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val artist = c.getString(iA)?.takeIf { it != "<unknown>" } ?: "Unbekannter Interpret"
                out += Song(c.getLong(iId), c.getString(iT) ?: "Ohne Titel", artist, c.getLong(iD))
            }
        }
    } catch (_: Exception) {
    }
    return out
}
