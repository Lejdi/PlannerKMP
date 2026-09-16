package pl.lejdi.plannerkmp.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Platform-derived dynamic color scheme (Android's wallpaper-based Material You), or null where
 * the platform has no such concept (iOS) so [PlannerTheme] falls back to a static scheme.
 */
@Composable
expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
fun PlannerTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = dynamicColorScheme(useDarkTheme)
        ?: if (useDarkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
