package app.glass.tresor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.BreakIterator

/**
 * Die Tresor-Tastatur: eine eigene Tastatur für alle Eingabefelder im Tresor.
 * Keine fremde Tastatur-App bekommt etwas mit – sie lernt nichts, speichert nichts, sendet nichts.
 * Die System-Tastatur wird für diese Felder gar nicht erst gestartet.
 */
object TresorKb {
    /** Eigene Tastatur an (Einstellung „Eigene Tastatur“, Standard: an). */
    var enabled by mutableStateOf(true)
        private set

    /** Das Feld, in das gerade getippt wird – null = Tastatur zu. */
    var active by mutableStateOf<Target?>(null)
        internal set

    fun init() {
        enabled = App.prefs.getBoolean("secureKb", true)
    }

    fun turn(on: Boolean) {
        enabled = on
        App.prefs.edit().putBoolean("secureKb", on).apply()
        if (!on) active = null
    }

    fun close() {
        active = null
    }

    enum class Kind { TEXT, NUMBER, URI, EMAIL, PASSWORD }

    /** Ein Eingabefeld, verbunden mit der Tastatur. */
    class Target {
        var tfv by mutableStateOf(TextFieldValue(""))
        var onChange: (String) -> Unit = {}
        var kind = Kind.TEXT
        var singleLine = true
        var actionLabel: String? = null
        var onAction: (() -> Unit)? = null

        private fun set(nv: TextFieldValue) {
            val changed = nv.text != tfv.text
            tfv = nv
            if (changed) onChange(nv.text)
        }

        fun insert(s: String) {
            val str = if (singleLine) s.replace("\n", "") else s
            if (str.isEmpty()) return
            val v = tfv
            val a = minOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
            val b = maxOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
            set(TextFieldValue(v.text.replaceRange(a, b, str), TextRange(a + str.length)))
        }

        /** Zwei Leerzeichen hintereinander = Punkt (wie am iPhone). */
        fun space() {
            val v = tfv
            val a = v.selection.start
            if (kind == Kind.TEXT && v.selection.collapsed && a >= 2 && v.text[a - 1] == ' ' && v.text[a - 2].isLetterOrDigit()) {
                set(TextFieldValue(v.text.replaceRange(a - 1, a, ". "), TextRange(a + 1)))
            } else insert(" ")
        }

        fun backspace() {
            val v = tfv
            val a = minOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
            val b = maxOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
            if (a != b) { set(TextFieldValue(v.text.removeRange(a, b), TextRange(a))); return }
            if (a == 0) return
            // Ganzes Zeichen löschen – auch Emojis, die aus mehreren Teilen bestehen
            val it = BreakIterator.getCharacterInstance()
            it.setText(v.text)
            val p = it.preceding(a).let { x -> if (x == BreakIterator.DONE || x < 0) a - 1 else x }
            set(TextFieldValue(v.text.removeRange(p, a), TextRange(p)))
        }

        /** Am Satzanfang automatisch groß schreiben. */
        fun wantsCaps(): Boolean {
            if (kind != Kind.TEXT) return false
            val before = tfv.text.substring(0, tfv.selection.start.coerceIn(0, tfv.text.length))
            val t = before.trimEnd(' ')
            return t.isEmpty() || t.endsWith('\n') || ((t.endsWith('.') || t.endsWith('!') || t.endsWith('?')) && before.endsWith(' '))
        }
    }
}

