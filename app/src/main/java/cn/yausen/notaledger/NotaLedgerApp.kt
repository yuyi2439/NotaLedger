package cn.yausen.notaledger

import android.app.Application
import android.content.Context
import cn.yausen.notaledger.data.AppDatabase
import cn.yausen.notaledger.data.LedgerRepository
import cn.yausen.notaledger.data.UserPreferences

/**
 * Single application object that owns the database, the repository and the
 * lightweight preference store, so no DI framework is needed for an app this size.
 */
class NotaLedgerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val repository: LedgerRepository by lazy { LedgerRepository(database, preferences) }

    val preferences: UserPreferences by lazy { UserPreferences(this) }
}

/** Convenience accessor for the app-scoped container from any [Context]. */
val Context.appContainer: NotaLedgerApp
    get() = applicationContext as NotaLedgerApp
