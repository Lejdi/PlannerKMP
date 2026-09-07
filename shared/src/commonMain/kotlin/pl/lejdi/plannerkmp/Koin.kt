package pl.lejdi.plannerkmp

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import pl.lejdi.plannerkmp.core.common.di.commonModule
import pl.lejdi.plannerkmp.core.database.di.databaseModule
import pl.lejdi.plannerkmp.core.network.di.networkModule

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    appDeclaration()
    modules(commonModule, databaseModule, networkModule)
}