/**
 * Eingabefeld für den Tresor. Mit eigener Tastatur: Text, Cursor und Markieren wie gewohnt,
 * aber getippt wird nur über die Tresor-Tastatur. Ohne: normales Feld mit System-Tastatur.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TresorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = G.body,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    /** false = immer System-Tastatur (z. B. in der Tarnung, damit nichts auf den Tresor hinweist). */
    own: Boolean = true,
) {
    val cursor = SolidColor(G.accentText)
    // In der Tarnung immer die normale System-Tastatur – nichts darf auf den Tresor hinweisen
    if (!TresorKb.enabled || !own || App.nav.top == app.glass.tresor.Screen.Disguise) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = if (onAction != null) KeyboardActions(
                onDone = { onAction() }, onGo = { onAction() }, onSend = { onAction() }, onSearch = { onAction() }
            ) else keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            cursorBrush = cursor,
        )
        return
    }

    val t = remember { TresorKb.Target() }
    // Von außen geänderter Text (z. B. nach dem Senden geleert) → übernehmen, Cursor ans Ende
    val shown = if (t.tfv.text == value) t.tfv else TextFieldValue(value, TextRange(value.length))
    SideEffect { if (t.tfv.text != value) t.tfv = shown }
    t.onChange = onValueChange
    t.singleLine = singleLine
    t.actionLabel = actionLabel
    t.onAction = onAction
    t.kind = when (keyboardOptions.keyboardType) {
        KeyboardType.Number, KeyboardType.NumberPassword, KeyboardType.Phone, KeyboardType.Decimal -> TresorKb.Kind.NUMBER
        KeyboardType.Uri -> TresorKb.Kind.URI
        KeyboardType.Email -> TresorKb.Kind.EMAIL
        KeyboardType.Password -> TresorKb.Kind.PASSWORD
        else -> TresorKb.Kind.TEXT
    }
    DisposableEffect(t) { onDispose { if (TresorKb.active === t) TresorKb.active = null } }

    // Die System-Tastatur wird für dieses Feld nie gestartet
    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
        BasicTextField(
            value = shown,
            onValueChange = { nv ->
                val changed = nv.text != t.tfv.text
                t.tfv = nv
                if (changed) onValueChange(nv.text)
            },
            modifier = modifier.onFocusChanged { st ->
                if (st.isFocused) TresorKb.active = t
                else if (TresorKb.active === t) TresorKb.active = null
                onFocusChanged?.invoke(st.isFocused)
            },
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            cursorBrush = cursor,
        )
    }
}

// ───────────────────────── Die Tastatur selbst ─────────────────────────

private val KEY_SHAPE = RoundedCornerShape(12.dp)
private val SEND_BLUE = Color(0xFF3B6CFF)
private val EMOJIS = listOf(
    "😀", "😂", "😍", "🥰", "😘", "😊", "😉", "😎",
    "🤔", "😅", "😭", "😡", "👍", "👎", "👏", "🙏",
    "❤️", "🔥", "✨", "🎉", "💯", "✅", "❌", "⭐",
    "👋", "💪", "🙌", "🤝", "😴", "🤫", "🔒", "🌙",
)
private val UMLAUT = mapOf('a' to "ä", 'o' to "ö", 'u' to "ü", 's' to "ß", 'e' to "€")

/** Unten im Fenster: erscheint, sobald ein Tresor-Feld angetippt wird. Der Inhalt darüber rückt nach oben. */
@Composable
fun TresorKeyboardHost() {
    val focus = LocalFocusManager.current
    val target = TresorKb.active
    var last by remember { mutableStateOf<TresorKb.Target?>(null) }
    SideEffect { if (target != null) last = target }
    val shownTarget = target ?: last
    val close = {
        TresorKb.close()
        try { focus.clearFocus(force = true) } catch (_: Exception) {}
    }
    BackHandler(enabled = target != null) { close() }

    AnimatedVisibility(
        visible = target != null,
        enter = expandVertically(spring(dampingRatio = 1f, stiffness = 700f), expandFrom = Alignment.Top) + fadeIn(tween(160)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 900f), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
    ) {
        val t = shownTarget ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF232A5E), Color(0xFF151A40))))
                .drawBehind {
                    // feine helle Oberkante wie Glas
                    drawLine(Color.White.copy(alpha = 0.30f), Offset(30.dp.toPx(), 0.5f), Offset(size.width - 30.dp.toPx(), 0.5f), 1.dp.toPx())
                }
                .navigationBarsPadding()
                .padding(start = 5.dp, end = 5.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Kopfzeile
            Box(Modifier.fillMaxWidth().height(26.dp)) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tresor-Tastatur · lernt nichts, sendet nichts", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f)), maxLines = 1)
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(26.dp)
                        .pressable(CircleShape, scaleTo = 0.88f, haptic = Haptic.TICK) { close() },
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.chevronDown, "Tastatur schließen", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp)) }
            }
            if (t.kind == TresorKb.Kind.NUMBER) NumberPad(t, close) else LetterPad(t, close)
        }
    }
}

