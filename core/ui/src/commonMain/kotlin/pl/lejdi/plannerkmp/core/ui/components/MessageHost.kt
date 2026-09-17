package pl.lejdi.plannerkmp.core.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Shows the screen's current message as a snackbar, then tells the ViewModel it has been shown.
 *
 * Every screen here had its own copy of this `LaunchedEffect`, and by the third the copies had
 * stopped agreeing: two guarded against consuming a message that a full-screen error was already
 * rendering, one did not, and one had lost its retry action entirely.
 *
 * Takes plain values rather than a state object, for the reason `CollectEffects` takes a `Flow`:
 * `core:ui` has no dependency on `core:mvi` and should not gain one. It takes its strings already
 * resolved, because the wording belongs to the feature that owns the resources.
 *
 * @param M whatever the caller uses as the message's *identity*. In practice that is
 *   `UiMessage<SomeFeatureMessage>` — the typed value plus the occurrence id that makes the same
 *   message raised twice compare unequal — not the bare enum, which is what this doc used to claim.
 *   Generic rather than `Any?`, which is what this used to take: every screen models its messages
 *   as a typed enum precisely so the compiler can check the mapping to wording, and erasing that to
 *   `Any?` at the boundary threw the guarantee away for nothing — the parameter is only ever
 *   compared for equality, which needs no upper bound beyond this.
 * @param message the message's identity — re-shows only when this changes, so a recomposition does
 *   not replay a snackbar the user already dismissed.
 * @param suppressed true when something else on screen is already reporting this failure — a
 *   full-screen [ErrorView], typically. Consuming the message here would erase that screen.
 * @param actionLabel the button on the snackbar, when [onAction] is supplied. Named for the action
 *   rather than for "retry": the same slot carries an Undo for a destructive action, and a
 *   parameter called `retryLabel` made that read like a mistake at the call site.
 * @param onAction supplied only for a message the user can actually do something about; when null,
 *   no button is offered.
 */
@Composable
fun <M : Any> MessageHost(
    snackbarHostState: SnackbarHostState,
    message: M?,
    messageText: String?,
    onShown: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    suppressed: Boolean = false,
) {
    LaunchedEffect(message, messageText, suppressed) {
        if (messageText == null || suppressed) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = messageText,
            actionLabel = actionLabel.takeIf { onAction != null },
        )
        if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        onShown()
    }
}
