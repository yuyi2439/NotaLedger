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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.IconButton
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
import cn.yausen.notaledger.data.CategoryBranch
import cn.yausen.notaledger.data.CategoryUsage
import cn.yausen.notaledger.data.Kind
import cn.yausen.notaledger.data.branchesOf
import cn.yausen.notaledger.ui.CategoryDraft
import cn.yausen.notaledger.ui.CategoryViewModel
import cn.yausen.notaledger.ui.components.CardShape
import cn.yausen.notaledger.ui.components.CategoryBadge
import cn.yausen.notaledger.ui.components.CategoryIcons
import cn.yausen.notaledger.ui.components.FlowRow
import cn.yausen.notaledger.ui.components.SectionHeader
import cn.yausen.notaledger.util.CategoryPalette

/**
 * Manage the categories the user records against: two levels of them, where every
 * row — seeded or hand-made — can be renamed, restyled, moved between the levels
 * and deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoryViewModel) {
    val categories by viewModel.categories.collectAsState()
    var editor by remember { mutableStateOf<CategoryEditor?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var pendingUsage by remember { mutableStateOf<CategoryUsage?>(null) }

    val expense = categories.branchesOf(Kind.EXPENSE)
    val income = categories.branchesOf(Kind.INCOME)

    // Deleting a default category is allowed now, so the long press asks first and
    // the confirm dialog carries the one fact that matters: what is in the subtree.
    val requestDelete: (Category) -> Unit = { category ->
        viewModel.inspect(category) { usage ->
            pendingUsage = usage
            pendingDelete = category
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("分类") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = CategoryEditor.New(Kind.EXPENSE) }) {
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
                    text = "点分类可改名、换图标、换颜色、调整层级；长按可删除。" +
                        "一级分类右侧的 + 用来加二级分类，默认分类和自建分类一样能改能删。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            categorySection(
                title = sectionTitle("支出分类", expense),
                branches = expense,
                keyPrefix = "e",
                onEdit = { editor = CategoryEditor.Edit(it) },
                onDelete = requestDelete,
                onAddChild = { editor = CategoryEditor.New(Kind.EXPENSE, it.id) },
            )

            categorySection(
                title = sectionTitle("收入分类", income),
                branches = income,
                keyPrefix = "i",
                onEdit = { editor = CategoryEditor.Edit(it) },
                onDelete = requestDelete,
                onAddChild = { editor = CategoryEditor.New(Kind.INCOME, it.id) },
            )

            item {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { editor = CategoryEditor.New(Kind.INCOME) }) {
                    Text("+ 新建收入分类")
                }
            }
        }
    }

    editor?.let { target ->
        CategoryDialog(
            target = target,
            categories = categories,
            onDismiss = { editor = null },
            onSave = { draft, onError ->
                when (target) {
                    is CategoryEditor.New -> viewModel.add(draft, onError) { editor = null }
                    is CategoryEditor.Edit ->
                        viewModel.update(target.category, draft, onError) { editor = null }
                }
            },
        )
    }

    pendingDelete?.let { target ->
        val usage = pendingUsage
        AlertDialog(
            onDismissRequest = {
                pendingDelete = null
                pendingUsage = null
            },
            title = { Text("删除分类「${target.name}」？") },
            text = {
                val lines = buildList {
                    if (usage != null && usage.childCount > 0) {
                        add("它的 ${usage.childCount} 个二级分类会一起删除。")
                    }
                    add(
                        if (usage != null && !usage.isDeletable) {
                            viewModel.blockedMessage(target, usage)
                        } else {
                            "删除后无法恢复。"
                        },
                    )
                }
                Text(lines.joinToString("\n"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target) { message = it }
                        pendingDelete = null
                        pendingUsage = null
                    },
                    enabled = usage?.isDeletable == true,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    pendingUsage = null
                }) { Text("取消") }
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

/** One level of the list: a heading and every top-level category with its children. */
private fun LazyListScope.categorySection(
    title: String,
    branches: List<CategoryBranch>,
    keyPrefix: String,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onAddChild: (Category) -> Unit,
) {
    item(key = "$keyPrefix-header") { SectionHeader(title) }
    items(items = branches, key = { "$keyPrefix-${it.parent.id}" }) { branch ->
        Column {
            CategoryRow(
                category = branch.parent,
                childCount = branch.children.size,
                onAddChild = { onAddChild(branch.parent) },
                onClick = { onEdit(branch.parent) },
                onLongClick = { onDelete(branch.parent) },
            )
            // Children sit under their parent, one indent in and with a smaller
            // badge, so the level is visible without writing it out.
            branch.children.forEach { child ->
                CategoryRow(
                    category = child,
                    childCount = 0,
                    onAddChild = null,
                    indented = true,
                    onClick = { onEdit(child) },
                    onLongClick = { onDelete(child) },
                )
            }
        }
    }
}