@Composable
private fun LetterPad(t: TresorKb.Target, close: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 Buchstaben, 1 Ziffern, 2 Zeichen, 3 Emoji
    var shift by remember { mutableIntStateOf(0) } // 0 aus, 1 einmal, 2 Feststelltaste
    var lastShiftTap by remember { mutableStateOf(0L) }
    // Neues Feld → wieder mit Buchstaben beginnen
    LaunchedEffect(t) { mode = 0 }
    // Satzanfang → automatisch groß (nicht bei Feststelltaste)
    LaunchedEffect(t, t.tfv.text, t.tfv.selection) {
        if (shift != 2) shift = if (t.wantsCaps()) 1 else 0
    }
    fun type(s: String) {
        t.insert(s)
        if (shift == 1) shift = 0
    }
    val action: () -> Unit = {
        when {
            t.onAction != null -> t.onAction?.invoke()
            !t.singleLine -> t.insert("\n")
            else -> close()
        }
    }
    val actionText = t.actionLabel ?: if (t.singleLine || t.onAction != null) "Fertig" else "Eingabe"
    val actionBlue = t.onAction != null || t.singleLine

    when (mode) {
        0 -> {
            KeyRow { "qwertzuiop".forEach { c -> CharKey(c, shift > 0, Modifier.weight(1f), ::type) } }
            KeyRow { "asdfghjklö".forEach { c -> CharKey(c, shift > 0, Modifier.weight(1f), ::type) } }
            KeyRow {
                FnKey(Modifier.weight(1.4f), icon = if (shift > 0) Ic.shiftOn else Ic.shift, label = "Großbuchstaben", lit = shift == 2) {
                    val now = System.currentTimeMillis()
                    shift = when {
                        shift == 2 -> 0
                        shift == 1 && now - lastShiftTap < 350 -> 2
                        shift == 1 -> 0
                        else -> 1
                    }
                    lastShiftTap = now
                }
                "yxcvbnm".forEach { c -> CharKey(c, shift > 0, Modifier.weight(1f), ::type) }
                DeleteKey(Modifier.weight(1.4f)) { t.backspace() }
            }
        }
        1, 2 -> {
            val r1 = if (mode == 1) "1234567890" else "[]{}#%^*+="
            val r2 = if (mode == 1) "-/:;()€&@\"" else "_\\|~<>$£¥•"
            KeyRow { r1.forEach { c -> CharKey(c, false, Modifier.weight(1f), ::type) } }
            KeyRow { r2.forEach { c -> CharKey(c, false, Modifier.weight(1f), ::type) } }
            KeyRow {
                FnKey(Modifier.weight(1.4f), text = if (mode == 1) "#+=" else "123") { mode = if (mode == 1) 2 else 1 }
                ".,?!'".forEach { c -> CharKey(c, false, Modifier.weight(1.4f), ::type) }
                DeleteKey(Modifier.weight(1.4f)) { t.backspace() }
            }
        }
        else -> {
            EMOJIS.chunked(8).take(3).forEach { row ->
                KeyRow { row.forEach { e -> EmojiKey(e, Modifier.weight(1f)) { t.insert(e) } } }
            }
            KeyRow {
                EMOJIS.drop(24).forEach { e -> EmojiKey(e, Modifier.weight(1f)) { t.insert(e) } }
                DeleteKey(Modifier.weight(1.4f)) { t.backspace() }
            }
        }
    }
    // Unterste Reihe
    KeyRow {
        FnKey(Modifier.weight(1.4f), text = if (mode == 0) "123" else "ABC") { mode = if (mode == 0) 1 else 0 }
        val extras = when (t.kind) {
            TresorKb.Kind.URI -> listOf(".", "/")
            TresorKb.Kind.EMAIL -> listOf("@", ".")
            TresorKb.Kind.PASSWORD -> listOf(".")
            else -> emptyList()
        }
        if (extras.isEmpty()) {
            FnKey(Modifier.weight(1f), icon = Ic.emoji, label = "Emoji", lit = mode == 3) { mode = if (mode == 3) 0 else 3 }
        } else extras.forEach { e -> CharKey(e[0], false, Modifier.weight(1f), ::type) }
        Key(Modifier.weight(if (extras.isEmpty()) 4.6f else 5.6f - extras.size), onClick = { t.space(); if (shift == 1 && !t.wantsCaps()) shift = 0 }) {
            Text("Leerzeichen", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White), maxLines = 1)
        }
        Key(Modifier.weight(2f), bg = if (actionBlue) SEND_BLUE else Color.White.copy(alpha = 0.10f), onClick = action) {
            Text(actionText, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
        }
    }
}

