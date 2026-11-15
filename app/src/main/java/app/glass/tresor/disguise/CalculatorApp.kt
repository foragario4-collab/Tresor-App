package app.glass.tresor.disguise

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val calcBlobs = listOf(
    Blob(Color(0xFF2F6BF6), 0.0f, 0.3f, 1.0f, 0.32f),
    Blob(Color(0xFF0EA5C6), 1.1f, 0.85f, 0.9f, 0.28f),
)

/** Voll funktionsfähiger Rechner. Geheim: Code eintippen und „=“ drücken. */
@Composable
fun CalculatorApp() {
    val scope = rememberCoroutineScope()
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<BigDecimal?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") } // reine Ziffernfolge seit letztem Löschen/Operator

    fun num(s: String): BigDecimal = try {
        BigDecimal(s.replace(".", "").replace(',', '.'))
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    fun fmt(v: BigDecimal?): String {
        if (v == null) return "Fehler"
        val r = v.round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (r.abs() >= BigDecimal("1e12") || (r.signum() != 0 && r.abs() < BigDecimal("1e-8"))) {
            return "%.4e".format(r.toDouble()).replace('.', ',')
        }
        val plain = r.toPlainString()
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(".").reversed()
        return (if (neg) "-" else "") + grouped + if (frac.isNotEmpty()) ",$frac" else ""
    }

    fun calc(a: BigDecimal, b: BigDecimal, o: String): BigDecimal? = when (o) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        "÷" -> if (b.signum() == 0) null else a.divide(b, MathContext(16, RoundingMode.HALF_UP))
        else -> b
    }

    fun press(k: String) {
        when {
            k.length == 1 && k[0].isDigit() -> {
                // Eingetippte Ziffern separat merken – so zählen auch führende Nullen (z. B. 0071)
                if (fresh) typed = k else if (typed.length < 12) typed += k
                if (fresh || display == "0") {
                    display = k; fresh = false
                } else {
                    val digits = display.count { it.isDigit() }
                    if (digits >= 12) return
                    val raw = display.replace(".", "") + k
                    display = if (raw.contains(',')) raw.substringBefore(',').let { fmt(BigDecimal(it.ifEmpty { "0" })) } + "," + raw.substringAfter(',')
                    else fmt(BigDecimal(raw))
                }
            }
            k == "," -> {
                typed = ""
                if (fresh) { display = "0,"; fresh = false } else if (!display.contains(',')) display += ","
            }
            k == "AC" -> { display = "0"; acc = null; op = null; fresh = true; typed = "" }
            k == "±" -> { display = fmt(num(display).negate()); typed = "" }
            k == "%" -> { display = fmt(num(display).divide(BigDecimal(100))); fresh = true; typed = "" }
            k == "=" -> {
                val code = typed
                if (op == null && code.length in 4..12 && !display.contains(',')) {
                    // Geheimer Zugang – sieht von außen aus wie ein normales "="
                    scope.launch { App.tryUnlock(code, silent = true) }
                    fresh = true
                    typed = ""
                    return
                }
                val o = op ?: return
                val a = acc ?: return
                display = fmt(calc(a, num(display), o))
                acc = null; op = null; fresh = true; typed = ""
            }
            else -> {
                val cur = num(display)
                val a = acc
                val o = op
                val result = if (o != null && a != null && !fresh) calc(a, cur, o) else (if (o != null && a != null) a else cur)
                if (result == null) {
                    display = "Fehler"; acc = null; op = null
                } else {
                    acc = result; display = fmt(result); op = k
                }
                fresh = true; typed = ""
            }
        }
    }

    GlassBackground(calcBlobs) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            val size = when {
                display.length > 11 -> 46.sp
                display.length > 8 -> 60.sp
                else -> 80.sp
            }
            Text(
                display,
                style = androidx.compose.ui.text.TextStyle(fontSize = size, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-1).sp),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )
            Spacer(Modifier.height(18.dp))
            val rows = listOf(
                listOf("AC", "±", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ",", "=")
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Exakte Spaltenbreite, damit die breite 0-Taste genau mit den Spalten darüber abschließt
            val unit = (maxWidth - 14.dp * 3) / 4
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (r in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (k in r) {
                            val isOp = k in listOf("÷", "×", "−", "+", "=")
                            val isFn = k in listOf("AC", "±", "%")
                            val active = isOp && k != "=" && op == k && fresh
                            val bg by animateColorAsState(
                                when {
                                    active -> Color.White
                                    isOp -> Color(0xFF2F7CF6)
                                    isFn -> Color.White.copy(alpha = 0.26f)
                                    else -> Color.White.copy(alpha = 0.10f)
                                }, tween(200), label = "key"
                            )
                            val fg by animateColorAsState(if (active) Color(0xFF2F7CF6) else Color.White, tween(200), label = "keyfg")
                            val wide = k == "0"
                            Box(
                                Modifier
                                    .width(if (wide) unit * 2 + 14.dp else unit)
                                    .height(minOf(78.dp, unit))
                                    .pressable(Pill, scaleTo = 0.93f, highlight = 0.25f, haptic = Haptic.KEY) { press(k) }
                                    .glass(Pill, bg),
                                contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center
                            ) {
                                Text(
                                    k,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = if (isFn) 28.sp else 34.sp,
                                        fontWeight = if (isFn) FontWeight.Medium else FontWeight.Normal,
                                        color = fg
                                    ),
                                    modifier = if (wide) Modifier.padding(start = 30.dp) else Modifier
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

