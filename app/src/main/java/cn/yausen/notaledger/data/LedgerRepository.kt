package cn.yausen.notaledger.data

import cn.yausen.notaledger.util.Dates
import cn.yausen.notaledger.util.endMillis
import cn.yausen.notaledger.util.startMillis
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Income / expense / balance for one month. */
data class MonthSummary(
    val income: Double = 0.0,
    val expense: Double = 0.0,
) {
    val balance: Double get() = income - expense
}

/**
 * Everything the UI needs from storage. Keeps Compose screens free of SQL and
 * makes the aggregation queries reusable across screens.
 */
class LedgerRepository(private val database: AppDatabase) {

    private val dao = database.ledgerDao()

    fun observeCategories(): Flow<List<Category>> = dao.observeCategories()

    fun observeCategories(kind: Kind): Flow<List<Category>> =
        dao.observeCategories().map { list -> list.filter { it.kind == kind.code } }

    fun observeTransactions(month: YearMonth): Flow<List<Transaction>> =
        dao.observeBetween(month.startMillis(), month.endMillis())

    fun observeSummary(month: YearMonth): Flow<MonthSummary> {
        val start = month.startMillis()
        val end = month.endMillis()
        return combine(
            dao.observeSum(Kind.EXPENSE.code, start, end),
            dao.observeSum(Kind.INCOME.code, start, end),
        ) { expense, income -> MonthSummary(income = income, expense = expense) }
    }

    fun observeCategoryTotals(month: YearMonth, kind: Kind): Flow<List<CategoryTotal>> =
        dao.observeCategoryTotals(kind.code, month.startMillis(), month.endMillis())

    fun observeDailyTotals(month: YearMonth, kind: Kind): Flow<List<DailyTotal>> =
        dao.observeDailyTotals(kind.code, month.startMillis(), month.endMillis())

    fun observeMonthlyTrend(kind: Kind, from: YearMonth, to: YearMonth): Flow<List<DailyTotal>> =
        dao.observeMonthlyTotals(kind.code, from.startMillis(), to.endMillis())

    suspend fun findTransaction(id: Long): Transaction? = dao.findById(id)

    /** One-shot read for screens that need the list once instead of collecting it. */
    suspend fun currentCategories(): List<Category> = dao.observeCategories().first()

    /** One-shot read of a single month, used by the statistics screen. */
    suspend fun transactionsIn(month: YearMonth): List<Transaction> =
        dao.observeBetween(month.startMillis(), month.endMillis()).first()

    suspend fun addTransaction(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun updateTransaction(transaction: Transaction) = dao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) = dao.delete(transaction)

    suspend fun deleteTransactionById(id: Long) = dao.deleteById(id)

    suspend fun findCategory(id: Long): Category? = dao.findCategory(id)

    suspend fun addCategory(category: Category): Long = dao.insertCategory(category)

    suspend fun updateCategory(category: Category) = dao.updateCategory(category)

    suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)

    /** Guards the UI: a category that already has records must not be removed silently. */
    suspend fun usageCount(categoryId: Long): Int = dao.countTransactionsInCategory(categoryId)

    suspend fun hasAnyTransaction(): Boolean = dao.countTransactions() > 0

    /** Seeds the default categories once, on first launch. */
    suspend fun seedDefaultsIfNeeded() = DefaultCategories.seedIfEmpty(dao)

    /** Month bounds helper for screens that need the raw range. */
    fun monthBounds(month: YearMonth): Pair<Long, Long> = month.startMillis() to month.endMillis()

    /** Formats a stored month key back into a [YearMonth], falling back to now. */
    fun monthFromKey(key: String?): YearMonth = key?.let {
        runCatching { YearMonth.parse(it) }.getOrNull()
    } ?: Dates.currentMonth()
}
