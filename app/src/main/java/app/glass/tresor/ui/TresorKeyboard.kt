package app.glass.tresor.ui

import androidx.activity.compose.BackHandler
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
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

    /** Tastenbelegung (Sprache), z. B. "de", "en", "tr" – siehe [KbLayouts]. */
    var layout by mutableStateOf("de")

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
// Maße wie die iPhone-Tastatur: Tasten 45 dp hoch, 5 dp Abstand, 11 dp zwischen den Reihen,
// abgerundete Glastasten mit weichem Schatten nach unten (kein Strich), Buchstaben-Blase beim Drücken.

private val KEY_H = 45.dp
private val KEY_GAP = 5.dp
private val ROW_GAP = 11.dp
private val KEY_RADIUS = 9.dp
private val SEND_BLUE = Color(0xFF0A84FF)
private val BUBBLE = Color(0xFF6E7183)
private val KEY_TEXT = TextStyle(fontSize = 23.sp, color = Color.White)
private val FN_TEXT = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)

private val EMOJIS = listOf(
    "😀", "😂", "😍", "🥰", "😘", "😊", "😉", "😎",
    "🤔", "😅", "😭", "😡", "👍", "👎", "👏", "🙏",
    "❤️", "🔥", "✨", "🎉", "💯", "✅", "❌", "⭐",
    "👋", "💪", "🙌", "🤝", "😴", "🤫", "🔒", "🌙",
)

/** Lange drücken: Sonderzeichen wie am iPhone (mit dem Finger seitlich auswählen). */
private val ALTS: Map<Char, List<String>> = mapOf(
    'a' to listOf("ä", "à", "á", "â", "æ", "ã", "å"),
    'o' to listOf("ö", "ò", "ó", "ô", "õ", "ø", "œ"),
    'u' to listOf("ü", "ù", "ú", "û"),
    'e' to listOf("é", "è", "ê", "ë", "€"),
    's' to listOf("ß", "š", "ś", "ş"),
    'c' to listOf("č", "ć", "ç"),
    'z' to listOf("ž", "ź", "ż"),
    'n' to listOf("ñ", "ń"),
    'i' to listOf("í", "ì", "î", "ï", "ı"),
    'y' to listOf("ý", "ÿ"),
    'd' to listOf("đ"),
    'g' to listOf("ğ"),
    'l' to listOf("ł"),
    'е' to listOf("ё"),
    'ь' to listOf("ъ"),
    '-' to listOf("–", "—", "•"),
    '?' to listOf("¿"),
    '!' to listOf("¡"),
    '"' to listOf("„", "“", "”", "«", "»"),
    '\'' to listOf("‚", "‘", "’"),
    '€' to listOf("$", "£", "¥", "₺", "₽"),
    '.' to listOf("…"),
)

/** Tastenbelegungen (Buchstaben-Reihen) je Sprache. */
object KbLayouts {
    class Layout(val rows: List<String>, val rtl: Boolean = false, val space: String = "Leerzeichen")

    val all: Map<String, Layout> = mapOf(
        "de" to Layout(listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"), space = "Leerzeichen"),
        "en" to Layout(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), space = "space"),
        "tr" to Layout(listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"), space = "boşluk"),
        "bs" to Layout(listOf("qwertzuiopšđ", "asdfghjklčćž", "yxcvbnm"), space = "razmak"),
        "sr" to Layout(listOf("љњертзуиопш", "асдфгхјклчћ", "ѕџцвбнмђж"), space = "размак"),
        "ru" to Layout(listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"), space = "пробел"),
        "ce" to Layout(listOf("йцукенгшщзх", "фывапролджэ", "ӏячсмитьбю"), space = "пробел"),
        "ar" to Layout(listOf("ضصثقفغعهخحج", "شسيبلاتنمكة", "ءظطذدزروى"), rtl = true, space = "مسافة"),
        "zh" to Layout(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), space = "空格"),
        "ja" to Layout(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), space = "空白"),
    )

    fun get(lang: String): Layout = all[lang] ?: all.getValue("de")
}

