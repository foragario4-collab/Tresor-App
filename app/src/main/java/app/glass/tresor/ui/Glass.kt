package app.glass.tresor.ui

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// ───────────────────────── Farben & Schrift ─────────────────────────

object G {
    val bg = Color(0xFF07080C)
    val fill = Color.White.copy(alpha = 0.08f)
    val fillStrong = Color.White.copy(alpha = 0.14f)
    val dim = Color.White.copy(alpha = 0.72f)
    val faint = Color.White.copy(alpha = 0.55f)
    val divider = Color.White.copy(alpha = 0.10f)
    val accent = Color(0xFF2F7CF6)
    val accentText = Color(0xFF8FB4FF)
    val green = Color(0xFF30C25A)
    val mint = Color(0xFF5EE0B5)
    val red = Color(0xFFFF6B70)
    val blue = Color(0xFF2F6BF6)
    val purple = Color(0xFF8B4FF0)
    val amber = Color(0xFFD97A0B)
    val teal = Color(0xFF0F9D8E)
    val sheet = Color(0xFF1C1E26).copy(alpha = 0.92f)

    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = Color.White)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = Color.White)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val body = TextStyle(fontSize = 17.sp, color = Color.White)
    val callout = TextStyle(fontSize = 15.sp, color = Color.White)
    val footnote = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.72f))
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
}

// ───────────────────────── Apple-Ecken (Squircle) ─────────────────────────

/**
 * "Kontinuierliche" Ecken wie bei Apple: die Rundung geht weich in die Gerade über
 * (Superellipse statt Viertelkreis). Bei Pillen/Kreisen wird automatisch rund.
 */
class SquircleShape(private val radius: Dp, private val smoothing: Float = 1.5f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val half = minOf(size.width, size.height) / 2f
        if (r <= 0f) return Outline.Rectangle(size.toRect())
        val rr = r * smoothing
        if (rr >= half) {
            return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(minOf(r, half))))
        }
        val n = 4.0
        val steps = 22
        val w = size.width
        val h = size.height
        val p = Path()
        var first = true
        fun corner(cx: Float, cy: Float, start: Double) {
            for (i in 0..steps) {
                val t = start + (PI / 2) * i / steps
                val c = cos(t)
                val s = sin(t)
                val x = cx + rr * (sign(c) * abs(c).pow(2.0 / n)).toFloat()
                val y = cy + rr * (sign(s) * abs(s).pow(2.0 / n)).toFloat()
                if (first) {
                    p.moveTo(x, y); first = false
                } else p.lineTo(x, y)
            }
        }
        corner(w - rr, rr, -PI / 2)
        corner(w - rr, h - rr, 0.0)
        corner(rr, h - rr, PI / 2)
        corner(rr, rr, PI)
        p.close()
        return Outline.Generic(p)
    }
}

fun sq(r: Int) = SquircleShape(r.dp)
val Pill = RoundedCornerShape(percent = 50)

// ───────────────────────── Glas ─────────────────────────

private val glassEdge = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f))
)

fun Modifier.glass(shape: Shape, fill: Color = G.fill): Modifier =
    this
        .clip(shape)
        .background(fill)
        .border(1.dp, glassEdge, shape)

// ───────────────────────── Drück-Effekt ─────────────────────────

enum class Haptic { NONE, TICK, KEY, CONFIRM }

