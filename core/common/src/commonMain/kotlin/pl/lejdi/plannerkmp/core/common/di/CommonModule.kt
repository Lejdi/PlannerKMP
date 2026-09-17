package pl.lejdi.plannerkmp.core.common.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.common.AppCoroutineScope
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DefaultCoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.SystemTodayProvider
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.platformLogger

val commonModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single { AppCoroutineScope(get()) }
    // On the app scope, so the one shared midnight timer outlives any screen that watches it.
    single<TodayProvider> { SystemTodayProvider(get<AppCoroutineScope>()) }
    single<Logger> { platformLogger() }
}
