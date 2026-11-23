package app.glass.tresor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.browser.BrowserTor
import app.glass.tresor.chat.Bridges
import app.glass.tresor.chat.ChatService
import app.glass.tresor.music.MusicPlayer
import app.glass.tresor.ui.G
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.TresorKb
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq

private val GLASS = Color.White.copy(alpha = 0.13f)
private val ON_BG = Color.White.copy(alpha = 0.88f)
private val ON_FG = Color(0xFF141838)

/**
 * Kontrollzentrum wie am iPhone: oben auf dem Startbildschirm nach unten wischen (oder Regler-Symbol).
 * Tor, Snowflake, Chat, Kamera · Musik · Schutz-Schalter · Bewegungs-Empfindlichkeit · Jetzt sperren.
 */
@Composable
fun ControlCenter(visible: Boolean, onClose: () -> Unit, onCamera: () -> Unit, onMusic: () -> Unit) {
    BackHandler(enabled = visible) { onClose() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.82f, stiffness = 520f), initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0f)),
        exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0f)),
    ) {
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize()
                .background(Color(0xFF070A20).copy(alpha = 0.86f))
                // leere Stelle antippen oder nach oben wischen = schließen
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -with(density) { 70.dp.toPx() }) onClose() },
                    ) { change, dy -> total += dy; change.consume() }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3B4CFF).copy(alpha = 0.55f), Color.Transparent), center = Offset(w * 0.05f, size.height * 0.12f), radius = w * 0.75f), radius = w * 0.75f, center = Offset(w * 0.05f, size.height * 0.12f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB04CFF).copy(alpha = 0.5f), Color.Transparent), center = Offset(w * 1.0f, size.height * 0.6f), radius = w * 0.7f), radius = w * 0.7f, center = Offset(w * 1.0f, size.height * 0.6f))
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NetTile(Modifier.weight(1f).aspectRatio(1f), onCamera)
                    MusicTile(Modifier.weight(1f).aspectRatio(1f), onMusic)
                }
                Guards(onClose)
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SensSlider(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .pressable(sq(26), scaleTo = 0.95f, haptic = Haptic.CONFIRM) { App.lock() }
                            .glass(sq(26), Color(0xFFFF3B30).copy(alpha = 0.35f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Ic.lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Text("Jetzt sperren", style = G.headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 64.dp, height = 24.dp).pressable(sq(12), haptic = Haptic.NONE) { onClose() },
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.5f))) }
            }
        }
    }
}

@Composable
private fun NetTile(modifier: Modifier, onCamera: () -> Unit) {
    val ctx = LocalContext.current
    var bridges by remember { mutableStateOf(Bridges.mode(ctx)) }
    var chatOn by remember { mutableStateOf(App.prefs.getBoolean("chatBackground", true)) }
    Column(modifier.glass(sq(30), GLASS).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NetButton(Ic.tor, "Browser über Tor", BrowserTor.enabled, Color(0xFF7A4CFF), Modifier.weight(1f)) {
                if (!BrowserTor.supported) { App.toast("Tor im Browser wird auf diesem Handy nicht unterstützt"); return@NetButton }
                BrowserTor.turn(!BrowserTor.enabled)
                App.toast(if (BrowserTor.enabled) "Browser über Tor: an" else "Browser über Tor: aus")
            }
            NetButton(Ic.flake, "Snowflake", bridges != Bridges.OFF, Color(0xFF2F7CF6), Modifier.weight(1f)) {
                if (!Bridges.available) { App.toast("Brücken sind in dieser Version nicht verfügbar"); return@NetButton }
                val m = if (bridges == Bridges.OFF) Bridges.SNOWFLAKE else Bridges.OFF
                Bridges.setMode(ctx, m)
                bridges = m
                if (ChatService.running) ChatService.reload(ctx)
                App.toast(if (m == Bridges.OFF) "Snowflake aus – Tor verbindet neu" else "Snowflake an – Tor verbindet neu (1–2 Minuten)")
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NetButton(Ic.chat, "Chat im Hintergrund", chatOn, Color(0xFF00B37A), Modifier.weight(1f)) {
                chatOn = !chatOn
                App.prefs.edit().putBoolean("chatBackground", chatOn).apply()
                if (chatOn) ChatService.start(ctx) else ChatService.stop(ctx)
                App.toast(if (chatOn) "Chat im Hintergrund erreichbar" else "Chat nur bei offener App erreichbar")
            }
            NetButton(Ic.camera, "Kamera", false, Color(0xFFFF9500), Modifier.weight(1f)) { onCamera() }
        }
    }
}

