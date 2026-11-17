package cn.yausen.notaledger.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-level rules the UI leans on: a sub-category is grouped under its parent,
 * and it is written out as `一级名称-二级名称` while a top-level one keeps its own
 * name. Both are pure functions over a category list, so they are pinned here
 * rather than re-checked by hand on every screen that shows a name.
 */
class CategoryTreeTest {

    private fun category(
        id: Long,
        name: String,
        parentId: Long = Category.ROOT,
        kind: Kind = Kind.EXPENSE,
        sortOrder: Int = 0,
    ) = Category(
        id = id,
        name = name,
        kind = kind.code,
        icon = 1,
        colorIndex = 0,
        parentId = parentId,
        sortOrder = sortOrder,
    )

    @Test
    fun `sub-categories are grouped under their parent in sort order`() {
        val food = category(1, "餐饮")
        val dinner = category(2, "晚餐", parentId = 1, sortOrder = 2)
        val breakfast = category(3, "早餐", parentId = 1, sortOrder = 1)
        val traffic = category(4, "交通")

        val branches = listOf(food, traffic, dinner, breakfast).branchesOf(Kind.EXPENSE)

        assertEquals(listOf("餐饮", "交通"), branches.map { it.parent.name })
        assertEquals(listOf("早餐", "晚餐"), branches.first().children.map { it.name })
        assertEquals(emptyList<String>(), branches.last().children.map { it.name })
    }

    @Test
    fun `a sub-category without its parent is dropped rather than promoted`() {
        val orphan = category(2, "早餐", parentId = 99)

        assertEquals(emptyList<CategoryBranch>(), listOf(orphan).branchesOf(Kind.EXPENSE))
    }

    @Test
    fun `expense and income never mix`() {
        val expense = category(1, "餐饮")
        val income = category(2, "工资", kind = Kind.INCOME)
        val all = listOf(expense, income)

        assertEquals(listOf("餐饮"), all.branchesOf(Kind.EXPENSE).map { it.parent.name })
        assertEquals(listOf("工资"), all.branchesOf(Kind.INCOME).map { it.parent.name })
    }

    @Test
    fun `a sub-category reads as parent then child`() {
        val all = listOf(
            category(1, "餐饮"),
            category(2, "早餐", parentId = 1),
        )

        assertEquals("餐饮", all.displayName(all[0]))
        assertEquals("餐饮-早餐", all.displayName(all[1]))
        assertEquals("餐饮-早餐", all.displayNameOf(2))
    }

    @Test
    fun `a name never renders with a dangling dash`() {
        val orphan = listOf(category(2, "早餐", parentId = 99))

        // The parent is gone: the child still shows its own name.
        assertEquals("早餐", orphan.displayNameOf(2))
        assertEquals("未分类", emptyList<Category>().displayNameOf(7))
        assertEquals("未分类", emptyList<Category>().displayName(null))
    }
}
