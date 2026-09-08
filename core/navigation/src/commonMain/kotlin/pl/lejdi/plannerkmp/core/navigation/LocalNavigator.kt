package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.runtime.compositionLocalOf

val LocalNavigator = compositionLocalOf<Navigator> {
    error("No Navigator provided — wrap this composable in CompositionLocalProvider(LocalNavigator provides ...)")
}
