package pl.lejdi.plannerkmp.core.database.di

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.database.KeyValueDatabase

private const val KEY_VALUE_DRIVER = "keyValueDriver"

val keyValueCacheModule = module {
    // The driver is bound in its own right so that `onClose` has something to close. A driver
    // created inline inside the database binding is unreachable afterwards — the generated database
    // class keeps no reference anything can get at — so the connection simply stayed open for the
    // life of the process, and a test that built the graph twice leaked one each time.
    single<SqlDriver>(named(KEY_VALUE_DRIVER)) {
        get<DatabaseDriverFactory>().createDriver(KeyValueDatabase.Schema, "keyvalue.db")
    } onClose { it?.close() }
    single { KeyValueDatabase(get(named(KEY_VALUE_DRIVER))) }
    single { get<KeyValueDatabase>().keyValueEntryQueries }
}
