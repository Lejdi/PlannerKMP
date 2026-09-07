package pl.lejdi.plannerkmp.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

// Implemented (and Koin-bound as a multi-binding) by each feature so :shared
// can build one entryProvider without knowing which features exist.
fun interface NavEntryProviderContributor {
    fun EntryProviderScope<NavKey>.contribute()
}
