package cn.yausen.notaledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yausen.notaledger.data.Category
import cn.yausen.notaledger.data.CategoryTotal
import cn.yausen.notaledger.data.DailyTotal
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.data.displayName
import cn.yausen.notaledger.data.indexedById
import cn.yausen.notaledger.ui.LedgerViewModel
import cn.yausen.notaledger.ui.components.BarChart
import cn.yausen.notaledger.ui.components.BarEntry
import cn.yausen.notaledger.ui.components.BudgetDialog
import cn.yausen.notaledger.ui.components.CardShape
import cn.yausen.notaledger.ui.components.ChartSlice
import cn.yausen.notaledger.ui.components.DonutChart
import cn.yausen.notaledger.ui.components.EmptyHint
import cn.yausen.notaledger.ui.components.LegendRow
import cn.yausen.notaledger.ui.components.MonthSelector
import cn.yausen.notaledger.ui.components.SectionHeader
import cn.yausen.notaledger.ui.theme.LedgerColors
import cn.yausen.notaledger.util.CategoryPalette
import cn.yausen.notaledger.util.Dates
import cn.yausen.notaledger.util.Money
import cn.yausen.notaledger.util.label
import cn.yausen.notaledger.util.toKey
import java.time.YearMonth

/** Spending analysis: where the money went, how it trends, and the budget. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: LedgerViewModel) {
    val month by viewModel.month.collectAsState()
    val kind by viewModel.kind.collectAsState()
    val home by viewModel.homeState.collectAsState()
    val totals by viewModel.categoryTotals.collectAsState()
    val daily by viewModel.dailyTotals.collectAsState()
    val trend by viewModel.monthlyTrend.collectAsState()
    val budget by viewModel.budget.collectAsState()

    // The whole category list, not just the ones in this month's rows: a
    // sub-category needs its parent's name even when the parent was never used.
    val categories = home.categories
    val byId = categories.indexedById()

    var showBudgetDialog by remember { mutableStateOf(false) }

    val isExpense = kind == Kind.EXPENSE
    val kindLabel = if (isExpense) "支出" else "收入"
    val accent = if (isExpense) LedgerColors.expense else LedgerColors.income
    val totalAmount = totals.sumOf { it.total }
    val slices = totals.map { item ->
        ChartSlice(
            label = categories.displayName(byId[item.categoryId]),
            value = item.total,
            color = CategoryPalette.colorAt(byId[item.categoryId]?.colorIndex ?: 0),
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                MonthSelector(
                    label = month.label(),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { viewModel.setKind(Kind.EXPENSE) },
                        label = { Text("支出") },
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { viewModel.setKind(Kind.INCOME) },
                        label = { Text("收入") },
                    )
                }
            }

            item {
                BreakdownCard(
                    totals = totals,
                    categories = categories,
                    byId = byId,
                    slices = slices,
                    totalAmount = totalAmount,
                    kindLabel = kindLabel,
                )
            }

            item {
                SectionHeader("近 6 个月$kindLabel 趋势")
                Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                    BarChart(
                        entries = trend.map { item ->
                            BarEntry(
                                label = shortMonthLabel(item.day),
                                value = item.total,
                                highlighted = item.day == month.toKey(),
                            )
                        },
                        showValues = true,
                        valueFormatter = { compactMoney(it) },
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            item {
                SectionHeader("本月每日$kindLabel")
                Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                    if (daily.isEmpty()) {
                        EmptyHint("本月还没有$kindLabel 记录", icon = Icons.Filled.CalendarToday)
                    } else {
                        DailyChart(daily = daily, month = month, accent = accent)
                    }
                }
            }

            item {
                SectionHeader("预算对比")
                BudgetComparisonCard(
                    spent = home.summary.expense,
                    budgetAmount = budget.amount,
                    onEdit = { showBudgetDialog = true },
                )
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
}

@Composable
private fun BreakdownCard(
    totals: List<CategoryTotal>,
    categories: List<Category>,
    byId: Map<Long, Category>,
    slices: List<ChartSlice>,
    totalAmount: Double,
    kindLabel: String,
) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${kindLabel}合计 ${Money.formatWithSymbol(totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            if (totals.isEmpty()) {
                EmptyHint("本月还没有$kindLabel 记录", icon = Icons.Filled.PieChart)
                return@Column
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = slices,
                    centerLabel = kindLabel,
                    centerValue = compactMoney(totalAmount),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    totals.take(5).forEach { item ->
                        val category = byId[item.categoryId]
                        LegendRow(
                            label = categories.displayName(category),
                            value = Money.format(item.total),
                            share = if (totalAmount > 0) (item.total / totalAmount).toFloat() else 0f,
                            color = CategoryPalette.colorAt(category?.colorIndex ?: 0),
                        )
                    }
                }
            }

            if (totals.size > 5) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "其余 ${totals.size - 5} 个分类合计 " +
                        Money.format(totals.drop(5).sumOf { it.total }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One column per day of the month; only days with records get a real bar. */
@Composable
private fun DailyChart(daily: List<DailyTotal>, month: YearMonth, accent: Color) {
    val byDay = daily.associate { it.day to it.total }
    val peak = daily.maxOfOrNull { it.total } ?: 0.0
    val entries = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        val value = byDay[date.toString()]
        BarEntry(
            // Label every fifth day so the axis stays readable on a phone.
            label = if (day == 1 || day % 5 == 0) day.toString() else "",
            value = value ?: 0.0,
            color = if (value != null) accent else null,
            highlighted = value != null && value == peak,
        )
    }
    BarChart(
        entries = entries,
        barHeight = 110,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

@Composable
private fun BudgetComparisonCard(spent: Double, budgetAmount: Double?, onEdit: () -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (budgetAmount == null) {
                Text(
                    text = "还没有设置月度预算，设置后这里会显示还能花多少。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onEdit) { Text("设置预算") }
                return@Column
            }

            val overspent = spent > budgetAmount
            val alertColor = if (overspent) LedgerColors.expense else MaterialTheme.colorScheme.primary

            Text(
                text = if (overspent) {
                    "本月超支 ${Money.formatWithSymbol(spent - budgetAmount)}"
                } else {
                    "本月还剩 ${Money.formatWithSymbol(budgetAmount - spent)} 可用"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = alertColor,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (spent / budgetAmount).coerceIn(0.0, 1.0).toFloat() },
                color = alertColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已花 ${Money.format(spent)} / 预算 ${Money.format(budgetAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "按天均摊，今天起每天可花 " +
                        Money.formatWithSymbol(
                            (budgetAmount - spent).coerceAtLeast(0.0) /
                                remainingDaysIn(Dates.currentMonth()).coerceAtLeast(1),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onEdit) { Text("修改预算") }
        }
    }
}

/** Days left in the month, counting today, so the daily allowance makes sense. */
private fun remainingDaysIn(month: YearMonth): Int {
    val today = Dates.today()
    return if (today.year == month.year && today.monthValue == month.monthValue) {
        month.lengthOfMonth() - today.dayOfMonth + 1
    } else {
        month.lengthOfMonth()
    }
}

private fun shortMonthLabel(key: String): String {
    val parts = key.split("-")
    return if (parts.size == 2) "${parts[1].toInt()}月" else key
}

/**
 * Compact money label for chart axes. Keeps the cents below 1000 so a small
 * amount still reads as the real figure instead of being rounded to "25".
 */
private fun compactMoney(value: Double): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000)
    value >= 1_000 -> "%.1f千".format(value / 1_000)
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> "%.2f".format(value)
}
