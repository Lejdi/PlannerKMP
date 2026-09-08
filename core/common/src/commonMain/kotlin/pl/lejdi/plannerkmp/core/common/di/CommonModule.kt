package pl.lejdi.plannerkmp.core.common.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DefaultCoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.SystemTodayProvider
import pl.lejdi.plannerkmp.core.common.TodayProvider

val commonModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single<TodayProvider> { SystemTodayProvider() }
}
