package cn.yausen.notaledger.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The operations offered by the keypad, evaluated left to right the way a pocket
 * calculator does.
 */
enum class CalcOp(val symbol: Char) {
    ADD('+'),
    SUBTRACT('−'),
    MULTIPLY('×'),
    DIVIDE('÷'),
    ;

    /** Combines the two operands this operator stands for. */
    fun combine(left: Double, right: Double): Double = when (this) {
        ADD -> left + right
        SUBTRACT -> left - right
        MULTIPLY -> left * right
        // Dividing by zero has no amount to record; the left side is kept so the
        // expression stays usable instead of turning into Infinity or NaN.
        DIVIDE -> if (right == 0.0) left else left / right
    }

    companion object {
        /** The operator a character stands for, or null for a digit or point. */
        fun fromSymbol(symbol: Char): CalcOp? = entries.firstOrNull { it.symbol == symbol }
    }
}

/**
 * The amount keypad as a single editable expression string, e.g. `13×3`.
 *
 * There is no hidden accumulator and nothing is stored per key: every key appends
 * to or trims this one string, so what the user sees is exactly what is kept. The
 * string is compact — no padding — and [pretty] is the spaced form the display
 * renders. `= 39` is derived from the string on every recomposition (see
 * [result]) and [value] re-derives the number the expression stands for when the
 * entry is saved, so display and stored amount can never disagree.
 *
 * Operators resolve left to right, the behaviour of a pocket calculator, so
 * `2+3×4` is 20 rather than 14. Money has two decimals, so each step rounds back
 * to fen and binary floating point never reaches the ledger.
 */
@JvmInline
value class CalculatorState(val expression: String = "") {

    /** The number the expression stands for; 0 for an empty or incomplete entry. */
    val value: Double get() = evaluate(expression)

    /** Whether there is an amount worth saving: the ledger refuses zero. */
    val hasValue: Boolean get() = value > 0.0

    /** Whether a clear press would remove anything. */
    val isClearable: Boolean get() = expression.isNotEmpty()

    /** True when the last character is an operator still waiting for its operand. */
    private val waitsForOperand: Boolean
        get() = expression.isNotEmpty() && CalcOp.fromSymbol(expression.last()) != null

    /**
     * `13 × 3` — the expression with spaces around its operators. Purely for
     * display; the stored string stays compact.
     */
    val pretty: String
        get() = expression.map { ch ->
            if (CalcOp.fromSymbol(ch) != null) " $ch " else ch.toString()
        }.joinToString("")

    /**
     * The figure the keypad shows: the digits being typed, or the running total
     * when the expression currently ends in an operator. The `= total` beside it
     * comes from [result], never from the string.
     */
    val display: String
        get() = when {
            expression.isEmpty() -> "0"
            waitsForOperand -> format(value)
            else -> currentOperand(expression)
        }

    /**
     * `= 37.5` for the expression as it currently stands, or empty when there is
     * nothing to resolve (a bare number, or an operator waiting for its operand).
     * Derived purely from the string, so it updates on every key press.
     */
    val result: String
        get() = if (pendingOpCount(expression) == 0 || waitsForOperand) "" else "= ${format(value)}"

    fun digit(d: Int): CalculatorState {
        val operand = currentOperand(expression)
        // Avoid building numbers like 007; a lone 0 is one keystroke, not two.
        if (operand == "0") return CalculatorState(expression.dropLast(1) + d)
        if (operand.length >= MAX_DIGITS) return this
        return CalculatorState(expression + d)
    }

    fun decimal(): CalculatorState = when {
        currentOperand(expression).contains('.') -> this
        // A point with no digits yet reads as `0.` — both at the start and right
        // after an operator, which would otherwise leave `12+.`.
        currentOperand(expression).isEmpty() -> CalculatorState("${expression}0.")
        else -> CalculatorState("$expression.")
    }

    /** Removes the last character typed — an operator, a digit or a point alike. */
    fun backspace(): CalculatorState =
        if (expression.isEmpty()) this else CalculatorState(expression.dropLast(1))

    /**
     * Appends an operator. Whatever came before is resolved into the total that
     * the display shows from then on, so `13×` states the operation and the next
     * number continues the calculation from 13.
     */
    fun operator(op: CalcOp): CalculatorState = when {
        expression.isEmpty() -> CalculatorState("0${op.symbol}")
        // Two operators in a row just replaces the waiting one.
        waitsForOperand -> CalculatorState(expression.dropLast(1) + op.symbol)
        else -> CalculatorState("${format(value)}${op.symbol}")
    }

    /** Wipes the expression back to an empty keypad. */
    fun clear(): CalculatorState = CalculatorState()

    companion object {
        /** Long enough for any realistic amount, short enough to stay on screen. */
        const val MAX_DIGITS = 12

        /** Money has two decimals; rounding each step keeps the sum exact in fen. */
        fun round(value: Double): Double =
            BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

        /** Trims meaningless zeros: 12.50 -> 12.5, 12.00 -> 12. */
        fun format(value: Double): String {
            val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
            return rounded.stripTrailingZeros().toPlainString()
        }

        /** The digits after the last operator, i.e. what is being typed right now. */
        fun currentOperand(expression: String): String {
            val start = expression.indexOfLast { CalcOp.fromSymbol(it) != null }
            return expression.substring(start + 1)
        }

        /** How many operators the expression carries, i.e. whether it computes. */
        fun pendingOpCount(expression: String): Int =
            expression.count { CalcOp.fromSymbol(it) != null }

        /**
         * The number an expression stands for, evaluated left to right exactly as
         * written. A trailing operator contributes the total before it, so `35+`
         * is 35 and `12+3×` is 15.
         */
        fun evaluate(expression: String): Double {
            var accumulator = 0.0
            var pendingOp: CalcOp? = null
            var operand = ""
            var sawOperand = false

            fun settle() {
                if (operand.isEmpty()) return
                val number = operand.toDoubleOrNull() ?: 0.0
                val op = pendingOp
                accumulator =
                    if (op == null) round(number) else round(op.combine(accumulator, number))
                operand = ""
                sawOperand = true
            }

            expression.forEach { ch ->
                val op = CalcOp.fromSymbol(ch)
                if (op != null) {
                    settle()
                    pendingOp = op
                } else {
                    operand += ch
                }
            }
            settle()
            return if (sawOperand) accumulator else 0.0
        }
    }
}
