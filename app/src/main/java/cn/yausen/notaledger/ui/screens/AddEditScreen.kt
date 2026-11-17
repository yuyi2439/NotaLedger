package cn.yausen.notaledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.yausen.notaledger.data.Category
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.data.branchesOf
import cn.yausen.notaledger.ui.AddEditViewModel
import cn.yausen.notaledger.ui.components.AmountDisplay
import cn.yausen.notaledger.ui.components.AmountKeypad
import cn.yausen.notaledger.ui.components.CalcKey
import cn.yausen.notaledger.ui.components.CategoryIcons
import cn.yausen.notaledger.ui.components.DateTimeChip
import cn.yausen.notaledger.ui.components.FlowRow
import cn.yausen.notaledger.ui.components.SectionHeader
import cn.yausen.notaledger.ui.theme.LedgerColors
import cn.yausen.notaledger.util.CalculatorState
import cn.yausen.notaledger.util.CategoryPalette
import cn.yausen.notaledger.util.relativeDayName
import cn.yausen.notaledger.util.toLocalDate
import cn.yausen.notaledger.util.toLocalDateTime
import cn.yausen.notaledger.util.toUtcPickerMillis
import cn.yausen.notaledger.util.utcPickerMillisToLocalDateMillis
import java.time.format.DateTimeFormatter

