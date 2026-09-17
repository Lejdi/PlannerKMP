package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.StringResource

/**
 * One bottom-navigation destination, contributed by the feature that owns it.
 *
 * `:shared` builds its bottom bar from every [FeatureTab] Koin knows about, so adding a feature
 * means adding a module — not editing the composition root. This is the same reason
 * [NavEntryProviderContributor] exists; without it the bottom bar was still a hardcoded list of
 * every feature in the app.
 *
 * An interface, not a data holder: Koin keys definitions by type, so two `single<FeatureTab>`
 * declarations would silently override each other and the app would come up with one tab. Each
 * feature implements it with its own class instead, exactly as it does for the two contributors.
 */
interface FeatureTab {
    /**
     * Stable identity, used to remember which tab was selected across process death.
     *
     * Not the bar position: that is derived from [order] over whichever features are installed, so
     * saving an index meant a restored app could come back on a different tab than it left on if
     * the set of features had changed in between.
     */
    val id: String

    /** Left-to-right position in the bottom bar. */
    val order: Int
    val title: StringResource
    val icon: ImageVector

    /** The destination this tab's back stack starts at. */
    val rootKey: NavKey
}
