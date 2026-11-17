package cn.yausen.notaledger.data

import android.database.sqlite.SQLiteDatabase
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cn.yausen.notaledger.ui.AddEditViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class LedgerPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Isolate tests from the user's real ledger and preferences.
    private val context = object : ContextWrapper(instrumentation.targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("persistence-test-$name", mode)
    }

    @Test fun migratesBothHistoricalSchemasWithoutLosingRecords() = runBlocking {
        for (version in listOf(1, 2)) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val categoryFields = if (version == 1) "parentId INTEGER NOT NULL, isDefault INTEGER NOT NULL" else "builtIn INTEGER NOT NULL"
                old.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, kind INTEGER NOT NULL, icon INTEGER NOT NULL, colorIndex INTEGER NOT NULL, $categoryFields, sortOrder INTEGER NOT NULL)")
                val categoryValues = if (version == 1) "0, 1" else "1"
                old.execSQL("INSERT INTO categories VALUES (7, '餐饮', 0, 1, 2, $categoryValues, 0)")
                if (version == 1) old.execSQL("INSERT INTO categories VALUES (8, '午餐', 0, 1, 2, 7, 0, 1)")
                old.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, kind INTEGER NOT NULL, categoryId INTEGER NOT NULL, note TEXT NOT NULL, timestamp INTEGER NOT NULL)")
                val categoryId = if (version == 1) 8 else 7
                old.execSQL("INSERT INTO transactions VALUES (42, 1.005, 0, $categoryId, '保留备注', 123456789)")
                old.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(LedgerMigrations.FROM_1, LedgerMigrations.FROM_2).build()
            try {
                val record = db.ledgerDao().findById(42)!!
                assertEquals(101L, record.amountCents)
                assertEquals("保留备注", record.note)
                assertEquals(123456789L, record.timestamp)
                assertEquals(if (version == 1) 8L else 7L, record.categoryId)
                assertTrue(db.ledgerDao().findCategory(7)!!.isDefault)
                if (version == 1) assertEquals(7L, db.ledgerDao().findCategory(8)!!.parentId)
                assertEquals(101L, db.ledgerDao().observeSum(0, 0, Long.MAX_VALUE).first())
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun rapidSaveInsertsOnceAndInvalidCategoryCanBeRetried() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val preferences = UserPreferences(context).apply { categoriesSeeded = true }
            val repo = LedgerRepository(db, preferences)
            val category = repo.addCategory(Category(name = "测试", kind = 0, icon = 1, colorIndex = 0))
            lateinit var vm: AddEditViewModel
            val failed = CompletableDeferred<Unit>()
            val saved = CompletableDeferred<Unit>()
            instrumentation.runOnMainSync {
                vm = AddEditViewModel(repo, null)
                vm.setAmount(12.34)
                vm.setCategory(-1)
                vm.save({ failed.completeExceptionally(AssertionError("Invalid category saved")) }, { failed.complete(Unit) })
            }
            withTimeout(5000) { failed.await() }
            assertFalse(vm.isSaving.value)
            assertEquals(12.34, vm.formState.value.amount, 0.0)
            instrumentation.runOnMainSync {
                vm.setCategory(category)
                repeat(10) { vm.save({ saved.complete(Unit) }, { saved.completeExceptionally(AssertionError(it)) }) }
            }
            withTimeout(5000) { saved.await() }
            assertEquals(1, db.ledgerDao().countTransactions())
            assertEquals(1234L, db.ledgerDao().observeAll().first().single().amountCents)
        } finally { db.close() }
    }

    @Test fun deliberatelyEmptiedCategoriesStayEmpty() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val preferences = UserPreferences(context).apply { categoriesSeeded = false }
            val repo = LedgerRepository(db, preferences)
            repo.seedDefaultsIfNeeded()
            assertTrue(db.ledgerDao().countCategories() > 0)
            repo.currentCategories().forEach { repo.deleteCategoryTree(it) }
            repo.seedDefaultsIfNeeded()
            assertEquals(0, db.ledgerDao().countCategories())
        } finally { db.close() }
    }
}
