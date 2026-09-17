package pl.lejdi.plannerkmp.feature.grocery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.grocery.resources.Res
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_tab_title
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListScreen

class GroceryNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<GroceryNavKey> { GroceryListScreen() }
    }
}

class GroceryNavKeySerializersContributor : NavKeySerializersContributor {
    override fun PolymorphicModuleBuilder<NavKey>.contribute() {
        subclass(GroceryNavKey::class)
    }
}

class GroceryFeatureTab : FeatureTab {
    override val id: String = "grocery"
    override val order: Int = 1
    override val title = Res.string.grocery_tab_title
    override val icon = Icons.Filled.ShoppingCart
    override val rootKey = GroceryNavKey
}
