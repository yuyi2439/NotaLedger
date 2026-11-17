package cn.yausen.notaledger.data

import androidx.room.withTransaction
import cn.yausen.notaledger.util.Dates
import cn.yausen.notaledger.util.endMillis
import cn.yausen.notaledger.util.startMillis
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Income / expense / balance for one month. */
data class MonthSummary(
    val income: Double = 0.0,
    val expense: Double = 0.0,
) {
    val balance: Double get() = income - expense
}

/**
 * What deleting a category is up against: the sub-categories that would go with it
 * and the records spread across that subtree. Records block the delete, because a
 * removed category would leave them pointing at nothing.
 */
data class CategoryUsage(val childCount: Int, val recordCount: Int) {
    val isDeletable: Boolean get() = recordCount == 0
}

/**
 * Everything the UI needs from storage. Keeps Compose screens free of SQL and
 * makes the aggregation queries reusable across screens.
 */
class LedgerRepository(
    private val database: AppDatabase,
    private val preferences: UserPreferences,
) {

    private val dao = database.ledgerDao()

    /** Guards the one-time seed when two screens ask for it at the same moment. */
    private val seedLock = Mutex()

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
        ) { expense, income -> MonthSummary(income = income / 100.0, expense = expense / 100.0) }
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

    suspend fun addTransaction(transaction: Transaction): Long = database.withTransaction {
        validateTransaction(transaction)
        dao.insert(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) = database.withTransaction {
        check(dao.findById(transaction.id) != null) { "记录已不存在" }
        validateTransaction(transaction)
        dao.update(transaction)
    }

    private suspend fun validateTransaction(transaction: Transaction) {
        require(transaction.amountCents > 0) { "金额必须大于零" }
        val category = dao.findCategory(transaction.categoryId)
        require(category != null && category.kind == transaction.kind) { "请选择有效分类" }
    }

    suspend fun deleteTransaction(transaction: Transaction) = dao.delete(transaction)

    suspend fun deleteTransactionById(id: Long) = dao.deleteById(id)

    suspend fun findCategory(id: Long): Category? = dao.findCategory(id)

    /** Sub-categories of a top-level category, in the user's own order. */
    suspend fun childrenOf(parentId: Long): List<Category> = dao.childrenOf(parentId)

    suspend fun addCategory(category: Category): Long = dao.insertCategory(category)

    suspend fun updateCategory(category: Category) = dao.updateCategory(category)

    suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)

    /**
     * Records in this category and in everything below it. A sub-category has no
     * children, so this reduces to its own count.
     */
    suspend fun usageOf(category: Category): CategoryUsage {
        val children = dao.childrenOf(category.id)
        return CategoryUsage(
            childCount = children.size,
            recordCount = dao.countTransactionsInCategories(
                buildList {
                    add(category.id)
                    children.forEach { add(it.id) }
                },
            ),
        )
    }

    /** Removes a top-level category together with its sub-categories, or neither. */
    suspend fun deleteCategoryTree(category: Category) = database.withTransaction {
        if (!usageOf(category).isDeletable) return@withTransaction false
        dao.deleteChildren(category.id)
        dao.deleteCategory(category)
        true
    }

    suspend fun hasAnyTransaction(): Boolean = dao.countTransactions() > 0

    /**
     * Seed once, atomically. Existing ledgers and deliberately emptied categories
     * must survive upgrades without having defaults inserted again.
     */
    suspend fun seedDefaultsIfNeeded() = seedLock.withLock {
        if (preferences.categoriesSeeded) return@withLock
        database.withTransaction {
            if (dao.countCategories() == 0 && !hasAnyTransaction()) DefaultCategories.seed(dao)
        }
        preferences.categoriesSeeded = true
    }

    /** Month bounds helper for screens that need the raw range. */
    fun monthBounds(month: YearMonth): Pair<Long, Long> = month.startMillis() to month.endMillis()

    /** Formats a stored month key back into a [YearMonth], falling back to now. */
    fun monthFromKey(key: String?): YearMonth = key?.let {
        runCatching { YearMonth.parse(it) }.getOrNull()
    } ?: Dates.currentMonth()
}
