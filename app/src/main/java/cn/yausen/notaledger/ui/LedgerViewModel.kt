package cn.yausen.notaledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.yausen.notaledger.NotaLedgerApp
import cn.yausen.notaledger.data.Category
import cn.yausen.notaledger.data.CategoryTotal
import cn.yausen.notaledger.data.DailyTotal
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.data.LedgerRepository
import cn.yausen.notaledger.data.MonthSummary
import cn.yausen.notaledger.data.Transaction
import cn.yausen.notaledger.data.UserPreferences
import cn.yausen.notaledger.util.Dates
import cn.yausen.notaledger.util.withDayOf
import cn.yausen.notaledger.util.withTimeOf
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One transaction row joined with its category, ready to render. */
data class LedgerRow(
    val transaction: Transaction,
    val category: Category?,
) {
    val kind: Kind get() = Kind.fromCode(transaction.kind)
}

/** Everything the home screen shows for the selected month. */
data class HomeUiState(
    val month: YearMonth = Dates.currentMonth(),
    val summary: MonthSummary = MonthSummary(),
    val rows: List<LedgerRow> = emptyList(),
    val isLoaded: Boolean = false,
)

data class BudgetState(
    val amount: Double? = null,
    val warningEnabled: Boolean = true,
) {
    /** Fraction of the budget already spent, clamped so the bar never overflows. */
    fun progress(spent: Double): Float {
        val budget = amount ?: return 0f
        if (budget <= 0.0) return 0f
        return (spent / budget).coerceIn(0.0, 1.0).toFloat()
    }

    fun remaining(spent: Double): Double = (amount ?: 0.0) - spent

    fun isOverspent(spent: Double): Boolean = amount != null && spent > amount
}