/** `14:30` — the clock time shown in the chip beside the amount on the entry panel. */
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Records a new entry, or edits an existing one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditViewModel,
    onDone: () -> Unit,
) {
    val form by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val ready by viewModel.isReady.collectAsState()
    val saving by viewModel.isSaving.collectAsState()

    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // The keypad owns the entry; the amount it stands for is pushed to the form.
    var calc by remember { mutableStateOf(CalculatorState()) }
    // Editing has to seed the keypad, otherwise the screen would show 0 for a
    // record worth 5.00 and save that 0 back over it.
    var calcSeeded by remember { mutableStateOf(false) }

    val isExpense = form.kind == Kind.EXPENSE
    val accent = if (isExpense) LedgerColors.expense else LedgerColors.income

    // The two levels of the picker. A top-level chip selects that category and also
    // unfolds its sub-categories, so either level can be recorded. The unfolded one
    // follows the selection unless the user opens another branch by hand.
    var openParent by remember(form.kind) { mutableStateOf<Long?>(null) }
    val branches = categories.branchesOf(form.kind)
    val selectedCategory = categories.firstOrNull { it.id == form.categoryId }
    val openId = openParent
        ?: selectedCategory?.let { if (it.isTopLevel) it.id else it.parentId }

    LaunchedEffect(ready) {
        if (ready && !calcSeeded) {
            calc = CalculatorState(CalculatorState.format(form.amount))
            calcSeeded = true
        }
    }

    // The figure on screen is the amount that will be saved, so the form is kept
    // in step with every key press rather than only at the save key. It waits for
    // the record to be read back so an empty keypad cannot blank the form first.
    LaunchedEffect(calc, ready) {
        if (ready && calcSeeded) viewModel.setAmount(calc.value)
    }

    fun commit() {
        error = null
        viewModel.save(onSaved = onDone, onError = { error = it })
    }

    Scaffold(
        topBar = {
            // Deleting a record lives in the ledger's action sheet, so this screen
            // stays about recording one.
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onDone, enabled = !saving) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (viewModel.isCapturedDraft) {
                    Text("来自支付通知的待确认草稿，请核对金额、商户和时间，选择分类后手动保存。", style = MaterialTheme.typography.bodySmall)
                }
                KindSwitch(kind = form.kind, onSelect = viewModel::setKind)

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SectionHeader("分类")
                if (branches.isEmpty()) {
                    Text(
                        text = "还没有${if (isExpense) "支出" else "收入"}分类，先到「分类」页添加一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        branches.forEach { branch ->
                            // One wrap row per top-level category: the parent chip
                            // first, then its sub-categories once it is the open one.
                            FlowRow(modifier = Modifier.fillMaxWidth()) {
                                CategoryChip(
                                    category = branch.parent,
                                    label = branch.parent.name,
                                    selected = form.categoryId == branch.parent.id,
                                    onClick = {
                                        openParent = branch.parent.id
                                        viewModel.setCategory(branch.parent.id)
                                    },
                                )
                                if (branch.parent.id == openId) {
                                    branch.children.forEach { child ->
                                        CategoryChip(
                                            category = child,
                                            // A sub-category always reads as
                                            // 一级名称-二级名称, here included.
                                            label = "${branch.parent.name}-${child.name}",
                                            selected = form.categoryId == child.id,
                                            onClick = { viewModel.setCategory(child.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (branches.any { it.children.isNotEmpty() }) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "点一级分类就记在一级上；它下面的二级分类会展开，选了就记得更细。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionHeader("备注")
                OutlinedTextField(
                    value = form.note,
                    onValueChange = viewModel::setNote,
                    placeholder = { Text("可选，例如：和同事吃午饭") },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
            }

            // The amount line and the keys form one panel pinned to the bottom. It
            // carries no weight: it is exactly as tall as the keys need, so the form
            // above keeps every pixel the panel does not use and the keypad sits
            // right on top of the navigation bar instead of a share of the screen
            // above it. The cap is only the ceiling it may reach before it starts
            // scrolling, which is what stops the note keyboard from pushing the note
            // field off the screen.
            Column(modifier = Modifier.fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val panelMax = maxHeight * 0.62f
                    val rowPadding = 10.dp
                    val amountRow = 56.dp
                    val keypadPadding = 12.dp
                    val chrome = 1.dp + rowPadding + amountRow + keypadPadding * 2
                    val gaps = 8.dp * 3
                    val rowHeight = (((panelMax - chrome - gaps) / 4) - 1.dp)
                        .coerceIn(44.dp, 52.dp)

                    // The panel measures itself: one divider, the amount line and the
                    // four key rows, no more.
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = panelMax),
                    ) {
                        // Scrollable as a last resort: on a short window (small
                        // screen with the note keyboard open) the last key row
                        // stays reachable instead of being clipped away.
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            // The amount takes the room that is left; the chip hugs
                            // the right edge with the day and the clock time it will
                            // be saved with. This row owns the height and both boxes
                            // simply fill it, so they stay flush top and bottom.
                            Row(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 12.dp, top = rowPadding)
                                    .height(amountRow),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AmountDisplay(
                                    state = calc,
                                    accent = accent,
                                    // Trims the expression one character at a time,
                                    // exactly as it is displayed.
                                    onBackspace = { calc = calc.backspace() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                                Spacer(Modifier.width(8.dp))
                                DateTimeChip(
                                    dateText = form.dateMillis.toLocalDate().relativeDayName(),
                                    timeText = form.dateMillis.toLocalDateTime().format(timeFormatter),
                                    onPickDate = { showDatePicker = true },
                                    onPickTime = { showTimePicker = true },
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                            AmountKeypad(
                                enabled = ready && !saving,
                                keyHeight = rowHeight,
                                onKey = { key ->
                                    when (key) {
                                        is CalcKey.Digit -> calc = calc.digit(key.value)
                                        CalcKey.Decimal -> calc = calc.decimal()
                                        is CalcKey.Op -> calc = calc.operator(key.op)
                                        CalcKey.Save -> {
                                            // The expression is the state, so there
                                            // is nothing to settle first: 35 + 2.5
                                            // saves 37.5 straight away.
                                            viewModel.setAmount(amount = calc.value)
                                            commit()
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = keypadPadding),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.dateMillis.toLocalDate().toUtcPickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight, so convert back to the local
                    // day the ledger stores. Keep the previous day if it returns null.
                    state.selectedDateMillis?.let { utcMillis ->
                        viewModel.setDate(utcPickerMillisToLocalDateMillis(utcMillis))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        // The panel always reads `HH:mm`, so the picker is driven the same way
        // rather than following the device's 12-hour setting.
        val state = rememberTimePickerState(
            initialHour = form.dateMillis.toLocalDateTime().hour,
            initialMinute = form.dateMillis.toLocalDateTime().minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("记录时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSwitch(kind: Kind, onSelect: (Kind) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Kind.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = kind == item,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Kind.entries.size),
                label = { Text(if (item == Kind.EXPENSE) "支出" else "收入") },
            )
        }
    }
}

/** One selectable category, at either level, wearing its own icon and colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    category: Category,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = CategoryIcons.vectorAt(category.icon),
                contentDescription = null,
                tint = CategoryPalette.colorAt(category.colorIndex),
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
