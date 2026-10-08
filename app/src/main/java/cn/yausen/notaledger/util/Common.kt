package cn.yausen.notaledger.util

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared clock and formatters. Everything is stored as epoch millis and converted
 * with the device time zone, so a record keeps the calendar day the user picked.
 */
object Dates {

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val shortDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val compactDayFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
    private val compactFullDayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): LocalDate = LocalDate.now()

    fun currentMonth(): YearMonth = YearMonth.now()

    // ---- Formatters (top level extensions so screens can import them) ----

    fun formatMonth(month: YearMonth): String = month.format(monthFormatter)

    fun formatDay(date: LocalDate): String = date.format(dayFormatter)

    fun formatShortDay(date: LocalDate): String = date.format(shortDayFormatter)

    fun formatCompactDay(date: LocalDate): String = date.format(compactDayFormatter)

    fun formatCompactFullDay(date: LocalDate): String = date.format(compactFullDayFormatter)

    fun formatTime(millis: Long): String = millis.toLocalDateTime().format(timeFormatter)

    fun formatMonthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    fun weekday(date: LocalDate): String = weekdayNames[date.dayOfWeek.value - 1]

    /**
     * How a single day is named: the three days a person thinks of by name, the
     * month and day for anything else this year, and the full date once the year
     * would otherwise be ambiguous.
     */
    fun relativeDayName(date: LocalDate): String {
        val today = today()
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date == today.minusDays(2) -> "前天"
            date.year == today.year -> formatCompactDay(date)
            else -> formatCompactFullDay(date)
        }
    }

    /**
     * The day's name with its weekday: `今天 周一` while a day still goes by name,
     * `07-13 周一` for any other day this year, `2024-03-02 周六` beyond it.
     */
    fun relativeDayWithWeekday(date: LocalDate): String =
        "${relativeDayName(date)} ${weekday(date)}"
}

// First and last millisecond of a month, used for every range query.
fun YearMonth.startMillis(): Long =
    atDay(1).atStartOfDay(Dates.zone).toInstant().toEpochMilli()

fun YearMonth.endMillis(): Long =
    atEndOfMonth().plusDays(1).atStartOfDay(Dates.zone).toInstant().toEpochMilli() - 1

fun YearMonth.toKey(): String = Dates.formatMonthKey(this)

fun YearMonth.label(): String = Dates.formatMonth(this)

fun LocalDate.toMillis(): Long =
    atStartOfDay(Dates.zone).toInstant().toEpochMilli()

fun LocalDate.label(): String = Dates.formatDay(this)

/** `今天 周一`, `07-13 周一` or `2024-03-02 周六` for day headers. */
fun LocalDate.relativeDayWithWeekday(): String = Dates.relativeDayWithWeekday(this)

/** `今天`, `昨天`, `前天`, `07-13` or `2024-03-02`. */
fun LocalDate.relativeDayName(): String = Dates.relativeDayName(this)

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(Dates.zone).toLocalDate()

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(Dates.zone).toLocalDateTime()

fun Long.timeLabel(): String = Dates.formatTime(this)

/** `今天 14:30`, `07-13 14:30`, or `2024-03-02 14:30` once the year matters. */
fun Long.dateTimeLabel(): String = "${toLocalDate().relativeDayName()} ${Dates.formatTime(this)}"

/**
 * Material 3 date pickers identify a day by its UTC midnight, while records are
 * stored at local midnight. These two convert between the two so the chosen day
 * never drifts by one in a non-UTC time zone.
 */
fun LocalDate.toUtcPickerMillis(): Long =
    atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

fun utcPickerMillisToLocalDateMillis(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate().toMillis()

/**
 * Keeps the clock time of this timestamp but moves it to the calendar day of
 * [dayMillis]. The date picker reports a bare day, and a record's time of day is
 * the moment its screen was opened, so picking another day must not reset it to
 * midnight.
 */
fun Long.withDayOf(dayMillis: Long): Long {
    val time = toLocalDateTime().toLocalTime()
    return dayMillis.toLocalDate().atTime(time).atZone(Dates.zone).toInstant().toEpochMilli()
}

/**
 * Keeps the calendar day of this timestamp but replaces its clock time, which is
 * what the time picker reports. Seconds are dropped: a record is stamped at the
 * minute the user chose.
 */
fun Long.withTimeOf(hour: Int, minute: Int): Long =
    toLocalDate().atTime(hour, minute).atZone(Dates.zone).toInstant().toEpochMilli()

/** Money input/parsing and display, all in yuan with two decimals. */
object Money {

    private val formatter: NumberFormat =
        NumberFormat.getNumberInstance(Locale.CHINA).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    fun format(amount: Double): String = formatter.format(amount)

    /** Signed display used in lists: expenses get a leading minus. */
    fun formatSigned(amount: Double, isExpense: Boolean): String =
        if (isExpense) "-${format(amount)}" else "+${format(amount)}"

    fun formatWithSymbol(amount: Double): String = "¥${format(amount)}"

    /** Parses user input leniently; returns null when it is not a valid amount. */
    fun parse(input: String): Double? {
        val cleaned = input.trim().replace("¥", "").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return value
    }

    /** Input field formatting: strips a redundant leading zero and extra dots. */
    fun sanitizeInput(input: String): String {
        var text = input.filter { it.isDigit() || it == '.' }
        val firstDot = text.indexOf('.')
        if (firstDot >= 0) {
            text = text.substring(0, firstDot + 1) +
                text.substring(firstDot + 1).replace(".", "")
        }
        // Keep at most two decimals.
        val dot = text.indexOf('.')
        if (dot >= 0 && text.length - dot - 1 > 2) {
            text = text.substring(0, dot + 3)
        }
        if (text.length > 1 && text.startsWith("0") && !text.startsWith("0.")) {
            text = text.trimStart('0')
            if (text.isEmpty()) text = "0"
        }
        return text
    }
}

/** Palette offered to categories; stored by index so it stays stable for the user. */
object CategoryPalette {

    private val colors = listOf(
        Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
        Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF29B6F6), Color(0xFF26C6DA),
        Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
        Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFFF7043), Color(0xFF8D6E63),
    )

    val size: Int get() = colors.size

    fun colorAt(index: Int): Color = colors[((index % colors.size) + colors.size) % colors.size]
}
