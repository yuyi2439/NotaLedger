package cn.yausen.notaledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yausen.notaledger.data.Transaction
import cn.yausen.notaledger.ui.LedgerRow
import cn.yausen.notaledger.ui.LedgerViewModel
import cn.yausen.notaledger.ui.components.AmountColumn
import cn.yausen.notaledger.ui.components.BudgetDialog
import cn.yausen.notaledger.ui.components.CardShape
import cn.yausen.notaledger.ui.components.CategoryBadge
import cn.yausen.notaledger.ui.components.CategoryIcons
import cn.yausen.notaledger.ui.components.EmptyHint
import cn.yausen.notaledger.ui.components.MonthSelector
import cn.yausen.notaledger.ui.theme.LedgerColors
import cn.yausen.notaledger.util.Dates
import cn.yausen.notaledger.util.Money
import cn.yausen.notaledger.util.dateTimeLabel
import cn.yausen.notaledger.util.label
import cn.yausen.notaledger.util.relativeDayWithWeekday
import cn.yausen.notaledger.util.timeLabel
import cn.yausen.notaledger.util.toLocalDate
import java.time.LocalDate

/**
 * The ledger: month summary, budget progress and every record of the month
 * grouped by day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LedgerViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
) {
    val state by viewModel.homeState.collectAsState()
    val budget by viewModel.budget.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    // Tapping a record opens the action sheet rather than jumping into the
    // editor, so editing and deleting are deliberate choices.
    var actionTarget by remember { mutableStateOf<LedgerRow?>(null) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val groups: List<Pair<LocalDate, List<LedgerRow>>> = remember(state.rows) {
        state.rows
            .groupBy { it.transaction.timestamp.toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("账本") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MonthSelector(
                    label = state.month.label(),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            item {
                SummaryCard(
                    income = state.summary.income,
                    expense = state.summary.expense,
                    balance = state.summary.balance,
                )
            }

            item {
                BudgetCard(
                    budgetAmount = budget.amount,
                    spent = state.summary.expense,
                    warningEnabled = budget.warningEnabled,
                    onEdit = { showBudgetDialog = true },
                )
            }

            if (state.isLoaded && groups.isEmpty()) {
                item {
                    EmptyHint(
                        text = "${state.month.label()}还没有记录\n点右下角 + 记第一笔",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    )
                }
            }

            groups.forEach { (day, rows) ->
                item(key = "header-${day}") {
                    DayHeader(
                        day = day,
                        expense = rows.filter { it.kind.code == 0 }.sumOf { it.transaction.amount },
                        income = rows.filter { it.kind.code == 1 }.sumOf { it.transaction.amount },
                    )
                }
                items(items = rows, key = { it.transaction.id }) { row ->
                    TransactionRow(
                        row = row,
                        onClick = { actionTarget = row },
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            current = budget.amount,
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
            },
        )
    }

    actionTarget?.let { target ->
        TransactionActionSheet(
            row = target,
            onDismiss = { actionTarget = null },
            onEdit = {
                actionTarget = null
                onEditTransaction(target.transaction.id)
            },
            onDelete = {
                actionTarget = null
                pendingDelete = target.transaction
            },
        )
    }

    pendingDelete?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = {
                Text(
                    "${target.note.ifBlank { "无备注" }} · " +
                        Money.formatWithSymbol(target.amount) + "\n删除后无法恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(target.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SummaryCard(income: Double, expense: Double, balance: Double) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "本月结余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = Money.formatWithSymbol(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AmountColumn(
                    label = "收入",
                    amount = income,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)),
                )
                AmountColumn(
                    label = "支出",
                    amount = expense,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(
    budgetAmount: Double?,
    spent: Double,
    warningEnabled: Boolean,
    onEdit: () -> Unit,
) {
    val progress = if (budgetAmount == null || budgetAmount <= 0.0) 0f
    else (spent / budgetAmount).coerceIn(0.0, 1.0).toFloat()
    val overspent = budgetAmount != null && spent > budgetAmount
    val alertColor = if (overspent) LedgerColors.expense else MaterialTheme.colorScheme.primary

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本月预算",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Text(if (budgetAmount == null) "设置" else "修改")
                }
            }

            if (budgetAmount == null) {
                Text(
                    text = "还没有设置预算，点右侧「设置」给自己一个上限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LinearProgressIndicator(
                progress = { progress },
                color = alertColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "已花 ${Money.format(spent)} / ${Money.format(budgetAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (overspent) "超支 ${Money.format(spent - budgetAmount)}"
                    else "剩余 ${Money.format(budgetAmount - spent)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = alertColor,
                )
            }
            if (overspent && warningEnabled) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LedgerColors.expense.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = LedgerColors.expense,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "本月已超出预算 " +
                                Money.formatWithSymbol(spent - budgetAmount) + "，注意节奏。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.expense,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, expense: Double, income: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.relativeDayWithWeekday(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val parts = buildList {
            if (expense > 0) add("支出 ${Money.format(expense)}")
            if (income > 0) add("收入 ${Money.format(income)}")
        }
        Text(
            text = parts.joinToString("  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    row: LedgerRow,
    onClick: () -> Unit,
) {
    val isExpense = row.kind.code == 0
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                icon = row.category?.icon ?: CategoryIcons.CONTENT_CUT,
                colorIndex = row.category?.colorIndex ?: 0,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.categoryLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                // The row only needs the clock time: its day is the group header
                // directly above it.
                val subtitle = buildList {
                    add(row.transaction.timestamp.timeLabel())
                    if (row.transaction.note.isNotBlank()) add(row.transaction.note)
                }.joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Money.formatSigned(row.transaction.amount, isExpense),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isExpense) LedgerColors.expense else LedgerColors.income,
            )
        }
    }
}

/**
 * The secondary menu behind a tap on a record: what was recorded, then the two
 * things that can be done with it. Deleting stays a confirmed action, while
 * editing is one deliberate tap rather than the old straight-to-editor jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionActionSheet(
    row: LedgerRow,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isExpense = row.kind.code == 0
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(
                    icon = row.category?.icon ?: CategoryIcons.CONTENT_CUT,
                    colorIndex = row.category?.colorIndex ?: 0,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.categoryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = row.transaction.timestamp.dateTimeLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = Money.formatSigned(row.transaction.amount, isExpense),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpense) LedgerColors.expense else LedgerColors.income,
                )
            }

            if (row.transaction.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = row.transaction.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            ActionButton(
                icon = Icons.Filled.Edit,
                label = "编辑",
                onClick = onEdit,
            )
            // A clear gap: the two actions were reading as one block.
            Spacer(Modifier.height(10.dp))
            ActionButton(
                icon = Icons.Filled.Delete,
                label = "删除",
                tint = LedgerColors.expense,
                onClick = onDelete,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            // Tall enough to stand on its own as a row, not half of one block.
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}
