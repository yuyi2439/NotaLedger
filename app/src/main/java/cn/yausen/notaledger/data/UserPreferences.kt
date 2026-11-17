package cn.yausen.notaledger.data

import android.content.Context
import java.time.YearMonth

/**
 * Small preference store for the handful of settings that are not ledger rows.
 * A monthly budget is a single running number, not per-month history, so this
 * belongs here rather than in the database.
 */
class UserPreferences(context: Context) {

    private val capturePrefs = context.applicationContext.getSharedPreferences("capture-settings", Context.MODE_PRIVATE)

    var notificationCaptureEnabled: Boolean
        get() = capturePrefs.getBoolean("notification_capture", false)
        set(value) = capturePrefs.edit().putBoolean("notification_capture", value).apply()

    var foregroundReminderEnabled: Boolean
        get() = capturePrefs.getBoolean("foreground_reminder", false)
        set(value) = capturePrefs.edit().putBoolean("foreground_reminder", value).apply()

    private val prefs = context.applicationContext
        .getSharedPreferences("notaledger-settings", Context.MODE_PRIVATE)

    /** Monthly spending budget in yuan; null means the user has not set one. */
    var monthlyBudget: Double?
        get() = when {
            prefs.contains(KEY_BUDGET_CENTS) -> prefs.getLong(KEY_BUDGET_CENTS, 0L) / 100.0
            prefs.contains(KEY_BUDGET) -> Money.toCents(prefs.getFloat(KEY_BUDGET, 0f).toString().toDouble()) / 100.0
            else -> null
        }
        set(value) {
            prefs.edit().apply {
                remove(KEY_BUDGET)
                if (value == null) remove(KEY_BUDGET_CENTS) else putLong(KEY_BUDGET_CENTS, Money.toCents(value))
            }.apply()
        }

    /** Whether overspending should be surfaced on the home screen. */
    var budgetWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_WARN, true)
        set(value) = prefs.edit().putBoolean(KEY_WARN, value).apply()

    /**
     * The month the user was last looking at, so reopening the app lands where
     * they left off instead of jumping back to the current month.
     */
    var lastViewedMonth: YearMonth?
        get() = prefs.getString(KEY_MONTH, null)?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        }
        set(value) = prefs.edit().putString(KEY_MONTH, value?.toString()).apply()

    /**
     * Whether the default categories have already been written. This is a stored
     * flag rather than a "the table is empty" check because those categories are
     * deletable now: a user who clears them out must not find them back next launch.
     */
    var categoriesSeeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

    private companion object {
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_BUDGET_CENTS = "monthly_budget_cents"
        const val KEY_WARN = "budget_warning"
        const val KEY_MONTH = "last_viewed_month"
        const val KEY_SEEDED = "categories_seeded"
    }
}
