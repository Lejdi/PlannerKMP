package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers this app injects.
 *
 * There is deliberately no `main`: it was declared here and read by nothing, while every test
 * double still had to implement it. Presentation code reaches Main through `viewModelScope`, which
 * is already Main-confined, so nothing needed to name it.
 */
interface CoroutineDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

// Dispatchers.IO isn't part of the common multiplatform API (Kotlin/Native
// only exposes it as internal), so the IO dispatcher is platform-specific.
expect val ioDispatcher: CoroutineDispatcher

class DefaultCoroutineDispatchers : CoroutineDispatchers {
    override val io: CoroutineDispatcher get() = ioDispatcher
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
