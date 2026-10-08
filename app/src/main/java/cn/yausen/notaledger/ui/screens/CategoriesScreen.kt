package cn.yausen.notaledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yausen.notaledger.data.Category
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.ui.CategoryViewModel
import cn.yausen.notaledger.ui.components.CardShape
import cn.yausen.notaledger.ui.components.CategoryBadge
import cn.yausen.notaledger.ui.components.CategoryIcons
import cn.yausen.notaledger.ui.components.SectionHeader
import cn.yausen.notaledger.util.CategoryPalette

/** Manage the categories the user records against. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoryViewModel) {
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creatingKind by remember { mutableStateOf<Kind?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val expense = categories.filter { it.kind == Kind.EXPENSE.code }
    val income = categories.filter { it.kind == Kind.INCOME.code }

    Scaffold(
        topBar = { TopAppBar(title = { Text("分类") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingKind = Kind.EXPENSE }) {
                Icon(Icons.Filled.Add, contentDescription = "新建分类")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            item {
                Text(
                    text = "点分类可改名、换图标或换颜色；长按可删除自建分类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item { SectionHeader("支出分类 · ${expense.size} 个") }
            items(items = expense, key = { "e-${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { editing = category },
                    onLongClick = {
                        if (category.builtIn) {
                            message = "「${category.name}」是内置分类，不能删除"
                        } else {
                            viewModel.delete(category) { message = it }
                        }
                    },
                )
            }

            item { SectionHeader("收入分类 · ${income.size} 个") }
            items(items = income, key = { "i-${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { editing = category },
                    onLongClick = {
                        if (category.builtIn) {
                            message = "「${category.name}」是内置分类，不能删除"
                        } else {
                            viewModel.delete(category) { message = it }
                        }
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { creatingKind = Kind.INCOME }) {
                    Text("+ 新建收入分类")
                }
            }
        }
    }

    creatingKind?.let { kind ->
        CategoryEditorDialog(
            title = if (kind == Kind.EXPENSE) "新建支出分类" else "新建收入分类",
            initial = null,
            kind = kind,
            onDismiss = { creatingKind = null },
            onConfirm = { name, icon, colorIndex ->
                viewModel.add(name, kind, icon, colorIndex) { message = it }
                creatingKind = null
            },
        )
    }

    editing?.let { category ->
        CategoryEditorDialog(
            title = "编辑分类",
            initial = category,
            kind = Kind.fromCode(category.kind),
            onDismiss = { editing = null },
            onConfirm = { name, icon, colorIndex ->
                viewModel.rename(category, name, icon, colorIndex)
                editing = null
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("知道了") }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(icon = category.icon, colorIndex = category.colorIndex)
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (category.builtIn) {
                Text(
                    text = "内置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorDialog(
    title: String,
    initial: Category?,
    kind: Kind,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: Int, colorIndex: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: CategoryIcons.defaultFor(kind.code)) }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("图标", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = CategoryIcons.allIds, key = { it }) { candidate ->
                        val selected = candidate == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { icon = candidate },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategoryIcons.vectorAt(candidate),
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("颜色", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 1, 2, 3, 4, 5, 6, 7).forEach { index ->
                        ColorDot(
                            index = index,
                            selected = index == colorIndex,
                            onClick = { colorIndex = index },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(8, 9, 10, 11, 12, 13, 14, 15).forEach { index ->
                        ColorDot(
                            index = index,
                            selected = index == colorIndex,
                            onClick = { colorIndex = index },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, icon, colorIndex) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ColorDot(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(CategoryPalette.colorAt(index))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
        )
    }
}
