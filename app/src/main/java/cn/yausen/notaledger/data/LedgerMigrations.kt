package cn.yausen.notaledger.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Both the two-level v1 development build and the earlier flat v2 retain their data. */
object LedgerMigrations {
    val FROM_1 = migrationFrom(1)
    val FROM_2 = migrationFrom(2)

    private fun migrationFrom(version: Int) = object : Migration(version, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(categories)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            val parent = if ("parentId" in columns) "parentId" else "0"
            val defaultFlag = when {
                "isDefault" in columns -> "isDefault"
                "builtIn" in columns -> "builtIn"
                else -> error("Unsupported category schema; original ledger has been retained")
            }
            db.execSQL("""CREATE TABLE categories_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, kind INTEGER NOT NULL, icon INTEGER NOT NULL,
                colorIndex INTEGER NOT NULL, parentId INTEGER NOT NULL,
                isDefault INTEGER NOT NULL, sortOrder INTEGER NOT NULL)""")
            db.execSQL("""INSERT INTO categories_new
                SELECT id, name, kind, icon, colorIndex, $parent, $defaultFlag, sortOrder FROM categories""")
            db.execSQL("DROP TABLE categories")
            db.execSQL("ALTER TABLE categories_new RENAME TO categories")
            db.execSQL("CREATE UNIQUE INDEX index_categories_name_kind_parentId ON categories(name, kind, parentId)")
            db.execSQL("CREATE INDEX index_categories_parentId ON categories(parentId)")

            db.execSQL("""CREATE TABLE transactions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amountCents INTEGER NOT NULL, kind INTEGER NOT NULL,
                categoryId INTEGER NOT NULL, note TEXT NOT NULL, timestamp INTEGER NOT NULL)""")
            // Use the same decimal conversion as new entries, including explicit overflow checking.
            db.query("SELECT id, amount, kind, categoryId, note, timestamp FROM transactions").use { cursor ->
                val insert = db.compileStatement("INSERT INTO transactions_new VALUES (?, ?, ?, ?, ?, ?)")
                try {
                    while (cursor.moveToNext()) {
                        insert.bindLong(1, cursor.getLong(0))
                        insert.bindLong(2, Money.toCents(cursor.getDouble(1)))
                        insert.bindLong(3, cursor.getLong(2))
                        insert.bindLong(4, cursor.getLong(3))
                        insert.bindString(5, cursor.getString(4))
                        insert.bindLong(6, cursor.getLong(5))
                        insert.executeInsert()
                    }
                } finally {
                    insert.close()
                }
            }
            db.execSQL("DROP TABLE transactions")
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            for (column in listOf("timestamp", "categoryId", "kind")) {
                db.execSQL("CREATE INDEX index_transactions_$column ON transactions($column)")
            }
        }
    }
}
