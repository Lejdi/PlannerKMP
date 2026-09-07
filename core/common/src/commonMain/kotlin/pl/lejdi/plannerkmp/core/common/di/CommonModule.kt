package pl.lejdi.plannerkmp.core.common.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DefaultCoroutineDispatchers

val commonModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
}
