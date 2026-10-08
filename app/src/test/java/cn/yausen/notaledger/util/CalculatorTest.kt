package cn.yausen.notaledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keypad is a single expression string with a derived result, so these tests
 * pin down both what the string becomes and what the display and saved amount
 * read at every step. The stored string is compact (`13×3`); [CalculatorState.pretty]
 * is only what the display renders.
 */
class CalculatorTest {

    private fun digits(state: CalculatorState, text: String): CalculatorState {
        var s = state
        text.forEach { ch ->
            s = if (ch == '.') s.decimal() else s.digit(ch - '0')
        }
        return s
    }

    @Test
    fun `plain entry is the value`() {
        val s = digits(CalculatorState(), "25.50")
        assertEquals("25.50", s.expression)
        assertEquals(25.5, s.value, 0.0)
        assertEquals("25.50", s.display)
        // A bare number has nothing to resolve, so no `= result` is shown.
        assertEquals("", s.result)
    }

    @Test
    fun `addition resolves left to right`() {
        val s = digits(CalculatorState(), "12").operator(CalcOp.ADD)
        assertEquals("12+", s.expression)
        assertEquals(12.0, s.value, 0.0)
        val t = digits(s, "3")
        assertEquals("12+3", t.expression)
        assertEquals(15.0, t.value, 0.0)
        assertEquals("= 15", t.result)
        // Only the rendering carries spaces.
        assertEquals("12 + 3", t.pretty)
    }

    @Test
    fun `immediate execution ignores precedence`() {
        // 2 + 3 x 4 must be 20, the pocket-calculator answer, not 14. The earlier
        // step collapses into the running total, so the stored line reads `5×4`.
        val s = digits(CalculatorState(), "2").operator(CalcOp.ADD)
        val t = digits(s, "3").operator(CalcOp.MULTIPLY)
        assertEquals("5×", t.expression)
        val u = digits(t, "4")
        assertEquals("5×4", u.expression)
        assertEquals(20.0, u.value, 0.0)
        assertEquals("= 20", u.result)
    }

    @Test
    fun `saving evaluates the expression without an equals press`() {
        // The expression is the state, so the stored number is simply its value.
        val s = digits(digits(CalculatorState(), "13").operator(CalcOp.MULTIPLY), "3")
        assertEquals("13×3", s.expression)
        assertEquals(39.0, s.value, 0.0)
    }

    @Test
    fun `thirteen times three reads 39 as it is typed`() {
        val s = digits(CalculatorState(), "13")
        assertEquals("13", s.display)
        assertEquals("", s.result)
        val withOp = s.operator(CalcOp.MULTIPLY)
        assertEquals("13×", withOp.expression)
        // The figure shows the total so far while the second operand is awaited.
        assertEquals("13", withOp.display)
        val t = digits(withOp, "3")
        assertEquals("3", t.display)
        assertEquals("= 39", t.result)
        assertEquals(39.0, t.value, 0.0)
    }

    @Test
    fun `a chain accumulates into the next expression`() {
        // 13 x 3 then +: the 39 becomes the operand of the next step.
        val s = digits(digits(CalculatorState(), "13").operator(CalcOp.MULTIPLY), "3")
        val t = s.operator(CalcOp.ADD)
        assertEquals("39+", t.expression)
        assertEquals(40.0, digits(t, "1").value, 0.0)
    }

    @Test
    fun `digits typed after an operator append to the running operand`() {
        // 13 x 34: the second operand builds up as 34, not 3 then 4.
        val s = digits(CalculatorState(), "13").operator(CalcOp.MULTIPLY)
        val t = digits(s, "34")
        assertEquals("13×34", t.expression)
        assertEquals(442.0, t.value, 0.0)
        assertEquals("34", t.display)
    }

    @Test
    fun `changing the operator replaces it`() {
        val s = digits(CalculatorState(), "5").operator(CalcOp.ADD).operator(CalcOp.SUBTRACT)
        // 5 - <nothing yet> still reads as 5, not as an error.
        assertEquals("5−", s.expression)
        assertEquals(5.0, s.value, 0.0)
        assertEquals(3.0, digits(s, "2").value, 0.0)
    }

    @Test
    fun `subtraction can go negative`() {
        val s = digits(CalculatorState(), "5").operator(CalcOp.SUBTRACT)
        assertEquals(-3.0, digits(s, "8").value, 0.0)
    }

    @Test
    fun `multiplication of money rounds to fen`() {
        val s = digits(CalculatorState(), "3.33").operator(CalcOp.MULTIPLY)
        assertEquals(9.99, digits(s, "3").value, 0.0)
    }

    @Test
    fun `division rounds to fen`() {
        val s = digits(CalculatorState(), "100").operator(CalcOp.DIVIDE)
        assertEquals("100÷", s.expression)
        val t = digits(s, "3")
        assertEquals("100÷3", t.expression)
        assertEquals(33.33, t.value, 0.0)
        assertEquals("= 33.33", t.result)
    }

    @Test
    fun `dividing by zero keeps the amount instead of producing infinity`() {
        val s = digits(digits(CalculatorState(), "100").operator(CalcOp.DIVIDE), "0")
        assertEquals("100÷0", s.expression)
        // The ledger must never receive Infinity or NaN from a stray zero.
        assertEquals(100.0, s.value, 0.0)
        assertEquals("= 100", s.result)
    }

