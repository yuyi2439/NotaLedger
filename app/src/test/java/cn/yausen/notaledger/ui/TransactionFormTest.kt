package cn.yausen.notaledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry panel renders the form's [TransactionForm.dateMillis] as its date and
 * clock time, so the default has to be the moment the screen opened — a bare day
 * would show `00:00` — and the time that is shown has to be the time that is saved.
 */
class TransactionFormTest {

    @Test
    fun `a new form starts at the current clock time`() {
        val before = System.currentTimeMillis()
        val form = TransactionForm()
        val after = System.currentTimeMillis()

        assertTrue(form.dateMillis in before..after)
    }

    @Test
    fun `editing keeps the record's own timestamp`() {
        val recorded = System.currentTimeMillis() - 90 * 60 * 1000

        assertEquals(recorded, TransactionForm(amount = 5.0, dateMillis = recorded).dateMillis)
    }
}
