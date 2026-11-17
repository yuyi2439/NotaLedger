package cn.yausen.notaledger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun decimalRoundingAndFloatingPointInput() {
        assertEquals(30L, Money.toCents(0.1 + 0.2))
        assertEquals(101L, Money.toCents(1.005))
        assertEquals(268L, Money.toCents(2.675))
        assertEquals(0L, Money.toCents(0.004))
        assertEquals(99999999999L, Money.toCents(999999999.99))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNaN() { Money.toCents(Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInfinity() { Money.toCents(Double.POSITIVE_INFINITY) }

    @Test(expected = ArithmeticException::class)
    fun rejectsOverflow() { Money.toCents(Double.MAX_VALUE) }
}
