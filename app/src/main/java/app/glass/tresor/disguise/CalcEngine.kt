package app.glass.tresor.disguise

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Rechenwerk für den Rechner (wie beim Samsung-Rechner):
 * ganze Ausdrücke mit Punkt-vor-Strich, Klammern, Prozent und Vorzeichen.
 * Interne Schreibweise: Ziffern, ',' als Komma, + − × ÷ ( ) %
 */
object CalcEngine {
    private val MC = MathContext(20, RoundingMode.HALF_UP)
    const val OPS = "+−×÷"

    class Err : Exception()

    /** Ergebnis des Ausdrucks oder null (unvollständig / Fehler). */
    fun eval(expr: String): BigDecimal? = try {
        val e = trimTail(expr)
        if (e.isEmpty()) null else Parser(closeParens(e)).run()
    } catch (_: Exception) {
        null
    }

    /** Unfertiges Ende entfernen (z. B. „5+“ → „5“), damit die Vorschau schon rechnet. */
    fun trimTail(expr: String): String {
        var e = expr
        while (e.isNotEmpty() && (e.last() in OPS || e.last() == '(' || e.last() == ',')) e = e.dropLast(1)
        return e
    }

    /** Offene Klammern automatisch schließen. */
    fun closeParens(expr: String): String {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        return if (open > 0) expr + ")".repeat(open) else expr
    }

    /** Enthält der Ausdruck eine echte Rechnung (nicht nur eine Zahl)? */
    fun hasOperation(expr: String): Boolean {
        val body = expr.removePrefix("(−").removePrefix("−")
        return body.any { it in OPS || it == '%' || it == '(' || it == ')' }
    }

    private class Parser(val s: String) {
        var i = 0

        fun run(): BigDecimal {
            val v = sum()
            if (i != s.length) throw Err()
            return v
        }

        fun sum(): BigDecimal {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '−')) {
                val op = s[i++]
                val start = i
                var r = product()
                // „200+10%“ → 200 + 10 % von 200 (wie bei Taschenrechnern üblich)
                if (s.getOrNull(i - 1) == '%' && isPlainPercent(start)) r = v.multiply(r, MC)
                v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
            }
            return v
        }

        private fun isPlainPercent(start: Int): Boolean {
            val part = s.substring(start, i)
            return part.count { it == '%' } == 1 && part.dropLast(1).all { it.isDigit() || it == ',' }
        }

        fun product(): BigDecimal {
            var v = unary()
            while (i < s.length) {
                val c = s[i]
                if (c == '×' || c == '÷') {
                    i++
                    val r = unary()
                    v = if (c == '×') v.multiply(r, MC) else {
                        if (r.signum() == 0) throw Err()
                        v.divide(r, MC)
                    }
                } else if (c == '(' || c.isDigit()) {
                    // „2(3+4)“ oder „(1+2)(3)“ → Malzeichen ergänzen
                    v = v.multiply(unary(), MC)
                } else break
            }
            return v
        }

        fun unary(): BigDecimal {
            if (i < s.length && s[i] == '−') { i++; return unary().negate() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return postfix()
        }

        fun postfix(): BigDecimal {
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v = v.divide(BigDecimal(100), MC) }
            return v
        }

        fun atom(): BigDecimal {
            if (i >= s.length) throw Err()
            if (s[i] == '(') {
                i++
                val v = sum()
                if (i >= s.length || s[i] != ')') throw Err()
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == ',')) i++
            if (start == i) throw Err()
            val t = s.substring(start, i).replace(',', '.')
            if (t == ".") throw Err()
            return BigDecimal(if (t.startsWith(".")) "0$t" else if (t.endsWith(".")) t.dropLast(1) else t)
        }
    }

    /** Zahl hübsch formatieren: 1.234.567,89 – sehr große/kleine Zahlen wissenschaftlich. */
    fun format(v: BigDecimal?): String {
        if (v == null) return ""
        val r = v.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (r.signum() == 0) return "0"
        if (r.abs() >= BigDecimal("1e15") || r.abs() < BigDecimal("1e-9")) {
            return "%.6e".format(java.util.Locale.ROOT, r.toDouble()).replace('.', ',')
        }
        val plain = r.toPlainString()
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        return (if (neg) "−" else "") + group(intPart) + if (frac.isNotEmpty()) ",$frac" else ""
    }

    fun group(intPart: String): String = intPart.reversed().chunked(3).joinToString(".").reversed()

    /** Ausdruck für die Anzeige: Tausenderpunkte in Zahlen einfügen. */
    fun pretty(expr: String): String {
        val out = StringBuilder()
        var k = 0
        while (k < expr.length) {
            if (expr[k].isDigit()) {
                val st = k
                while (k < expr.length && expr[k].isDigit()) k++
                val digits = expr.substring(st, k)
                // Nachkommastellen nicht gruppieren
                out.append(if (st > 0 && expr[st - 1] == ',') digits else group(digits))
            } else {
                out.append(expr[k]); k++
            }
        }
        return out.toString()
    }

    /** Taste in den Ausdruck einfügen (mit den Regeln eines normalen Rechners). */
    fun input(expr: String, key: String): String {
        val last = expr.lastOrNull()
        return when {
            key.length == 1 && key[0].isDigit() -> {
                if (last == ')' || last == '%') return "$expr×$key"
                val num = currentNumber(expr)
                if (num.count { it.isDigit() } >= 15) return expr
                if (num == "0") expr.dropLast(1) + key else expr + key
            }
            key == "," -> {
                val num = currentNumber(expr)
                when {
                    num.contains(',') -> expr
                    last == ')' || last == '%' -> "$expr×0,"
                    num.isEmpty() -> expr + "0,"
                    else -> "$expr,"
                }
            }
            key.length == 1 && key[0] in OPS -> {
                when {
                    expr.isEmpty() -> if (key == "−") "−" else expr
                    last == '(' -> if (key == "−") expr + key else expr
                    last != null && last in OPS -> if (expr.length == 1) expr else expr.dropLast(1) + key
                    last == ',' -> expr.dropLast(1) + key
                    else -> expr + key
                }
            }
            key == "%" -> if (last != null && (last.isDigit() || last == ')')) "$expr%" else expr
            key == "()" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                when {
                    last == null || last == '(' || last in OPS -> "$expr("
                    open > 0 && (last.isDigit() || last == ')' || last == '%') -> "$expr)"
                    last == ',' -> expr
                    else -> "$expr×("
                }
            }
            key == "±" -> toggleSign(expr)
            key == "⌫" -> expr.dropLast(1)
            else -> expr
        }
    }

    private fun currentNumber(expr: String): String {
        var k = expr.length
        while (k > 0 && (expr[k - 1].isDigit() || expr[k - 1] == ',')) k--
        return expr.substring(k)
    }

    /** Vorzeichen der letzten Zahl umschalten: 5 → (−5 → 5 */
    private fun toggleSign(expr: String): String {
        val num = currentNumber(expr)
        val head = expr.dropLast(num.length)
        return when {
            head.endsWith("(−") -> head.dropLast(2) + num
            else -> "$head(−$num"
        }
    }
}
