package pl.lejdi.plannerkmp.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable

/**
 * The window insets a feature screen's `Scaffold` pads its content by: its top and sides, never its
 * bottom.
 *
 * Every feature screen sits above the app's bottom bar, so its bottom edge is never a window edge —
 * the bar has already cleared the system navigation bar. At Material's default each screen's
 * Scaffold asked for the bottom inset as well and got it back only if the host's
 * `consumeWindowInsets` was seen, so whether a band of padding sat between the last row and the
 * bottom bar depended on inset propagation working. Leaving the side out here makes that band
 * impossible rather than cancelled out.
 *
 * The top stays the screen's own, which is what lets each screen's background run up behind the
 * status bar instead of the host's container colour showing there.
 */
val screenWindowInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
