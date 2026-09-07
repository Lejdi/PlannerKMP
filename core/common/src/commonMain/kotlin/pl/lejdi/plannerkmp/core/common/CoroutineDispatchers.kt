package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface CoroutineDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

// Dispatchers.IO isn't part of the common multiplatform API (Kotlin/Native
// only exposes it as internal), so the IO dispatcher is platform-specific.
expect val ioDispatcher: CoroutineDispatcher

class DefaultCoroutineDispatchers : CoroutineDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = ioDispatcher
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