/** `支出分类 · 12 个（二级 4 个）` — both levels counted, the split called out. */
private fun sectionTitle(label: String, branches: List<CategoryBranch>): String {
    val children = branches.sumOf { it.children.size }
    val total = branches.sumOf { it.children.size + 1 }
    return if (children > 0) "$label · $total 个（二级 $children 个）" else "$label · $total 个"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: Category,
    childCount: Int,
    onAddChild: (() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    indented: Boolean = false,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 24.dp else 0.dp, top = 3.dp, bottom = 3.dp)
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                icon = category.icon,
                colorIndex = category.colorIndex,
                size = if (indented) 32 else 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (indented) FontWeight.Normal else FontWeight.Medium,
                )
                val notes = buildList {
                    if (category.isDefault) add("默认")
                    if (childCount > 0) add("$childCount 个二级分类")
                }
                if (notes.isNotEmpty()) {
                    Text(
                        text = notes.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onAddChild != null) {
                IconButton(onClick = onAddChild) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加二级分类",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** What the editor was opened for: a new category, or one that already exists. */
private sealed interface CategoryEditor {
    val title: String
    val kind: Kind

    /** The row being changed, or null when this is a brand new category. */
    val initial: Category?
    val initialParentId: Long

    data class New(val newKind: Kind, val parent: Long = Category.ROOT) : CategoryEditor {
        override val title: String get() = "新建分类"
        override val kind: Kind get() = newKind
        override val initial: Category? get() = null
        override val initialParentId: Long get() = parent
    }

    data class Edit(val category: Category) : CategoryEditor {
        override val title: String get() = "编辑分类"
        override val kind: Kind get() = Kind.fromCode(category.kind)
        override val initial: Category get() = category
        override val initialParentId: Long get() = category.parentId
    }
}

/**
 * The one editor behind every row. The icon grid is the built-in set of
 * icon-and-name presets, so tapping one is enough to describe a category; a name the
 * user typed is never overwritten by it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    target: CategoryEditor,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (CategoryDraft, onError: (String) -> Unit) -> Unit,
) {
    val initial = target.initial
    var kind by remember { mutableStateOf(target.kind) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember {
        mutableStateOf(initial?.icon ?: CategoryIcons.defaultFor(target.kind.code))
    }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var parentId by remember { mutableStateOf(target.initialParentId) }
    // Only a name the user has not written themselves may be filled from a preset.
    var nameTouched by remember { mutableStateOf(initial != null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Candidates for a parent: top-level rows of the same kind, never the row being
    // edited. A category that has children cannot move down a level.
    val candidates = categories.filter {
        it.isTopLevel && it.kind == kind.code && it.id != initial?.id
    }
    val hasChildren = initial != null && categories.any { it.parentId == initial.id }
    val canNest = candidates.isNotEmpty() && !hasChildren

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // The kind is fixed once a category exists: records already filed
                // under it were signed by the kind it had.
                if (initial == null) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Kind.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = kind == entry,
                                onClick = {
                                    kind = entry
                                    // A parent from the other kind would be invalid.
                                    parentId = Category.ROOT
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, Kind.entries.size),
                                label = { Text(if (entry == Kind.EXPENSE) "支出" else "收入") },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // The category itself comes first: its preset, name and colour are
                // what the user came to set. Where it sits comes after, because that
                // is already right when the editor was opened from a parent's "+".
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 12) {
                            name = it
                            nameTouched = true
                        }
                    },
                    label = { Text("名称") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("内置图标与名称", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "点一下就把名称和图标一起填好；也可以只当图标用来换样子。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = CategoryIcons.presets, key = { it.id }) { preset ->
                        val selected = preset.id == icon
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
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
                                .clickable {
                                    icon = preset.id
                                    if (!nameTouched || name.isBlank()) name = preset.name
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = CategoryIcons.vectorAt(preset.id),
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = preset.name,
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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

                Spacer(Modifier.height(14.dp))
                Text("层级", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("一级分类", "二级分类").forEachIndexed { index, label ->
                        val wantsParent = index == 1
                        SegmentedButton(
                            selected = wantsParent == (parentId != Category.ROOT),
                            onClick = {
                                parentId =
                                    if (wantsParent) candidates.first().id else Category.ROOT
                            },
                            enabled = !wantsParent || canNest,
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = { Text(label) },
                        )
                    }
                }
                if (hasChildren) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "它下面已经有二级分类，不能再变成二级分类。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (candidates.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "还没有一级分类，先建一个一级分类，再回来给它加二级分类。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (parentId != Category.ROOT) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        candidates.forEach { candidate ->
                            FilterChip(
                                selected = candidate.id == parentId,
                                onClick = { parentId = candidate.id },
                                label = { Text("挂在「${candidate.name}」下") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CategoryDraft(
                            name = name,
                            kind = kind,
                            icon = icon,
                            colorIndex = colorIndex,
                            parentId = parentId,
                        ),
                    ) { error = it }
                },
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
