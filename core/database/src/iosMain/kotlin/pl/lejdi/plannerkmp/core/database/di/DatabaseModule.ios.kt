package pl.lejdi.plannerkmp.core.database.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.database.NativeDatabaseDriverFactory

actual val databaseModule: Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
}
