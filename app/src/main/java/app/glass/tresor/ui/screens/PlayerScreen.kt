package app.glass.tresor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.music.MusicPlayer
import app.glass.tresor.music.RepeatMode
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassSlider
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.Thumb
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.formatDuration
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import kotlinx.coroutines.delay

/** Großer Player im Apple-Stil: Cover, Titel, Fortschritt, Steuerung. */
@Composable
fun PlayerScreen() {
    val vault = App.vault ?: return
    val cur = MusicPlayer.current
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(cur?.second?.id) {
        while (true) {
            if (!dragging) { pos = MusicPlayer.positionMs(); dur = MusicPlayer.durationMs() }
            delay(250)
        }
    }

    GlassBackground {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
            Spacer(Modifier.height(18.dp))
            if (cur == null) {
                Spacer(Modifier.weight(1f))
                Icon(Ic.music, null, tint = G.dim, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text("Nichts wird abgespielt", style = G.headline)
                Spacer(Modifier.weight(1f))
                return@Column
            }
            val (fid, item) = cur
            // Cover
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .shadow(24.dp, sq(26)).clip(sq(26)).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.hasThumb) Thumb(vault, fid, item, Modifier.fillMaxSize(), showBadge = false)
                else Icon(Ic.music, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(30.dp))
            Text(item.name.ifBlank { "Ohne Titel" }, style = G.title2.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Text(item.artist.ifBlank { "Unbekannter Interpret" }, style = G.callout.copy(color = G.dim, fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(22.dp))

            // Fortschritt
            val frac = if (dragging) dragValue else if (dur > 0) pos.toFloat() / dur else 0f
            GlassSlider(
                frac,
                { dragging = true; dragValue = it },
                onDone = { MusicPlayer.seekTo(dragValue); pos = (dur * dragValue).toLong(); dragging = false }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(if (dragging) (dur * dragValue).toLong() else pos), style = G.footnote, modifier = Modifier.weight(1f))
                Text("-" + formatDuration((dur - if (dragging) (dur * dragValue).toLong() else pos).coerceAtLeast(0)), style = G.footnote)
            }
            Spacer(Modifier.height(18.dp))

            // Steuerung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SmallControl(Ic.shuffle, "Zufällig", active = MusicPlayer.shuffle) { MusicPlayer.toggleShuffle() }
                BigControl(Ic.prev, "Zurück", 34.dp) { MusicPlayer.previous() }
                Box(
                    Modifier.size(78.dp)
                        .pressable(CircleShape, scaleTo = 0.9f, highlight = 0.1f, haptic = Haptic.CONFIRM) { MusicPlayer.toggle() }
                        .clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (MusicPlayer.playing) Ic.pause else Ic.play, if (MusicPlayer.playing) "Pause" else "Abspielen", tint = Color(0xFF0B0D12), modifier = Modifier.size(34.dp))
                }
                BigControl(Ic.next, "Weiter", 34.dp) { MusicPlayer.next() }
                SmallControl(Ic.repeat, when (MusicPlayer.repeat) { RepeatMode.OFF -> "Wiederholen aus"; RepeatMode.ALL -> "Alle wiederholen"; RepeatMode.ONE -> "Lied wiederholen" }, active = MusicPlayer.repeat != RepeatMode.OFF, badge = if (MusicPlayer.repeat == RepeatMode.ONE) "1" else null) {
                    MusicPlayer.cycleRepeat()
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (App.prefs.getBoolean("musicBackground", false)) "Läuft beim Verlassen weiter (Einstellungen)" else "Stoppt beim Verlassen der App · änderbar in den Einstellungen",
                style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BigControl(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).pressable(CircleShape, scaleTo = 0.85f, highlight = 0.08f) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)) }
}

@Composable
private fun SmallControl(icon: ImageVector, label: String, active: Boolean, badge: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.85f, highlight = 0.08f) { onClick() }
            .clip(CircleShape).background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (active) G.accentText else G.dim, modifier = Modifier.size(20.dp))
        if (badge != null) Text(badge, style = G.footnote.copy(fontSize = 9.sp, color = G.accentText), modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp))
    }
}

/** Kleiner Player unten, solange Musik läuft (auf Übersicht, Alben und Ordnern). */
@Composable
fun MiniPlayer(modifier: Modifier) {
    val vault = App.vault
    val cur = MusicPlayer.current
    val top = App.nav.top
    val show = vault != null && cur != null &&
        (top == Screen.Home || top is Screen.Albums || top is Screen.FolderView)
    AnimatedVisibility(
        show, modifier = modifier,
        enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
    ) {
        val c = cur ?: return@AnimatedVisibility
        val v = vault ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .pressable(sq(20), haptic = Haptic.NONE) { App.nav.push(Screen.Player) }
                .glass(sq(20), G.sheet).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(sq(12)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                if (c.second.hasThumb) Thumb(v, c.first, c.second, Modifier.fillMaxSize(), showBadge = false)
                else Icon(Ic.music, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.second.name.ifBlank { "Ohne Titel" }, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.second.artist.ifBlank { "Unbekannter Interpret" }, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.85f) { MusicPlayer.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (MusicPlayer.playing) Ic.pause else Ic.play, "Abspielen/Pause", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.85f) { MusicPlayer.next() }, contentAlignment = Alignment.Center) {
                Icon(Ic.next, "Weiter", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
