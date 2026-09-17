package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided — wrap this composable in CompositionLocalProvider(LocalNavigator provides ...)")
}
