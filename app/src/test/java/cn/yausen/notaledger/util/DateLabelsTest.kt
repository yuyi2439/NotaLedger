package cn.yausen.notaledger.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The date wording used by the entry panel and the ledger rows. */
class DateLabelsTest {

    private val today = Dates.today()

    @Test
    fun `the last three days are named`() {
        assertEquals("今天", Dates.relativeDayName(today))
        assertEquals("昨天", Dates.relativeDayName(today.minusDays(1)))
        assertEquals("前天", Dates.relativeDayName(today.minusDays(2)))
    }

    @Test
    fun `other days in this year are month and day`() {
        val date = today.minusDays(10)
        assertEquals(Dates.formatCompactDay(date), Dates.relativeDayName(date))
    }

    @Test
    fun `days in another year keep the year`() {
        val date = today.minusYears(2)
        assertEquals(Dates.formatCompactFullDay(date), Dates.relativeDayName(date))
    }

    @Test
    fun `day headers name the day and its weekday`() {
        val yesterday = today.minusDays(1)
        // The named days read as `昨天 周二`: the name is the date.
        assertEquals("昨天 ${Dates.weekday(yesterday)}", Dates.relativeDayWithWeekday(yesterday))
    }

    @Test
    fun `day headers past the named three carry the date`() {
        val older = today.minusDays(10)
        val header = Dates.relativeDayWithWeekday(older)
        assertEquals("${Dates.formatCompactDay(older)} ${Dates.weekday(older)}", header)
    }

    @Test
    fun `a timestamp reads as date and time`() {
        val millis = today.toMillis() + 14 * 60 * 60 * 1000 + 30 * 60 * 1000
        assertEquals("今天 14:30", millis.dateTimeLabel())
    }

    @Test
    fun `picking another day keeps the clock time`() {
        val started = today.toMillis() + 14 * 60 * 60 * 1000 + 30 * 60 * 1000
        val picked = today.minusDays(3).toMillis()
        val moved = started.withDayOf(picked)
        assertEquals(today.minusDays(3), moved.toLocalDate())
        assertEquals("14:30", Dates.formatTime(moved))
    }

    @Test
    fun `picking another time keeps the day`() {
        val started = today.toMillis() + 9 * 60 * 60 * 1000 + 5 * 60 * 1000
        val moved = started.withTimeOf(hour = 21, minute = 5)
        assertEquals(today, moved.toLocalDate())
        assertEquals("21:05", Dates.formatTime(moved))
    }
}
