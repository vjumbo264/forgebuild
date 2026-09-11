package com.forgebuild.calcom

import androidx.compose.runtime.*
import java.text.DecimalFormat
import kotlin.math.*

data class CalcHistoryItem(
    val expression: String,
    val result: String,
    val note: String = ""
)

class CalculatorEngine {
    var expression by mutableStateOf("")
        private set

    var displayResult by mutableStateOf("0")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var history by mutableStateOf<List<CalcHistoryItem>>(emptyList())
        private set

    private val df = DecimalFormat("#,##0.######").apply {
        isGroupingUsed = false
    }

    fun inputDigit(digit: String) {
        errorMessage = null
        if (displayResult != "0" && expression.isEmpty()) {
            // Starting fresh after equals
            expression = digit
        } else {
            expression += digit
        }
        computeLiveResult()
    }

    fun inputDecimal() {
        errorMessage = null
        if (expression.isEmpty() || expression.last() in "+-×÷*/%(") {
            expression += "0."
        } else {
            // Check if current token already has decimal
            val lastToken = expression.takeLastWhile { it.isDigit() || it == '.' }
            if (!lastToken.contains('.')) {
                expression += "."
            }
        }
        computeLiveResult()
    }

    fun inputOperator(op: String) {
        errorMessage = null
        if (expression.isEmpty()) {
            if (displayResult != "0" && displayResult != "Error") {
                expression = displayResult + " " + op + " "
            } else if (op == "-") {
                expression = "-"
            }
            return
        }

        val trimmed = expression.trimEnd()
        if (trimmed.endsWith("+") || trimmed.endsWith("-") || trimmed.endsWith("×") ||
            trimmed.endsWith("÷") || trimmed.endsWith("*") || trimmed.endsWith("/") ||
            trimmed.endsWith("%")
        ) {
            // Replace trailing operator
            val withoutOp = trimmed.dropLast(1).trimEnd()
            expression = withoutOp + " " + op + " "
        } else {
            expression = "$trimmed $op "
        }
        computeLiveResult()
    }

    fun inputFunction(fn: String) {
        errorMessage = null
        if (displayResult != "0" && expression.isEmpty()) {
            expression = "$fn($displayResult)"
        } else {
            expression += "$fn("
        }
        computeLiveResult()
    }

    fun inputParenthesis(p: String) {
        errorMessage = null
        expression += p
        computeLiveResult()
    }

    fun insertHeading(headingDeg: Float) {
        errorMessage = null
        val headingStr = String.format(java.util.Locale.US, "%.1f", headingDeg)
        if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
            expression += " × $headingStr"
        } else {
            expression += headingStr
        }
        computeLiveResult()
    }

    fun addReciprocalBearing(headingDeg: Float) {
        errorMessage = null
        val recip = (headingDeg + 180f) % 360f
        val recipStr = String.format(java.util.Locale.US, "%.1f", recip)
        if (expression.isNotEmpty() && !expression.endsWith(" ")) {
            expression += " + $recipStr"
        } else {
            expression += recipStr
        }
        computeLiveResult()
    }

    fun clearAll() {
        expression = ""
        displayResult = "0"
        errorMessage = null
    }

    fun deleteLast() {
        errorMessage = null
        if (expression.isNotEmpty()) {
            if (expression.endsWith(" ")) {
                expression = expression.dropLast(1).trimEnd()
            } else {
                expression = expression.dropLast(1)
            }
        }
        if (expression.isEmpty()) {
            displayResult = "0"
        } else {
            computeLiveResult()
        }
    }

    fun calculateEquals() {
        if (expression.isBlank()) return
        try {
            val eval = evaluateExpression(expression)
            val formatted = formatResult(eval)
            val note = if (expression.contains("sin") || expression.contains("cos") || expression.contains("tan")) {
                "Trig (deg)"
            } else {
                ""
            }
            history = listOf(CalcHistoryItem(expression, formatted, note)) + history.take(19)
            displayResult = formatted
            expression = ""
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.message ?: "Invalid format"
        }
    }

    private fun computeLiveResult() {
        if (expression.isBlank()) {
            displayResult = "0"
            return
        }
        try {
            val eval = evaluateExpression(expression)
            if (!eval.isNaN() && !eval.isInfinite()) {
                displayResult = formatResult(eval)
            }
        } catch (_: Exception) {
            // Live calculation can fail mid-typing, keep current display
        }
    }

    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            df.format(value)
        }
    }

    companion object {
        /**
         * Evaluates arithmetic expressions with degree-based trigonometry,
         * percentages, sqrt, and standard operator precedence.
         */
        fun evaluateExpression(expr: String): Double {
            var sanitized = expr
                .replace("×", "*")
                .replace("÷", "/")
                .replace(" ", "")

            if (sanitized.isEmpty()) return 0.0

            // Balance unclosed parentheses for live typing
            val openCount = sanitized.count { it == '(' }
            val closeCount = sanitized.count { it == ')' }
            if (openCount > closeCount) {
                sanitized += ")".repeat(openCount - closeCount)
            }

            return ExpressionParser(sanitized).parse()
        }
    }
}

private class ExpressionParser(private val str: String) {
    private var pos = -1
    private var ch = ' '

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos] else '\u0000'
    }

    private fun eat(charToEat: Char): Boolean {
        while (ch == ' ') nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) {
            throw IllegalArgumentException("Unexpected: " + ch)
        }
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            when {
                eat('+') -> x += parseTerm()
                eat('-') -> x -= parseTerm()
                else -> return x
            }
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            when {
                eat('*') -> x *= parseFactor()
                eat('/') -> {
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    x /= divisor
                }
                eat('%') -> x %= parseFactor()
                else -> return x
            }
        }
    }

    private fun parseFactor(): Double {
        if (eat('+')) return +parseFactor()
        if (eat('-')) return -parseFactor()

        var x: Double
        val startPos = pos
        if (eat('(')) {
            x = parseExpression()
            eat(')')
        } else if (ch in '0'..'9' || ch == '.') {
            while (ch in '0'..'9' || ch == '.') nextChar()
            x = str.substring(startPos, pos).toDouble()
        } else if (ch in 'a'..'z') {
            while (ch in 'a'..'z') nextChar()
            val func = str.substring(startPos, pos)
            val arg = if (eat('(')) {
                val a = parseExpression()
                eat(')')
                a
            } else {
                parseFactor()
            }
            x = when (func) {
                "sqrt" -> sqrt(arg)
                // Trigonometry accepts angles in degrees (essential for compass calculations)
                "sin" -> sin(Math.toRadians(arg))
                "cos" -> cos(Math.toRadians(arg))
                "tan" -> tan(Math.toRadians(arg))
                "rad" -> Math.toRadians(arg)
                "deg" -> Math.toDegrees(arg)
                "abs" -> abs(arg)
                else -> throw IllegalArgumentException("Unknown function: $func")
            }
        } else {
            throw IllegalArgumentException("Unexpected character: $ch")
        }

        if (eat('^')) x = x.pow(parseFactor())
        return x
    }
}
