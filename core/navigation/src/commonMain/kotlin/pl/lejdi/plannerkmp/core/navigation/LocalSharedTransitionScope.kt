package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

// Provided by :shared at the point where it wraps a tab's NavDisplay in a
// SharedTransitionLayout, so feature screens can read it without NavEntryProviderContributor
// needing a SharedTransitionScope parameter of its own.
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope> =
    compositionLocalOf {
        error(
            "Unexpected access to LocalSharedTransitionScope. Wrap the NavDisplay in a " +
                "SharedTransitionLayout and provide this local before using it.",
        )
    }
