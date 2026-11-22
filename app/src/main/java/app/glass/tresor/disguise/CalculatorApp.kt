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

private val calcBlobs = listOf(
    Blob(Color(0xFF2F6BF6), 0.0f, 0.3f, 1.0f, 0.32f),
    Blob(Color(0xFF0EA5C6), 1.1f, 0.85f, 0.9f, 0.28f),
)

/**
 * Rechner wie beim Samsung: ganzer Rechenweg oben, darunter schon während des Tippens das Ergebnis.
 * Geheim: nur den Code eintippen (ohne Rechenzeichen) und „=“ drücken.
 */
@Composable
fun CalculatorApp() {
    val scope = rememberCoroutineScope()
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) } // nach „=“: angezeigtes Ergebnis
    var typed by remember { mutableStateOf("") } // reine Ziffernfolge (zählt auch führende Nullen, z. B. 0071)
    var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var error by remember { mutableStateOf(false) }

    fun press(k: String) {
        error = false
        if (k == "C") { expr = ""; result = null; typed = ""; return }
        if (k == "=") {
            val code = typed
            if (code.length in 4..12 && expr.all { it.isDigit() }) {
                // Geheimer Zugang – sieht von außen aus wie ein normales „=“
                scope.launch { App.tryUnlock(code, silent = true) }
                expr = ""; typed = ""; result = null
                return
            }
            if (!CalcEngine.hasOperation(expr)) return
            val v = CalcEngine.eval(expr)
            if (v == null) { error = true; return }
            val r = CalcEngine.format(v)
            history = (listOf(CalcEngine.pretty(CalcEngine.closeParens(CalcEngine.trimTail(expr))) to r) + history).take(20)
            result = r
            // Mit dem Ergebnis weiterrechnen (wissenschaftliche Schreibweise nicht)
            expr = if (r.contains('e')) "" else r.replace(".", "").let { if (it.startsWith("−")) "(−" + it.drop(1) else it }
            typed = ""
            return
        }
        // Nach einem Ergebnis: Ziffer startet neu, Rechenzeichen rechnet weiter
        if (result != null) {
            if (k.length == 1 && (k[0].isDigit() || k == ",")) expr = ""
            result = null
        }
        typed = if (k.length == 1 && k[0].isDigit()) (if (expr.all { it.isDigit() }) typed + k else k).takeLast(12) else ""
        expr = CalcEngine.input(expr, k)
        if (expr.length > 120) expr = expr.take(120)
    }

    val preview = if (result == null && CalcEngine.hasOperation(expr)) CalcEngine.eval(expr)?.let { CalcEngine.format(it) } else null

    GlassBackground(calcBlobs) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Verlauf (nur im Arbeitsspeicher)
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                history.take(3).reversed().forEach { (e, r) ->
                    Text("$e =", style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Color.White.copy(alpha = 0.45f)), maxLines = 1)
                    Text(r, style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f)), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                }
            }
            val shown = if (result != null) result!! else CalcEngine.pretty(expr).ifEmpty { "0" }
            val size = when {
                shown.length > 18 -> 30.sp
                shown.length > 12 -> 40.sp
                shown.length > 8 -> 54.sp
                else -> 68.sp
            }
            Text(
                shown,
                style = androidx.compose.ui.text.TextStyle(fontSize = size, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-1).sp),
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )
            // Live-Ergebnis
            Text(
                if (error) "Ungültige Rechnung" else preview ?: "",
                style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, color = if (error) Color(0xFFFF8A8A) else Color.White.copy(alpha = 0.5f)),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.pressable(Pill, haptic = Haptic.KEY) { press("⌫") }.padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("⌫", style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, color = Color(0xFF7FB2FF))) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Spacer(Modifier.height(14.dp))
            val rows = listOf(
                listOf("C", "()", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("±", "0", ",", "=")
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val unit = (maxWidth - 12.dp * 3) / 4
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (r in rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (k in r) {
                                val isOp = k in listOf("÷", "×", "−", "+")
                                val isEq = k == "="
                                val isFn = k in listOf("C", "()", "%")
                                val bg by animateColorAsState(
                                    when {
                                        isEq -> Color(0xFF2F7CF6)
                                        isOp || isFn -> Color.White.copy(alpha = 0.20f)
                                        else -> Color.White.copy(alpha = 0.10f)
                                    }, tween(200), label = "key"
                                )
                                val fg = when {
                                    k == "C" -> Color(0xFFFF7A7A)
                                    isOp || k == "()" || k == "%" -> Color(0xFF8FE3A8)
                                    else -> Color.White
                                }
                                Box(
                                    Modifier
                                        .width(unit)
                                        .height(minOf(74.dp, unit))
                                        .pressable(Pill, scaleTo = 0.93f, highlight = 0.25f, haptic = Haptic.KEY) { press(k) }
                                        .glass(Pill, bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        k,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = if (isFn || k == "±") 26.sp else 32.sp,
                                            fontWeight = if (isFn) FontWeight.Medium else FontWeight.Normal,
                                            color = fg
                                        )
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
