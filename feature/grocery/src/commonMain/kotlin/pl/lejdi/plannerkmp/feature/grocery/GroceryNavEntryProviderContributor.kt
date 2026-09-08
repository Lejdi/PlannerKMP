package pl.lejdi.plannerkmp.feature.grocery

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListScreen

class GroceryNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<GroceryNavKey> { GroceryListScreen() }
    }
}
