package pl.lejdi.plannerkmp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a ViewModel's one-shot effect flow for as long as the screen is at least STARTED.
 *
 * Deliberately typed on [Flow] rather than on `BaseViewModel`, so `core:ui` does not need a
 * dependency on `core:mvi`. Call it as `CollectEffects(viewModel.effect) { ... }`.
 *
 * A plain `LaunchedEffect { flow.collect { } }` would keep consuming — and so keep firing
 * navigation and snackbars — while the screen sits backgrounded behind another one. Because the
 * effect channel buffers, nothing is lost by pausing: effects raised while stopped are delivered
 * on the next start.
 */
@Composable
fun <T> CollectEffects(
    effects: Flow<T>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(effects, lifecycleOwner, minActiveState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            effects.collect { onEffect(it) }
        }
    }
}
