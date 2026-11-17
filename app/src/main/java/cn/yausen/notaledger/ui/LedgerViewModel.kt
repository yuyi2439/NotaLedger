package cn.yausen.notaledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.yausen.notaledger.NotaLedgerApp
import cn.yausen.notaledger.data.Category
import cn.yausen.notaledger.data.CategoryTotal
import cn.yausen.notaledger.data.CategoryUsage
import cn.yausen.notaledger.data.DailyTotal
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.data.LedgerRepository
import cn.yausen.notaledger.data.MonthSummary
import cn.yausen.notaledger.data.Money
import kotlinx.coroutines.CancellationException
import cn.yausen.notaledger.data.Transaction
import cn.yausen.notaledger.data.UserPreferences
import cn.yausen.notaledger.data.indexedById
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
    /** The parent of a sub-category; null for a top-level category and for 未分类. */
    val parent: Category? = null,
) {
    val kind: Kind get() = Kind.fromCode(transaction.kind)

    /** A top-level category reads as its own name, a sub-category as 一级-二级. */
    val categoryLabel: String
        get() = when {
            category == null -> "未分类"
            parent == null -> category.name
            else -> "${parent.name}-${category.name}"
        }
}

/** Everything the home screen shows for the selected month. */
data class HomeUiState(
    val month: YearMonth = Dates.currentMonth(),
    val summary: MonthSummary = MonthSummary(),
    val rows: List<LedgerRow> = emptyList(),
    /** Every category, so the statistics screen can name a sub-category's parent. */
    val categories: List<Category> = emptyList(),
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
            val byId = categories.indexedById()
            HomeUiState(
                month = target,
                summary = summary,
                rows = transactions.map { transaction ->
                    val category = byId[transaction.categoryId]
                    LedgerRow(
                        transaction = transaction,
                        category = category,
                        // Resolved once here so every row can name its own level
                        // without the screens having to look the parent up again.
                        parent = category?.takeIf { !it.isTopLevel }
                            ?.let { byId[it.parentId] },
                    )
                },
                categories = categories,
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
    private val draft: cn.yausen.notaledger.capture.PaymentDraft? = null,
) : ViewModel() {

    private val form = MutableStateFlow(draft?.let { TransactionForm(amount = it.amount, note = it.merchant, dateMillis = it.timestamp) } ?: TransactionForm())
    private val categoriesState = MutableStateFlow<List<Category>>(emptyList())
    private val readyState = MutableStateFlow(editingId == null)
    private val savingState = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = savingState

    val formState: StateFlow<TransactionForm> = form
    val categories: StateFlow<List<Category>> = categoriesState

    /** False until an existing record has been read back into the form. */
    val isReady: StateFlow<Boolean> = readyState

    val isEditing: Boolean get() = editingId != null
    val isCapturedDraft: Boolean get() = editingId == null && draft != null

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
        if (!readyState.value || savingState.value) return
        val current = form.value
        val cents = runCatching { Money.toCents(current.amount) }.getOrNull()
        when {
            cents == null || cents <= 0L -> onError("请输入有效的金额，金额不能超出支持范围")
            current.categoryId == null -> onError("请选择一个分类")
            else -> {
                if (!savingState.compareAndSet(false, true)) return
                viewModelScope.launch {
                    try {
                        val record = Transaction(
                            id = editingId ?: 0L,
                            amountCents = cents,
                            kind = current.kind.code,
                            categoryId = current.categoryId,
                            note = current.note.trim(),
                            timestamp = current.dateMillis,
                        )
                        if (editingId == null) repository.addTransaction(record)
                        else repository.updateTransaction(record)
                    } catch (cancelled: CancellationException) {
                        savingState.value = false
                        throw cancelled
                    } catch (_: Exception) {
                        savingState.value = false
                        onError("保存失败，内容已保留，请重试并检查分类是否仍然存在")
                        return@launch
                    }
                    // Keep submissions locked after success until navigation removes this form.
                    onSaved()
                }
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

/**
 * One filled-in category editor form. The icon and the name travel together
 * because a built-in preset describes a category as exactly that pair.
 */
data class CategoryDraft(
    val name: String,
    val kind: Kind,
    val icon: Int,
    val colorIndex: Int,
    /** [Category.ROOT] keeps it top-level; any other id nests it one level down. */
    val parentId: Long = Category.ROOT,
)

/** Category management state. */
class CategoryViewModel(private val repository: LedgerRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.seedDefaultsIfNeeded() }
    }

    /** Creates a category. On failure [onError] leaves the editor open to correct it. */
    fun add(draft: CategoryDraft, onError: (String) -> Unit, onDone: () -> Unit) {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            onError("请输入分类名称")
            return
        }
        viewModelScope.launch {
            val all = repository.currentCategories()
            val problem = problemWith(draft.copy(name = name), all, moving = null)
            if (problem != null) {
                onError(problem)
                return@launch
            }
            repository.addCategory(
                Category(
                    name = name,
                    kind = draft.kind.code,
                    icon = draft.icon,
                    colorIndex = draft.colorIndex,
                    parentId = draft.parentId,
                    isDefault = false,
                    // Lands at the end of its level, next to its own siblings.
                    sortOrder = all.count {
                        it.kind == draft.kind.code && it.parentId == draft.parentId
                    },
                ),
            )
            onDone()
        }
    }

    /** Saves an edit, including a move between the two levels. */
    fun update(
        category: Category,
        draft: CategoryDraft,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) {
        // The kind is not editable: the records already filed under this category
        // were signed by it, so changing it would leave them on the wrong side.
        val wanted = draft.copy(kind = Kind.fromCode(category.kind))
        val name = wanted.name.trim()
        if (name.isEmpty()) {
            onError("请输入分类名称")
            return
        }
        viewModelScope.launch {
            val all = repository.currentCategories()
            val problem = problemWith(wanted.copy(name = name), all, moving = category)
            if (problem != null) {
                onError(problem)
                return@launch
            }
            val moved = category.parentId != wanted.parentId
            repository.updateCategory(
                category.copy(
                    name = name,
                    icon = wanted.icon,
                    colorIndex = wanted.colorIndex,
                    parentId = wanted.parentId,
                    sortOrder = if (moved) {
                        all.count { it.kind == category.kind && it.parentId == wanted.parentId }
                    } else {
                        category.sortOrder
                    },
                ),
            )
            onDone()
        }
    }

    /**
     * The rules a category list has to keep: a sub-category needs a top-level parent
     * of its own kind, a category that has children cannot itself become one, and
     * names stay unique among siblings. Returns the reason, or null when it is fine.
     */
    private fun problemWith(draft: CategoryDraft, all: List<Category>, moving: Category?): String? {
        if (draft.parentId != Category.ROOT) {
            val parent = all.firstOrNull { it.id == draft.parentId }
            if (parent == null || !parent.isTopLevel) {
                return "父分类不存在，二级分类只能挂在某个一级分类下"
            }
            if (parent.id == moving?.id) return "分类不能挂在自己下面"
            if (parent.kind != draft.kind.code) return "二级分类要和父分类同属支出或收入"
            if (moving != null) {
                val children = all.count { it.parentId == moving.id }
                if (children > 0) {
                    return "「${moving.name}」下面还有 $children 个二级分类，" +
                        "先处理它们再把它变成二级分类"
                }
            }
        }
        val clash = all.any {
            it.id != moving?.id && it.kind == draft.kind.code &&
                it.parentId == draft.parentId && it.name == draft.name
        }
        return if (clash) "同级下已有同名分类" else null
    }

    /** Reads a category's subtree before the screen asks to confirm a delete. */
    fun inspect(category: Category, onResult: (CategoryUsage) -> Unit) {
        viewModelScope.launch { onResult(repository.usageOf(category)) }
    }

    /**
     * Removes a category. Records anywhere in its subtree block the delete, because
     * those rows would otherwise point at a category that no longer exists.
     */
    fun delete(category: Category, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            val usage = repository.usageOf(category)
            if (!usage.isDeletable) onBlocked(blockedMessage(category, usage))
            else if (!repository.deleteCategoryTree(category)) {
                onBlocked("该分类下新增了记录，请先迁移或处理记录后再删除分类")
            }
        }
    }

    /** Why a category cannot go, phrased for the level it sits at. */
    fun blockedMessage(category: Category, usage: CategoryUsage): String =
        if (usage.childCount > 0) {
            "「${category.name}」和它的 ${usage.childCount} 个二级分类下还有 " +
                "${usage.recordCount} 笔记录，先移除记录再删除分类"
        } else {
            "该分类下还有 ${usage.recordCount} 笔记录，先移除记录再删除分类"
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
    private val draft: cn.yausen.notaledger.capture.PaymentDraft? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditViewModel(repository, editingId, draft) as T
}

class CategoryViewModelFactory(private val repository: LedgerRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CategoryViewModel(repository) as T
}