@Composable
private fun NumberPad(t: TresorKb.Target, close: () -> Unit) {
    val letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    for (r in 0 until 3) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in 1..3) {
                val d = r * 3 + c
                NumKey(d.toString(), letters[d - 1], Modifier.weight(1f)) { t.insert(d.toString()) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(Modifier.weight(1f), height = 52, bg = SEND_BLUE, onClick = { t.onAction?.invoke() ?: close() }) {
            Text(t.actionLabel ?: "OK", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
        }
        NumKey("0", "", Modifier.weight(1f)) { t.insert("0") }
        DeleteKey(Modifier.weight(1f), height = 52) { t.backspace() }
    }
}

// ───────────────────────── Tasten ─────────────────────────

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), content = content)
}

/** Glastaste wie im Entwurf: halbtransparent, feiner Rand, helle Oberkante. */
@Composable
private fun Key(
    modifier: Modifier,
    height: Int = 44,
    bg: Color = Color.White.copy(alpha = 0.20f),
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(height.dp)
            .pressable(KEY_SHAPE, scaleTo = 0.94f, highlight = 0.25f, haptic = Haptic.KEY, onLongClick = onLong, onClick = onClick)
            .clip(KEY_SHAPE)
            .background(bg)
            .then(
                if (bg.alpha == 0f) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), KEY_SHAPE).drawBehind {
                    drawLine(Color.White.copy(alpha = 0.40f), Offset(10.dp.toPx(), 1.5f), Offset(size.width - 10.dp.toPx(), 1.5f), 1.dp.toPx())
                }
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun CharKey(c: Char, upper: Boolean, modifier: Modifier, onType: (String) -> Unit) {
    val s = if (upper) c.uppercase() else c.toString()
    val alt = UMLAUT[c.lowercaseChar()]
    Key(
        modifier,
        onLong = if (alt != null) ({ onType(if (upper && alt != "ß" && alt != "€") alt.uppercase() else alt) }) else null,
        onClick = { onType(s) }
    ) {
        Text(s, style = TextStyle(fontSize = 21.sp, color = Color.White), maxLines = 1)
    }
}

@Composable
private fun EmojiKey(e: String, modifier: Modifier, onClick: () -> Unit) {
    Key(modifier, bg = Color.Transparent, onClick = onClick) {
        Text(e, style = TextStyle(fontSize = 22.sp), maxLines = 1)
    }
}

@Composable
private fun FnKey(modifier: Modifier, text: String? = null, icon: ImageVector? = null, label: String? = null, lit: Boolean = false, onClick: () -> Unit) {
    Key(modifier, bg = if (lit) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.10f), onClick = onClick) {
        if (icon != null) Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
        else Text(text ?: "", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
    }
}

/** Löschtaste: gedrückt halten löscht immer weiter. */
@Composable
private fun DeleteKey(modifier: Modifier, height: Int = 44, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(height.dp)
            .clip(KEY_SHAPE)
            .background(Color.White.copy(alpha = if (pressed) 0.40f else 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), KEY_SHAPE)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onDelete()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val job = scope.launch {
                        delay(420)
                        while (true) {
                            onDelete()
                            delay(65)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) { Icon(Ic.backspace, "Löschen", tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun NumKey(label: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Key(modifier, height = 52, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = TextStyle(fontSize = 24.sp, color = Color.White), maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, style = TextStyle(fontSize = 9.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.6f)), maxLines = 1)
        }
    }
}
