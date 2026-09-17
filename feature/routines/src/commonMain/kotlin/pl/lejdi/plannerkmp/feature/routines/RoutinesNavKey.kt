package pl.lejdi.plannerkmp.feature.routines

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Serializable so the tab's back stack can be saved and restored. One screen, no arguments. */
@Serializable
data object RoutinesNavKey : NavKey