/**
 * Der Drück-Effekt: das Element wird kleiner und heller – IMMER in seiner eigenen Form.
 * Kein Standard-Ripple, also nie eckige Kanten beim Drücken.
 * Beim Loslassen federt es mit einer Feder-Animation zurück.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    shape: Shape?,
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    highlight: Float = 0.12f,
    haptic: Haptic = Haptic.TICK,
    interaction: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val own = remember { MutableInteractionSource() }
    val src = interaction ?: own
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = 1600f)
        else spring(dampingRatio = 0.48f, stiffness = 420f),
        label = "pressScale"
    )
    val hl by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 70 else 280),
        label = "pressHighlight"
    )
    fun buzz(h: Haptic) {
        val c = when (h) {
            Haptic.NONE -> return
            Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.KEY -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.CONFIRM -> HapticFeedbackConstants.CONFIRM
        }
        view.performHapticFeedback(c)
    }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .drawWithContent {
            drawContent()
            if (hl > 0f && highlight > 0f) drawRect(Color.White.copy(alpha = highlight * hl))
        }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick?.let { lc ->
                {
                    lc()
                }
            },
            onClick = {
                buzz(haptic)
                onClick()
            }
        )
}

// ───────────────────────── Hintergrund ─────────────────────────

data class Blob(val color: Color, val x: Float, val y: Float, val r: Float, val alpha: Float)

val blobsDefault = listOf(
    Blob(Color(0xFF3B5BFD), -0.1f, 0.02f, 0.95f, 0.55f),
    Blob(Color(0xFFA855F7), 1.05f, 0.45f, 0.85f, 0.42f),
    Blob(Color(0xFF14B8A6), 0.2f, 1.02f, 0.85f, 0.38f),
)

@Composable
fun GlassBackground(
    blobs: List<Blob> = blobsDefault,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize().background(G.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            for (b in blobs) {
                val center = Offset(b.x * w, b.y * size.height)
                val radius = b.r * w * 0.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to b.color.copy(alpha = b.alpha),
                        0.45f to b.color.copy(alpha = b.alpha * 0.45f),
                        1f to Color.Transparent,
                        center = center, radius = radius
                    ),
                    radius = radius, center = center
                )
            }
        }
        content()
    }
}

// ───────────────────────── Bausteine ─────────────────────────

@Composable
fun GlassIconButton(
    icon: ImageVector,
    label: String,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Color.White,
    fill: Color = G.fill,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .pressable(CircleShape, scaleTo = 0.92f, onLongClick = onLongClick, onClick = onClick)
            .glass(CircleShape, fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    textColor: Color? = null,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        when {
            !enabled -> Color.White.copy(alpha = 0.12f)
            primary -> Color.White
            else -> G.fill
        }, label = "pillBg"
    )
    val fg = textColor ?: when {
        !enabled -> Color.White.copy(alpha = 0.5f)
        primary -> Color(0xFF0B0D12)
        else -> Color.White
    }
    Row(
        modifier
            .height(height)
            .pressable(Pill, enabled = enabled, highlight = if (primary) 0f else 0.12f, haptic = Haptic.CONFIRM, onClick = onClick)
            .then(if (primary || !enabled) Modifier.clip(Pill).background(bg) else Modifier.glass(Pill))
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = G.headline.copy(color = fg, fontSize = 16.sp))
    }
}

/** iOS-Schalter: Knopf dehnt sich beim Drücken und gleitet mit Feder rüber. */
@Composable
fun GlassSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val track by animateColorAsState(if (checked) G.green else Color.White.copy(alpha = 0.2f), tween(220), label = "track")
    val knobW by animateDpAsState(if (pressed) 33.dp else 27.dp, spring(stiffness = Spring.StiffnessMedium), label = "knobW")
    val knobX by animateDpAsState(
        if (checked) (51.dp - 2.dp - knobW) else 2.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "knobX"
    )
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(Pill)
            .background(track)
            .combinedClickableCompat(src) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onChange(!checked)
            }
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(knobW, 27.dp)
                .clip(Pill)
                .background(Color.White)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(src: MutableInteractionSource, onClick: () -> Unit) =
    this.combinedClickable(interactionSource = src, indication = null, role = Role.Switch, onClick = onClick)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 24,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(modifier.glass(sq(radius)).padding(padding)) { content() }
}

@Composable
fun TopBar(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = horizontalPadding)
            .padding(top = 12.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) GlassIconButton(Ic.back, "Zurück", onClick = onBack)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = G.caption, modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = false,
    trailing: @Composable () -> Unit
) {
    Column {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(G.divider))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = G.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                if (subtitle != null) Text(subtitle, style = G.footnote)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val ring by animateColorAsState(if (selected) G.accent else Color.White.copy(alpha = 0.35f), label = "ring")
    val dot by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 500f), label = "dot"
    )
    Box(
        Modifier.size(24.dp).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = dot; scaleY = dot }
                .clip(CircleShape)
                .background(G.accent)
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = G.headline)
        Spacer(Modifier.height(4.dp))
        Text(text, style = G.footnote, textAlign = TextAlign.Center)
    }
}

/** Farbiges App-Symbol im Apple-Stil. */
@Composable
fun AppTile(icon: ImageVector, color: Color, size: Dp = 40.dp, radius: Int = 12, iconSize: Dp = 22.dp) {
    Box(
        Modifier
            .size(size)
            .clip(sq(radius))
            .background(color)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), sq(radius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0).replace('.', ',')
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0).replace('.', ',')
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}


/** Schieberegler im Apple-Stil (Wert 0..1). */
@Composable
fun GlassSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    var w by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val knob = 28.dp
    val knobPx = with(density) { knob.toPx() }
    val v = value.coerceIn(0f, 1f)
    fun set(x: Float) {
        val span = (w - knobPx).coerceAtLeast(1f)
        change(((x - knobPx / 2) / span).coerceIn(0f, 1f))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { w = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { set(it.x); done() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { done() }) { c, _ -> c.consume(); set(c.position.x) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = knob / 2).height(4.dp).clip(Pill).background(Color.White.copy(alpha = 0.2f)))
        Box(
            Modifier.padding(start = knob / 2)
                .width(with(density) { ((w - knobPx).coerceAtLeast(0f) * v).toDp() })
                .height(4.dp).clip(Pill).background(G.accent)
        )
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(((w - knobPx).coerceAtLeast(0f) * v).toInt(), 0) }
                .size(knob)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
