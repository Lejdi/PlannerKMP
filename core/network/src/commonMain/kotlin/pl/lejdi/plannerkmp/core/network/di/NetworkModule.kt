package pl.lejdi.plannerkmp.core.network.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import pl.lejdi.plannerkmp.core.network.NetworkConfig
import pl.lejdi.plannerkmp.core.network.createHttpClient

/**
 * Takes its [config] as an argument rather than resolving it from the graph: the app decides
 * whether HTTP logging is on (it follows the build type), and a module that depends on a binding
 * declared somewhere else is a graph that only works if you assemble it in the right order.
 */
fun networkModule(config: NetworkConfig = NetworkConfig()): Module = module {
    single { config }
    // onClose, like every SqlDriver binding: the client owns a connection pool and an engine
    // dispatcher, and a graph built twice in one process leaked one each time.
    single { createHttpClient(config = get()) } onClose { it?.close() }
}