/**
 * Ledger state shared by the home and statistics screens. The selected month
 * lives here so every screen agrees on it and it survives rotation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: LedgerRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(
        preferences.lastViewedMonth ?: Dates.currentMonth(),
    )
    private val budgetAmount = MutableStateFlow(preferences.monthlyBudget)
    private val budgetWarning = MutableStateFlow(preferences.budgetWarningEnabled)

    val month: StateFlow<YearMonth> = selectedMonth

    val budget: StateFlow<BudgetState> =
        combine(budgetAmount, budgetWarning) { amount, warning ->
            BudgetState(amount = amount, warningEnabled = warning)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BudgetState(preferences.monthlyBudget))

    /**
     * Transactions and totals must be re-subscribed whenever the month changes,
     * otherwise the list would keep showing the previous month after a switch.
     */
    val homeState: StateFlow<HomeUiState> = selectedMonth.flatMapLatest { target ->
        combine(
            repository.observeTransactions(target),
            repository.observeSummary(target),
            repository.observeCategories(),
        ) { transactions, summary, categories ->
            val byId = categories.associateBy { it.id }
            HomeUiState(
                month = target,
                summary = summary,
                rows = transactions.map { LedgerRow(it, byId[it.categoryId]) },
                isLoaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(month = selectedMonth.value),
    )

    fun selectMonth(target: YearMonth) {
        selectedMonth.value = target
        preferences.lastViewedMonth = target
    }

    fun previousMonth() = selectMonth(selectedMonth.value.minusMonths(1))

    fun nextMonth() = selectMonth(selectedMonth.value.plusMonths(1))

    fun setMonthlyBudget(amount: Double?) {
        budgetAmount.value = amount
        preferences.monthlyBudget = amount
    }

    fun setBudgetWarningEnabled(enabled: Boolean) {
        budgetWarning.value = enabled
        preferences.budgetWarningEnabled = enabled
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransactionById(id) }
    }

    // ---- Statistics screen -------------------------------------------------

    private val statsKind = MutableStateFlow(Kind.EXPENSE)

    val kind: StateFlow<Kind> = statsKind

    fun setKind(kind: Kind) {
        statsKind.value = kind
    }

    fun toggleKind() {
        statsKind.value = if (statsKind.value == Kind.EXPENSE) Kind.INCOME else Kind.EXPENSE
    }

    /** Category breakdown for the ring chart, re-subscribed on month or kind change. */
    val categoryTotals: StateFlow<List<CategoryTotal>> =
        combine(selectedMonth, statsKind) { month, kind -> month to kind }
            .flatMapLatest { (month, kind) -> repository.observeCategoryTotals(month, kind) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-day totals within the selected month. */
    val dailyTotals: StateFlow<List<DailyTotal>> =
        combine(selectedMonth, statsKind) { month, kind -> month to kind }
            .flatMapLatest { (month, kind) -> repository.observeDailyTotals(month, kind) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last six months, so the trend always has context around the current month. */
    val monthlyTrend: StateFlow<List<DailyTotal>> =
        combine(selectedMonth, statsKind) { month, kind -> month to kind }
            .flatMapLatest { (month, kind) ->
                repository.observeMonthlyTrend(kind, month.minusMonths(5), month)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Raw picks behind the add/edit screen, plus the amount the keypad produced.
 *
 * [dateMillis] is a whole timestamp rather than a bare day: a new record starts at
 * the moment the screen was opened, so the panel shows the clock time the entry is
 * being made at instead of `00:00`, and that same time is what gets stored. An
 * edit starts at the record's own timestamp and keeps it.
 */
data class TransactionForm(
    val amount: Double = 0.0,
    val kind: Kind = Kind.EXPENSE,
    val categoryId: Long? = null,
    val note: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
)

/** Add / edit form state and actions. */
class AddEditViewModel(
    private val repository: LedgerRepository,
    private val editingId: Long?,
) : ViewModel() {

    private val form = MutableStateFlow(TransactionForm())
    private val categoriesState = MutableStateFlow<List<Category>>(emptyList())
    private val readyState = MutableStateFlow(editingId == null)

    val formState: StateFlow<TransactionForm> = form
    val categories: StateFlow<List<Category>> = categoriesState

    /** False until an existing record has been read back into the form. */
    val isReady: StateFlow<Boolean> = readyState

    val isEditing: Boolean get() = editingId != null

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfNeeded()
            categoriesState.value = repository.currentCategories()
            if (editingId != null) {
                repository.findTransaction(editingId)?.let { existing ->
                    form.value = TransactionForm(
                        amount = existing.amount,
                        kind = Kind.fromCode(existing.kind),
                        categoryId = existing.categoryId,
                        note = existing.note,
                        dateMillis = existing.timestamp,
                    )
                }
                readyState.value = true
            }
        }
    }

    fun setAmount(amount: Double) {
        form.value = form.value.copy(amount = amount)
    }

    /** Switching expense/income clears the pick, because categories are per kind. */
    fun setKind(kind: Kind) {
        val current = form.value
        if (current.kind != kind) form.value = current.copy(kind = kind, categoryId = null)
    }

    fun setCategory(id: Long) {
        form.value = form.value.copy(categoryId = id)
    }

    fun setNote(text: String) {
        form.value = form.value.copy(note = text)
    }

    /** Moves the record to the picked day, keeping the clock time it already has. */
    fun setDate(millis: Long) {
        val current = form.value
        form.value = current.copy(dateMillis = current.dateMillis.withDayOf(millis))
    }

    /** Moves the record to the picked clock time, keeping the day it is on. */
    fun setTime(hour: Int, minute: Int) {
        val current = form.value
        form.value = current.copy(dateMillis = current.dateMillis.withTimeOf(hour, minute))
    }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit) {
        val current = form.value
        when {
            current.amount <= 0.0 -> onError("请输入有效的金额")
            current.categoryId == null -> onError("请选择一个分类")
            else -> viewModelScope.launch {
                val record = Transaction(
                    id = editingId ?: 0L,
                    amount = current.amount,
                    kind = current.kind.code,
                    categoryId = current.categoryId,
                    note = current.note.trim(),
                    timestamp = current.dateMillis,
                )
                if (editingId == null) repository.addTransaction(record)
                else repository.updateTransaction(record)
                onSaved()
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            repository.deleteTransactionById(id)
            onDeleted()
        }
    }
}

/** Category management state. */
class CategoryViewModel(private val repository: LedgerRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, kind: Kind, icon: Int, colorIndex: Int, onError: (String) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onError("请输入分类名称")
            return
        }
        viewModelScope.launch {
            val existing = repository.currentCategories()
            if (existing.any { it.kind == kind.code && it.name == trimmed }) {
                onError("已有同名分类")
                return@launch
            }
            repository.addCategory(
                Category(
                    name = trimmed,
                    kind = kind.code,
                    icon = icon,
                    colorIndex = colorIndex,
                    builtIn = false,
                    sortOrder = existing.count { it.kind == kind.code },
                ),
            )
        }
    }

    fun rename(category: Category, name: String, icon: Int, colorIndex: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = trimmed, icon = icon, colorIndex = colorIndex))
        }
    }

    fun delete(category: Category, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            val used = repository.usageCount(category.id)
            if (used > 0) {
                onBlocked("该分类下还有 $used 笔记录，先移除记录再删除分类")
            } else {
                repository.deleteCategory(category)
            }
        }
    }
}

class LedgerViewModelFactory(
    private val repository: LedgerRepository,
    private val preferences: UserPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LedgerViewModel(repository, preferences) as T
}

class AddEditViewModelFactory(
    private val repository: LedgerRepository,
    private val editingId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditViewModel(repository, editingId) as T
}

class CategoryViewModelFactory(private val repository: LedgerRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CategoryViewModel(repository) as T
}
