package cn.yausen.notaledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import cn.yausen.notaledger.ui.components.CategoryIcons
import kotlinx.coroutines.flow.Flow

/** Expense or income. Stored as [code] so the value in the DB stays stable. */
enum class Kind(val code: Int) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        fun fromCode(code: Int): Kind = if (code == INCOME.code) INCOME else EXPENSE
    }
}

@Entity(
    tableName = "categories",
    indices = [
        // Sibling names are unique. A top-level category stores ROOT rather than
        // null in parentId, which is what lets one index cover both levels.
        Index(value = ["name", "kind", "parentId"], unique = true),
        Index("parentId"),
    ],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Expense or income. A sub-category always shares its parent's kind. */
    val kind: Int,
    /**
     * Stable id from [cn.yausen.notaledger.ui.components.CategoryIcons]; an index
     * rather than a class name so icons can be redesigned without touching data.
     */
    val icon: Int,
    val colorIndex: Int,
    /**
     * Parent category id, or [ROOT] when this is a top-level category. There are
     * only two levels, so a parent is always itself top-level.
     */
    val parentId: Long = ROOT,
    /**
     * One of the categories seeded on first launch. The flag only earns the row a
     * "默认" label: a default category can be renamed, restyled, moved between
     * levels and deleted exactly like a hand-made one, and deleting it does not
     * bring it back.
     */
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
) {
    /** Only a top-level category can carry sub-categories. */
    val isTopLevel: Boolean get() = parentId == ROOT

    companion object {
        /** Sentinel for [parentId]: no parent, so this is a top-level category. */
        const val ROOT = 0L
    }
}

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("categoryId"), Index("kind")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Always a positive magnitude; [kind] decides the sign. */
    val amountCents: Long,
    val kind: Int,
    val categoryId: Long,
    val note: String = "",
    /** Epoch millis of when the money moved; drives ordering and grouping. */
    val timestamp: Long,
) {
    @get:androidx.room.Ignore
    val amount: Double get() = amountCents / 100.0
}

/** Category totals for the pie chart. */
data class CategoryTotal(
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "total") val total: Double,
    @ColumnInfo(name = "count") val count: Int,
)

/** One bar in the daily spending chart. */
data class DailyTotal(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "total") val total: Double,
)

@Dao
interface LedgerDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY timestamp DESC
        """,
    )
    fun observeBetween(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): Transaction?

    @Query(
        """
        SELECT IFNULL(SUM(amountCents), 0) FROM transactions
        WHERE kind = :kind AND timestamp BETWEEN :start AND :end
        """,
    )
    fun observeSum(kind: Int, start: Long, end: Long): Flow<Long>

    @Query(
        """
        SELECT categoryId AS categoryId, SUM(amountCents) / 100.0 AS total, COUNT(*) AS count
        FROM transactions
        WHERE kind = :kind AND timestamp BETWEEN :start AND :end
        GROUP BY categoryId
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(kind: Int, start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(amountCents) / 100.0 AS total
        FROM transactions
        WHERE kind = :kind AND timestamp BETWEEN :start AND :end
        GROUP BY day
        ORDER BY day ASC
        """,
    )
    fun observeDailyTotals(kind: Int, start: Long, end: Long): Flow<List<DailyTotal>>

    /** Total spent per month for the trend chart, keyed as yyyy-MM. */
    @Query(
        """
        SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(amountCents) / 100.0 AS total
        FROM transactions
        WHERE kind = :kind AND timestamp BETWEEN :start AND :end
        GROUP BY day
        ORDER BY day ASC
        """,
    )
    fun observeMonthlyTotals(kind: Int, start: Long, end: Long): Flow<List<DailyTotal>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Every category in one list. Top-level rows come first within a kind and the
     * sub-categories follow grouped by their parent, so the caller rebuilds the
     * tree in a single pass instead of querying per parent.
     */
    @Query("SELECT * FROM categories ORDER BY kind ASC, parentId ASC, sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findCategory(id: Long): Category?

    /** Sub-categories of one top-level category, in the order the user arranged them. */
    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC, id ASC")
    suspend fun childrenOf(parentId: Long): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    /**
     * Records across a whole subtree. The caller always passes at least the
     * category itself, because SQLite rejects an empty `IN ()` list.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IN (:categoryIds)")
    suspend fun countTransactionsInCategories(categoryIds: List<Long>): Int

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)
}

@Database(
    entities = [Category::class, Transaction::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notaledger-ledger.db",
                )
                    .addMigrations(LedgerMigrations.FROM_1, LedgerMigrations.FROM_2)
                    .build()
                    .also { instance = it }
            }
    }
}

/** Seeds the categories a person actually uses, on first launch only. */
object DefaultCategories {

    private data class Seed(val name: String, val iconKey: Int, val color: Int)

    private val expense = listOf(
        Seed("餐饮", CategoryIcons.RESTAURANT, 14),
        Seed("交通", CategoryIcons.DIRECTIONS_BUS, 5),
        Seed("购物", CategoryIcons.SHOPPING_BAG, 2),
        Seed("居住", CategoryIcons.HOUSE, 15),
        Seed("娱乐", CategoryIcons.MOVIE, 3),
        Seed("医疗", CategoryIcons.MEDICAL_SERVICES, 0),
        Seed("学习", CategoryIcons.MENU_BOOK, 4),
        Seed("通讯", CategoryIcons.SMARTPHONE, 7),
        Seed("人情", CategoryIcons.CARD_GIFTCARD, 1),
        Seed("其他", CategoryIcons.CONTENT_CUT, 11),
    )

    private val income = listOf(
        Seed("工资", CategoryIcons.WORK, 10),
        Seed("奖金", CategoryIcons.EMOJI_EVENTS, 12),
        Seed("理财", CategoryIcons.SAVINGS, 8),
        Seed("兼职", CategoryIcons.BUSINESS_CENTER, 9),
        Seed("红包", CategoryIcons.REDEEM, 0),
        Seed("其他收入", CategoryIcons.LOCAL_ATM, 6),
    )

    /**
     * Writes the seed set. The caller owns the "already seeded" decision instead of
     * this object asking whether the table is empty: these rows are deletable now, so
     * a user who clears a list out would otherwise watch it grow back on the next
     * launch.
     */
    suspend fun seed(dao: LedgerDao) {
        insertAll(dao, Kind.EXPENSE.code, expense)
        insertAll(dao, Kind.INCOME.code, income)
    }

    private suspend fun insertAll(dao: LedgerDao, kind: Int, seeds: List<Seed>) {
        seeds.forEachIndexed { index, seed ->
            dao.insertCategory(
                Category(
                    name = seed.name,
                    kind = kind,
                    icon = seed.iconKey,
                    colorIndex = seed.color,
                    parentId = Category.ROOT,
                    isDefault = true,
                    sortOrder = index,
                ),
            )
        }
    }
}
