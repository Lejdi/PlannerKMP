package pl.lejdi.plannerkmp.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import pl.lejdi.plannerkmp.core.ui.resources.Res
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry

/**
 * The spinner / full-screen failure / content branch every screen in this app opens with.
 *
 * Written out per screen, it was three copies of the same three-way branch — and the screen that
 * forgot the middle arm showed a blank, fully interactive form after a failed load, with Save and
 * Delete live against a record it had never read.
 *
 * [errorMessage] is a parameter and already resolved: what failed is the feature's business, not
 * this module's. [retryLabel] defaults to the generic one, the same rule [ErrorView] follows.
 *
 * [modifier] reaches all three branches, including the content. It used to apply to the spinner and
 * the error view only, so every caller re-applied part of it by hand around its own content —
 * three copies of `.padding(top = padding.calculateTopPadding())`, each of them silently dropping
 * the start, end and bottom insets that the spinner in the same position honoured. That is exactly
 * the per-screen duplication this component exists to remove.
 */
@Composable
fun LoadableContent(
    isLoading: Boolean,
    hasTerminalLoadFailure: Boolean,
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(Res.string.core_action_retry),
    content: @Composable () -> Unit,
) {
    when {
        isLoading -> LoadingView(modifier = modifier)
        hasTerminalLoadFailure -> ErrorView(
            message = errorMessage,
            modifier = modifier,
            retryLabel = retryLabel,
            onRetry = onRetry,
        )
        else -> Box(modifier = modifier) { content() }
    }
}
