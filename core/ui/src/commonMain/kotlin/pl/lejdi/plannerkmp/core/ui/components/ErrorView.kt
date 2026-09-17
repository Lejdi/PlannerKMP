package pl.lejdi.plannerkmp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import pl.lejdi.plannerkmp.core.ui.resources.Res
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Spacing

/**
 * A full-screen failure with an optional retry.
 *
 * [message] is a parameter, never a literal: the wording of a failure belongs to the feature that
 * knows what failed. [retryLabel] defaults to this module's own generic "Retry", which carries no
 * domain and which every feature was otherwise declaring for itself — a screen that wants different
 * wording still passes it.
 */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = stringResource(Res.string.core_action_retry),
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null && retryLabel != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(onClick = onRetry) {
                Text(retryLabel)
            }
        }
    }
}
