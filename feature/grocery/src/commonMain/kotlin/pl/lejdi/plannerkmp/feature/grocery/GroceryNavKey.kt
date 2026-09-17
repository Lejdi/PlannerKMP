package pl.lejdi.plannerkmp.feature.grocery

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Serializable so the tab's back stack can be saved and restored. */
@Serializable
data object GroceryNavKey : NavKey
