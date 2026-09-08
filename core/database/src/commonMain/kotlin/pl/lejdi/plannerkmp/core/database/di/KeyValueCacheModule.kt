package pl.lejdi.plannerkmp.core.database.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.Database
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory

val keyValueCacheModule = module {
    single {
        Database(get<DatabaseDriverFactory>().createDriver(Database.Schema, "keyvalue.db"))
    }
    single { get<Database>().keyValueEntryQueries }
}
