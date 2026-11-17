package cn.yausen.notaledger.data

/** A top-level category together with the sub-categories hanging off it. */
data class CategoryBranch(val parent: Category, val children: List<Category>)

/**
 * Shapes a flat category list into the two levels the UI works with. Top-level rows
 * keep their [Category.sortOrder] and sub-categories are grouped under their parent
 * in the same order. A sub-category whose parent is gone is dropped instead of being
 * rendered at the wrong level.
 */
fun List<Category>.branchesOf(kind: Kind): List<CategoryBranch> {
    val ofKind = filter { it.kind == kind.code }
    val childrenByParent = ofKind.filterNot { it.isTopLevel }.groupBy { it.parentId }
    return ofKind.filter { it.isTopLevel }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { parent ->
            CategoryBranch(
                parent = parent,
                children = childrenByParent[parent.id].orEmpty()
                    .sortedWith(compareBy({ it.sortOrder }, { it.id })),
            )
        }
}

/** Category id -> category, for resolving a sub-category's parent. */
fun List<Category>.indexedById(): Map<Long, Category> = associateBy { it.id }

/**
 * How a category is written out: a top-level one is just its own name, while a
 * sub-category reads as "一级名称-二级名称". A missing parent falls back to the
 * category's own name rather than leaving a dangling dash.
 */
fun List<Category>.displayName(category: Category?): String {
    if (category == null) return "未分类"
    if (category.isTopLevel) return category.name
    val parent = firstOrNull { it.id == category.parentId } ?: return category.name
    return "${parent.name}-${category.name}"
}

/** [displayName] for a category id; an id with no row reads as 未分类. */
fun List<Category>.displayNameOf(categoryId: Long): String =
    displayName(firstOrNull { it.id == categoryId })
