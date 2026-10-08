package cn.yausen.notaledger.data

import android.content.Context
import java.time.YearMonth

/**
 * Small preference store for the handful of settings that are not ledger rows.
 * A monthly budget is a single running number, not per-month history, so this
 * belongs here rather than in the database.
 */
class UserPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notaledger-settings", Context.MODE_PRIVATE)

    /** Monthly spending budget in yuan; null means the user has not set one. */
    var monthlyBudget: Double?
        get() = if (prefs.contains(KEY_BUDGET)) prefs.getFloat(KEY_BUDGET, 0f).toDouble() else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_BUDGET) else putFloat(KEY_BUDGET, value.toFloat())
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

    private companion object {
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_WARN = "budget_warning"
        const val KEY_MONTH = "last_viewed_month"
    }
}
