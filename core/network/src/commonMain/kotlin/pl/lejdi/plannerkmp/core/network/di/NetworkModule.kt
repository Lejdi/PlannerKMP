package pl.lejdi.plannerkmp.core.network.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.network.createHttpClient

val networkModule = module {
    single { createHttpClient() }
}