@Composable
private fun NetButton(icon: ImageVector, label: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f)
                .pressable(CircleShape, scaleTo = 0.9f, highlight = 0.18f, haptic = Haptic.TICK, onClick = onClick)
                .clip(CircleShape)
                .background(if (on) color else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun MusicTile(modifier: Modifier, onMusic: () -> Unit) {
    val cur = MusicPlayer.current
    val title = cur?.second?.name?.substringBeforeLast('.') ?: "Keine Musik"
    Column(
        modifier.pressable(sq(30), scaleTo = 0.97f) { onMusic() }.glass(sq(30), GLASS).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(sq(10)).background(Brush.linearGradient(listOf(Color(0xFFFF6A3D), Color(0xFFD6247A)))),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.music, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = G.headline.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (cur != null) "Musik" else "Tippen zum Öffnen", style = G.footnote.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)), maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().alpha(if (cur != null) 1f else 0.4f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaKey(Ic.prev, "Zurück", 22, cur != null) { MusicPlayer.previous() }
            MediaKey(if (MusicPlayer.playing) Ic.pause else Ic.play, if (MusicPlayer.playing) "Pause" else "Abspielen", 28, cur != null) { MusicPlayer.toggle() }
            MediaKey(Ic.next, "Weiter", 22, cur != null) { MusicPlayer.next() }
        }
    }
}

@Composable
private fun MediaKey(icon: ImageVector, label: String, size: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressable(CircleShape, enabled = enabled, scaleTo = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size.dp)) }
}

@Composable
private fun Guards(onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GuardTile(Ic.motion, "Bewegung", App.motionLock, Modifier.weight(1f)) {
                App.setMotion(!App.motionLock)
            }
            GuardTile(Ic.flip, "Umdrehen", App.flipLock, Modifier.weight(1f)) {
                App.changeFlipLock(!App.flipLock)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GuardTile(Ic.keyboard, "Eigene Tastatur", TresorKb.enabled, Modifier.weight(1f)) {
                TresorKb.turn(!TresorKb.enabled)
            }
            // Verstecken braucht den eigenen Telefoncode → dafür in die Einstellungen
            GuardTile(Ic.eyeOff, "App versteckt", App.appHidden, Modifier.weight(1f)) {
                onClose()
                App.nav.push(Screen.Settings)
                App.toast("„App verstecken“ findest du hier unter Tarnung")
            }
        }
    }
}

@Composable
private fun GuardTile(icon: ImageVector, name: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (on) ON_FG else Color.White
    Row(
        modifier
            .pressable(sq(22), scaleTo = 0.95f, haptic = Haptic.TICK, onClick = onClick)
            .then(if (on) Modifier.clip(sq(22)).background(ON_BG) else Modifier.glass(sq(22), GLASS))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = G.headline.copy(fontSize = 14.sp, color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (on) "An" else "Aus", style = G.footnote.copy(fontSize = 12.sp, color = fg.copy(alpha = 0.8f)), maxLines = 1)
        }
    }
}

/** Großer Regler: nach oben/unten ziehen = Bewegungs-Empfindlichkeit. */
@Composable
private fun SensSlider(modifier: Modifier) {
    val fill = 0.08f + App.motionSens * 0.92f
    val dark = fill > 0.36f
    val fg = if (dark) Color(0xFF1A1F4A) else Color.White
    Box(
        modifier
            .glass(sq(26), GLASS)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { App.setMotionSens(App.motionSens, persist = true) },
                    onDragCancel = { App.setMotionSens(App.motionSens, persist = true) },
                ) { change, dy ->
                    change.consume()
                    App.setMotionSens(App.motionSens - dy / (size.height * 0.92f), persist = false)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val f = 1f - p.y / size.height
                    App.setMotionSens(((f - 0.08f) / 0.92f).coerceIn(0f, 1f), persist = true)
                }
            }
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fill).background(Color.White.copy(alpha = 0.85f)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 12.dp, bottom = 14.dp)) {
            Icon(Ic.motion, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    App.motionSens < 0.25f -> "Nur starkes Rucken"
                    App.motionSens < 0.55f -> "Mittel"
                    App.motionSens < 0.8f -> "Empfindlich"
                    else -> "Sehr empfindlich"
                },
                style = G.footnote.copy(fontSize = 11.sp, color = fg.copy(alpha = 0.8f)), maxLines = 1
            )
            Text("Bewegungs-Empfindlichkeit", style = G.footnote.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg), maxLines = 2)
        }
        if (!App.motionLock) Text(
            "Bewegung aus", style = G.footnote.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp)
        )
    }
}
