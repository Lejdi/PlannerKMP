package pl.lejdi.plannerkmp.feature.routines

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.routines.resources.Res
import pl.lejdi.plannerkmp.feature.routines.resources.routines_tab_title
import pl.lejdi.plannerkmp.feature.routines.ui.RoutinesScreen

class RoutinesNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<RoutinesNavKey> { RoutinesScreen() }
    }
}

class RoutinesNavKeySerializersContributor : NavKeySerializersContributor {
    override fun PolymorphicModuleBuilder<NavKey>.contribute() {
        subclass(RoutinesNavKey::class)
    }
}

/**
 * Three distinct classes, not three more `single<...>` of the existing types: Koin keys a
 * definition by type, so a second `single<FeatureTab>` would silently override its neighbour and
 * the app would come up a tab short.
 *
 * `Icons.Filled.Refresh` is the one cyclical glyph in `material-icons-core` — the only icon set on
 * the classpath — and collides with neither Tasks' `AutoMirrored.Filled.List` nor Grocery's
 * `Filled.ShoppingCart`.
 */
class RoutinesFeatureTab : FeatureTab {
    override val id: String = "routines"
    override val order: Int = 2
    override val title = Res.string.routines_tab_title
    override val icon = Icons.Filled.Refresh
    override val rootKey = RoutinesNavKey
}
