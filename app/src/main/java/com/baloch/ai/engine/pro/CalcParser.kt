package com.baloch.ai.engine.pro

/**
 * Hand-written recursive-descent maths parser.
 *
 * Grammar:
 *   expr   := term (('+' | '-') term)*
 *   term   := factor (('*' | '/' | '%') factor)*     // % is modulo here
 *   factor := unary ('^' factor)?                    // right-associative power
 *   unary  := '-' unary | primary
 *   primary:= number | '(' expr ')'
 *
 * Special case handled before parsing: "X% of Y" → X/100*Y.
 * Returns the formatted result, or null when the expression is invalid.
 */
object CalcParser {

    /** "25% of 800" → 200.0, else null. */
    private val PERCENT_OF_RX = Regex("""^\s*([\d.]+)\s*%\s*of\s*([\d.]+)\s*$""")

    fun eval(rawExpr: String): String? {
        val expr = rawExpr.trim()
        PERCENT_OF_RX.find(expr)?.let {
            val x = it.groupValues[1].toDoubleOrNull() ?: return null
            val y = it.groupValues[2].toDoubleOrNull() ?: return null
            return format(x / 100.0 * y)
        }
        return try {
            format(Parser(expr).parse())
        } catch (_: Exception) {
            null
        }
    }

    /** 200.0 → "200", 3.14159265 → "3.1416", 1/3 → "0.3333". */
    fun format(v: Double): String {
        if (!v.isFinite()) return "∞"
        val asLong = v.toLong()
        if (v == asLong.toDouble() && kotlin.math.abs(v) < 1e15) return asLong.toString()
        var s = "%.4f".format(java.util.Locale.US, v)
        s = s.trimEnd('0').trimEnd('.')
        return s.ifBlank { "0" }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            if (i != s.length) throw IllegalArgumentException("trailing input")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skipWs()
                v = when {
                    consume('+') -> v + term()
                    consume('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skipWs()
                v = when {
                    consume('*') -> v * factor()
                    consume('/') -> {
                        val d = factor()
                        if (d == 0.0) throw ArithmeticException("divide by zero")
                        v / d
                    }
                    consume('%') -> {
                        val d = factor()
                        if (d == 0.0) throw ArithmeticException("modulo by zero")
                        v % d
                    }
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            val base = unary()
            skipWs()
            return if (consume('^')) {
                val exp = factor() // right-associative
                Math.pow(base, exp)
            } else base
        }

        private fun unary(): Double {
            skipWs()
            return if (consume('-')) -unary() else primary()
        }

        private fun primary(): Double {
            skipWs()
            if (consume('(')) {
                val v = expr()
                skipWs()
                if (!consume(')')) throw IllegalArgumentException("missing )")
                return v
            }
            val start = i
            var dots = 0
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                if (s[i] == '.') dots++
                if (dots > 1) throw IllegalArgumentException("bad number")
                i++
            }
            if (start == i) throw IllegalArgumentException("expected number at $i")
            return s.substring(start, i).toDouble()
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun consume(c: Char): Boolean {
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }
    }
}