    @Test
    fun `division chains with the other operators`() {
        // 100 ÷ 4 + 1 = 26, evaluated left to right.
        val s = digits(digits(CalculatorState(), "100").operator(CalcOp.DIVIDE), "4")
        val t = s.operator(CalcOp.ADD)
        assertEquals("25+", t.expression)
        assertEquals(26.0, digits(t, "1").value, 0.0)
    }

    @Test
    fun `an operator first starts from zero`() {
        val s = CalculatorState().operator(CalcOp.ADD)
        assertEquals("0+", s.expression)
        assertEquals(7.0, digits(s, "7").value, 0.0)
    }

    @Test
    fun `an expression that comes out as zero is not saveable`() {
        val s = digits(digits(CalculatorState(), "13").operator(CalcOp.MULTIPLY), "0")
        assertEquals("13×0", s.expression)
        assertEquals(0.0, s.value, 0.0)
        assertEquals("= 0", s.result)
        assertFalse(s.hasValue)
        assertTrue(s.isClearable)
    }

    @Test
    fun `a trailing decimal point stays visible`() {
        val s = digits(CalculatorState(), "12").decimal()
        assertEquals("12.", s.expression)
        assertEquals("12.", s.display)
        // A half-typed decimal is not a number yet, and it must not blank out.
        assertEquals(12.0, s.value, 0.0)
    }

    @Test
    fun `decimal after an operator starts a zero point`() {
        val s = digits(CalculatorState(), "12").operator(CalcOp.ADD).decimal()
        assertEquals("12+0.", s.expression)
        assertEquals("0.", s.display)
        assertEquals("= 12", s.result)
        assertEquals(12.5, digits(s, "5").value, 0.0)
        assertEquals("12+0.5", digits(s, "5").expression)
    }

    @Test
    fun `a second decimal point is ignored`() {
        assertEquals("1.55", digits(CalculatorState(), "1.5.5").expression)
    }

    @Test
    fun `leading zeros are collapsed`() {
        assertEquals("7", digits(CalculatorState(), "007").expression)
        assertEquals("0.5", digits(CalculatorState(), "0.5").expression)
    }

    @Test
    fun `backspace removes the last character`() {
        val s = digits(CalculatorState(), "123")
        assertEquals("12", s.backspace().expression)
        assertEquals("1", s.backspace().backspace().expression)
        assertEquals("", s.backspace().backspace().backspace().expression)
        assertEquals("", s.backspace().backspace().backspace().backspace().expression)
    }

    @Test
    fun `backspace peels off a trailing operator`() {
        val s = digits(CalculatorState(), "35").operator(CalcOp.ADD)
        assertEquals("35+", s.expression)
        assertEquals("35", s.backspace().expression)
        // A decimal point is one character too.
        assertEquals("35", digits(CalculatorState(), "35.").backspace().expression)
    }

    @Test
    fun `clear empties the expression`() {
        val fresh = CalculatorState()
        assertFalse(fresh.isClearable)
        assertFalse(fresh.hasValue)
        assertEquals("", fresh.expression)
        assertEquals("0", fresh.display)
        assertEquals("", fresh.result)

        val s = digits(CalculatorState(), "13").operator(CalcOp.MULTIPLY)
        assertEquals("", s.clear().expression)
        assertFalse(s.clear().isClearable)
    }

    @Test
    fun `clearable only when something was entered`() {
        assertFalse(CalculatorState().isClearable)
        assertTrue(digits(CalculatorState(), "1").isClearable)
        assertTrue(digits(CalculatorState(), "5").operator(CalcOp.ADD).isClearable)
    }

    @Test
    fun `only a positive amount is saveable`() {
        assertFalse(CalculatorState().hasValue)
        assertFalse(digits(CalculatorState(), "0").hasValue)
        assertTrue(digits(CalculatorState(), "1").hasValue)
        // An expression that is still missing its operand counts the part it has.
        assertTrue(digits(CalculatorState(), "12").operator(CalcOp.ADD).hasValue)
    }

    @Test
    fun `entry length is capped`() {
        val s = digits(CalculatorState(), "1".repeat(CalculatorState.MAX_DIGITS + 5))
        assertEquals(CalculatorState.MAX_DIGITS, CalculatorState.currentOperand(s.expression).length)
    }

    @Test
    fun `format trims meaningless zeros`() {
        assertEquals("12", CalculatorState.format(12.0))
        assertEquals("12.5", CalculatorState.format(12.50))
        assertEquals("0", CalculatorState.format(0.0))
    }

    @Test
    fun `evaluate reads an expression left to right`() {
        assertEquals(37.5, CalculatorState.evaluate("35+2.5"), 0.0)
        assertEquals(20.0, CalculatorState.evaluate("2+3×4"), 0.0)
        // An unfinished expression contributes what it has already resolved.
        assertEquals(35.0, CalculatorState.evaluate("35+"), 0.0)
        assertEquals(15.0, CalculatorState.evaluate("12+3×"), 0.0)
        assertEquals(0.0, CalculatorState.evaluate(""), 0.0)
    }
}
