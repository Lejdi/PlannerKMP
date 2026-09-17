package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

// Dispatchers.IO exists on Kotlin/Native (since kotlinx-coroutines 1.7) — it is just not visible
// from common code. Blocking SQLite work used to run on Dispatchers.Default here, i.e. on the
// CPU-sized pool, where it competes with real computation.
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