/** Gemeinsamer Glas-Look für alle Tasten: weicher Schatten unten, leichter Verlauf, hauchfeiner Rand. */
private fun Modifier.keyFace(kind: Int, pressed: Boolean): Modifier = drawBehind {
    val r = CornerRadius(KEY_RADIUS.toPx())
    if (kind != 3) drawRoundRect(Color.Black.copy(alpha = 0.40f), topLeft = Offset(0f, 1.2.dp.toPx()), size = size, cornerRadius = r)
    val brush = when {
        kind == 2 -> Brush.verticalGradient(listOf(SEND_BLUE.copy(alpha = if (pressed) 0.75f else 1f), SEND_BLUE.copy(alpha = if (pressed) 0.65f else 0.92f)))
        kind == 4 -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.90f)))
        kind == 1 && !pressed -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.11f)))
        kind == 3 && !pressed -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        pressed -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.46f), Color.White.copy(alpha = 0.40f)))
        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.31f), Color.White.copy(alpha = 0.25f)))
    }
    drawRoundRect(brush, size = size, cornerRadius = r)
    if (kind != 3) drawRoundRect(Color.White.copy(alpha = 0.06f), size = size, cornerRadius = r, style = Stroke(0.6.dp.toPx()))
}

/** Tipp-Rückmeldung wie am iPhone: kurzes Klicken (Vibration) und – wenn im System an – Tastenton. */
private fun keyFeedback(view: View, sound: Int = AudioManager.FX_KEYPRESS_STANDARD) {
    try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS) } catch (_: Exception) {}
    try { (view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(sound) } catch (_: Exception) {}
}

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
        enter = expandVertically(spring(dampingRatio = 1f, stiffness = 700f), expandFrom = Alignment.Top, clip = false) + fadeIn(tween(160)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 900f), shrinkTowards = Alignment.Top, clip = false) + fadeOut(tween(120)),
    ) {
        val t = shownTarget ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        // Kein clip: die Buchstaben-Blasen der obersten Reihe dürfen über die Tastatur hinausragen
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2C3C), Color(0xFF1A1B26))), shape)
                .drawBehind {
                    // weicher Glanz oben links statt einer Linie
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width * 0.2f, 0f), radius = size.width * 0.7f
                        ),
                        size = size.copy(height = size.height * 0.5f)
                    )
                }
                .navigationBarsPadding()
                .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP)
        ) {
            // Kopfzeile: dezent
            Box(Modifier.fillMaxWidth().height(22.dp)) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.lock, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Tresor-Tastatur · lernt nichts, sendet nichts", style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f)), maxLines = 1)
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(28.dp)
                        .pressable(CircleShape, scaleTo = 0.88f, haptic = Haptic.TICK) { close() },
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.chevronDown, "Tastatur schließen", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
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
    val layout = KbLayouts.get(TresorKb.layout)
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
    val maxCols = layout.rows.maxOf { it.length }

    when (mode) {
        0 -> {
            // Reihe 1 und 2: gleich breite Tasten, kürzere Reihen mittig (wie am iPhone)
            for (r in 0..1) {
                val row = layout.rows[r]
                KeyRow {
                    val pad = (maxCols - row.length) / 2f
                    if (pad > 0f) Spacer(Modifier.weight(pad))
                    row.forEach { c -> CharKey(c, shift > 0, Modifier.weight(1f), ::type) }
                    if (pad > 0f) Spacer(Modifier.weight(pad))
                }
            }
            KeyRow {
                val row = layout.rows[2]
                val side = ((maxCols - row.length) / 2f).coerceAtLeast(1.2f)
                PressKey(
                    Modifier.weight(side), kind = if (shift > 0) 4 else 1,
                    sound = AudioManager.FX_KEYPRESS_STANDARD,
                    onTap = {
                        val now = System.currentTimeMillis()
                        shift = when {
                            shift == 2 -> 0
                            shift == 1 && now - lastShiftTap < 350 -> 2
                            shift == 1 -> 0
                            else -> 1
                        }
                        lastShiftTap = now
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (shift > 0) Ic.shiftOn else Ic.shift, "Großbuchstaben", tint = if (shift > 0) Color(0xFF1C1C24) else Color.White, modifier = Modifier.size(21.dp))
                        if (shift == 2) Box(Modifier.padding(top = 1.dp).size(width = 12.dp, height = 2.dp).background(Color(0xFF1C1C24)))
                    }
                }
                Spacer(Modifier.width(4.dp))
                row.forEach { c -> CharKey(c, shift > 0, Modifier.weight(1f), ::type) }
                Spacer(Modifier.width(4.dp))
                DeleteKey(Modifier.weight(side)) { t.backspace() }
            }
        }
        1, 2 -> {
            val r1 = if (mode == 1) "1234567890" else "[]{}#%^*+="
            val r2 = if (mode == 1) "-/:;()€&@\"" else "_\\|~<>$£¥•"
            KeyRow { r1.forEach { c -> CharKey(c, false, Modifier.weight(1f), ::type) } }
            KeyRow { r2.forEach { c -> CharKey(c, false, Modifier.weight(1f), ::type) } }
            KeyRow {
                PressKey(Modifier.weight(1.45f), kind = 1, onTap = { mode = if (mode == 1) 2 else 1 }) {
                    Text(if (mode == 1) "#+=" else "123", style = FN_TEXT, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                ".,?!'".forEach { c -> CharKey(c, false, Modifier.weight(1.4f), ::type) }
                Spacer(Modifier.width(6.dp))
                DeleteKey(Modifier.weight(1.45f)) { t.backspace() }
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
        PressKey(Modifier.weight(1.5f), kind = 1, onTap = { mode = if (mode == 0) 1 else 0 }) {
            Text(if (mode == 0) "123" else if (layout.rtl) "أبج" else "ABC", style = FN_TEXT, maxLines = 1)
        }
        val extras = when (t.kind) {
            TresorKb.Kind.URI -> listOf(".", "/")
            TresorKb.Kind.EMAIL -> listOf("@", ".")
            TresorKb.Kind.PASSWORD -> listOf(".")
            else -> emptyList()
        }
        if (extras.isEmpty()) {
            PressKey(Modifier.weight(1.25f), kind = if (mode == 3) 4 else 1, onTap = { mode = if (mode == 3) 0 else 3 }) {
                Icon(Ic.emoji, "Emoji", tint = if (mode == 3) Color(0xFF1C1C24) else Color.White, modifier = Modifier.size(22.dp))
            }
        } else extras.forEach { e -> CharKey(e[0], false, Modifier.weight(1f), ::type) }
        PressKey(
            Modifier.weight(if (extras.isEmpty()) 5.0f else 6.25f - extras.size), kind = 0,
            sound = AudioManager.FX_KEYPRESS_SPACEBAR,
            onTap = { t.space(); if (shift == 1 && !t.wantsCaps()) shift = 0 }
        ) {
            Text(layout.space, style = FN_TEXT.copy(fontWeight = FontWeight.Normal), maxLines = 1)
        }
        PressKey(Modifier.weight(2.3f), kind = if (actionBlue) 2 else 1, sound = AudioManager.FX_KEYPRESS_RETURN, onTap = action) {
            Text(actionText, style = FN_TEXT.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

@Composable
private fun NumberPad(t: TresorKb.Target, close: () -> Unit) {
    val letters = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    for (r in 0 until 3) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            for (c in 1..3) {
                val d = r * 3 + c
                NumKey(d.toString(), letters[d - 1], Modifier.weight(1f)) { t.insert(d.toString()) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PressKey(Modifier.weight(1f), height = 52.dp, kind = 2, sound = AudioManager.FX_KEYPRESS_RETURN, onTap = { t.onAction?.invoke() ?: close() }) {
            Text(t.actionLabel ?: "OK", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
        }
        NumKey("0", "", Modifier.weight(1f)) { t.insert("0") }
        DeleteKey(Modifier.weight(1f), height = 52.dp) { t.backspace() }
    }
}

// ───────────────────────── Tasten ─────────────────────────

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP), content = content)
}

/**
 * Funktionstaste (Umschalten, 123, Emoji, Leerzeichen, Eingabe, Ziffernblock):
 * Rückmeldung schon beim Aufsetzen des Fingers, ausgelöst beim Loslassen, beim Drücken heller.
 * kind: 0 Buchstabentaste-Look, 1 Funktionstaste, 2 blau, 4 weiß (aktiv)
 */
@Composable
private fun PressKey(
    modifier: Modifier,
    height: Dp = KEY_H,
    kind: Int,
    sound: Int = AudioManager.FX_KEYPRESS_STANDARD,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val tap by rememberUpdatedState(onTap)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(height)
            .keyFace(kind, pressed)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    keyFeedback(view, sound)
                    var inside = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null) { inside = false; break }
                        val p = ch.position
                        inside = p.x >= -20f && p.y >= -40f && p.x <= size.width + 20f && p.y <= size.height + 40f
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    pressed = false
                    if (inside) tap()
                }
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Geometrie der Blase über einer Taste (alles in Pixeln, relativ zur linken oberen Ecke der Taste). */
private class BubbleGeo(val left: Float, val width: Float, val topH: Float, val neck: Float, val kw: Float, val kh: Float) {
    val height get() = topH + neck + kh
}

private fun bubbleGeo(kw: Float, kh: Float, wantW: Float, keyX: Float, screenW: Float, margin: Float): BubbleGeo {
    val w = maxOf(wantW, kw)
    val left = ((kw - w) / 2f).coerceIn(-keyX + margin, (screenW - keyX - w - margin).coerceAtLeast(-keyX + margin))
    return BubbleGeo(left, w, kh * 1.12f, kh * 0.32f, kw, kh)
}

/** Umriss der Blase: oben breit und rund, mit weichem Hals hinunter zur Taste – wie am iPhone. */
private fun bubblePath(g: BubbleGeo, r: Float, rb: Float): Path {
    val l = -g.left            // Taste beginnt hier innerhalb der Blase
    val rr = l + g.kw
    val h = g.height
    val t = g.topH
    val n = g.neck
    return Path().apply {
        moveTo(r, 0f)
        lineTo(g.width - r, 0f)
        quadraticBezierTo(g.width, 0f, g.width, r)
        lineTo(g.width, t)
        cubicTo(g.width, t + n * 0.55f, rr, t + n * 0.45f, rr, t + n)
        lineTo(rr, h - rb)
        quadraticBezierTo(rr, h, rr - rb, h)
        lineTo(l + rb, h)
        quadraticBezierTo(l, h, l, h - rb)
        lineTo(l, t + n)
        cubicTo(l, t + n * 0.45f, 0f, t + n * 0.55f, 0f, t)
        lineTo(0f, r)
        quadraticBezierTo(0f, 0f, r, 0f)
        close()
    }
}

/** Zeichnet die Blase (mit weichem Schatten) und legt den Inhalt in ihren oberen Teil. */
@Composable
private fun Bubble(g: BubbleGeo, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .layout { m, c ->
                val p = m.measure(Constraints.fixed(g.width.toInt(), g.height.toInt()))
                layout(c.maxWidth, c.maxHeight) { p.place(g.left.toInt(), (g.kh - g.height).toInt()) }
            }
            .drawBehind {
                val path = bubblePath(g, 11.dp.toPx(), KEY_RADIUS.toPx())
                drawIntoCanvas { cv ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = BUBBLE.toArgb()
                        setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(110, 0, 0, 0))
                    }
                    cv.nativeCanvas.drawPath(path.asAndroidPath(), paint)
                }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(with(density) { g.topH.toDp() }), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Zeichentaste: Beim Aufsetzen erscheint die Blase mit dem Buchstaben groß darüber (wie am iPhone),
 * geschrieben wird beim Loslassen. Lange drücken = Sonderzeichen; mit dem Finger seitlich auswählen.
 */
@Composable
private fun CharKey(c: Char, upper: Boolean, modifier: Modifier, onType: (String) -> Unit) {
    val label = if (upper) c.uppercase() else c.toString()
    val alts = remember(c, upper) { ALTS[c.lowercaseChar()]?.map { if (upper && it != "ß") it.uppercase() else it } }
    val type by rememberUpdatedState(onType)
    val curLabel by rememberUpdatedState(label)
    val curAlts by rememberUpdatedState(alts)
    var pressed by remember { mutableStateOf(false) }
    var altOpen by remember { mutableStateOf(false) }
    var altSel by remember { mutableIntStateOf(0) }
    var keyX by remember { mutableFloatStateOf(0f) }
    var keyW by remember { mutableFloatStateOf(0f) }
    var keyH by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val margin = with(density) { 3.dp.toPx() }
    val cell = with(density) { 36.dp.toPx() }

    fun altGeo(n: Int) = bubbleGeo(keyW, keyH, maxOf(n * maxOf(cell, keyW) + cell * 0.3f, keyW), keyX, screenW, margin)

    Box(
        modifier
            .zIndex(if (pressed) 2f else 0f)
            .height(KEY_H)
            .onGloballyPositioned { keyX = it.positionInRoot().x; keyW = it.size.width.toFloat(); keyH = it.size.height.toFloat() }
            .keyFace(0, false)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    altOpen = false
                    altSel = 0
                    keyFeedback(view)
                    val long = scope.launch {
                        delay(420)
                        if (curAlts != null) {
                            altOpen = true
                            try { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
                        }
                    }
                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null) { cancelled = true; break }
                        val a = curAlts
                        if (altOpen && a != null) {
                            val g = altGeo(a.size)
                            val cw = (g.width - cell * 0.3f) / a.size
                            val sel = ((ch.position.x - g.left - cell * 0.15f) / cw).toInt().coerceIn(0, a.size - 1)
                            if (sel != altSel) {
                                altSel = sel
                                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                            }
                        }
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    long.cancel()
                    val a = curAlts
                    if (!cancelled) type(if (altOpen && a != null) a[altSel] else curLabel)
                    pressed = false
                    altOpen = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = KEY_TEXT, maxLines = 1)
        if (pressed && keyW > 0f) {
            val a = alts
            if (altOpen && a != null) {
                val g = altGeo(a.size)
                Bubble(g) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = with(density) { (cell * 0.15f).toDp() }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        a.forEachIndexed { i, s ->
                            Box(
                                Modifier.weight(1f).height(with(density) { (g.topH * 0.78f).toDp() })
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (i == altSel) SEND_BLUE else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) { Text(s, style = TextStyle(fontSize = 24.sp, color = Color.White), maxLines = 1) }
                        }
                    }
                }
            } else {
                val g = bubbleGeo(keyW, keyH, keyW * 1.5f, keyX, screenW, margin)
                Bubble(g) { Text(label, style = TextStyle(fontSize = 36.sp, color = Color.White), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun EmojiKey(e: String, modifier: Modifier, onClick: () -> Unit) {
    PressKey(modifier, kind = 3, onTap = onClick) {
        Text(e, style = TextStyle(fontSize = 26.sp), maxLines = 1)
    }
}

/** Löschtaste: gedrückt halten löscht immer weiter (mit Klick bei jedem Zeichen). */
@Composable
private fun DeleteKey(modifier: Modifier, height: Dp = KEY_H, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val del by rememberUpdatedState(onDelete)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(height)
            .keyFace(1, pressed)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    del()
                    keyFeedback(view, AudioManager.FX_KEYPRESS_DELETE)
                    val job = scope.launch {
                        delay(420)
                        while (true) {
                            del()
                            keyFeedback(view, AudioManager.FX_KEYPRESS_DELETE)
                            delay(70)
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
    PressKey(modifier, height = 52.dp, kind = 0, onTap = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = TextStyle(fontSize = 25.sp, color = Color.White), maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, style = TextStyle(fontSize = 9.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.6f)), maxLines = 1)
        }
    }
}
