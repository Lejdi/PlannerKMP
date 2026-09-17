package pl.lejdi.plannerkmp.feature.routines.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.FieldError
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.components.PlainTextField
import pl.lejdi.plannerkmp.core.ui.resources.core_action_cancel
import pl.lejdi.plannerkmp.core.ui.resources.core_action_delete
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.core.ui.theme.itemTitle
import pl.lejdi.plannerkmp.feature.routines.domain.TodayRoutine
import pl.lejdi.plannerkmp.feature.routines.resources.Res
import pl.lejdi.plannerkmp.feature.routines.resources.routines_action_confirm
import pl.lejdi.plannerkmp.feature.routines.resources.routines_add_routine
import pl.lejdi.plannerkmp.feature.routines.resources.routines_delete_message
import pl.lejdi.plannerkmp.feature.routines.resources.routines_delete_routine
import pl.lejdi.plannerkmp.feature.routines.resources.routines_delete_title
import pl.lejdi.plannerkmp.feature.routines.resources.routines_edit_routine
import pl.lejdi.plannerkmp.feature.routines.resources.routines_empty_list
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_delete_failed
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_load_failed
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_name_blank
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_routine_gone
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_save_failed
import pl.lejdi.plannerkmp.feature.routines.resources.routines_error_toggle_failed
import pl.lejdi.plannerkmp.feature.routines.resources.routines_field_description
import pl.lejdi.plannerkmp.feature.routines.resources.routines_field_name
import pl.lejdi.plannerkmp.feature.routines.resources.routines_mark_done
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

@Composable
fun RoutinesScreen(viewModel: RoutinesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(RoutinesEvent.MessageShown) },
        actionLabel = stringResource(CoreRes.string.core_action_retry),
        onAction = when (message?.value) {
            RoutineMessage.LoadFailed -> {
                { viewModel.onEvent(RoutinesEvent.RetryClicked) }
            }
            else -> null
        },
        suppressed = state.hasTerminalLoadFailure,
    )

    RoutinesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

/** Stateless: takes the state and one event sink, never the ViewModel. */
@Composable
internal fun RoutinesContent(
    state: RoutinesState,
    snackbarHostState: SnackbarHostState,
    onEvent: (RoutinesEvent) -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.routines_error_load_failed),
            onRetry = { onEvent(RoutinesEvent.RetryClicked) },
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "top-spacer") { Spacer(modifier = Modifier.height(Spacing.lg)) }

                if (state.routines.isEmpty() && state.editor?.target != EditorTarget.New) {
                    item(key = "empty-state") {
                        Text(
                            text = stringResource(Res.string.routines_empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.xl, horizontal = Spacing.lg),
                        )
                    }
                }

                items(state.routines, key = { it.id }) { routine ->
                    val editor = state.editor
                    if (editor != null && editor.target == EditorTarget.Existing(routine.id)) {
                        InlineEditCard(
                            editor = editor,
                            canSubmit = !state.isEditorBusy,
                            onEvent = onEvent,
                        )
                    } else {
                        RoutineRow(
                            routine = routine,
                            // This row only, so ticking one leaves the rest tappable.
                            canToggle = !state.isToggling(routine.id),
                            onEvent = onEvent,
                        )
                    }
                }

                // Keyed: without one the trailing item's identity is its index, so it is disposed
                // and rebuilt whenever the list length changes — and it hosts the "new routine"
                // editor, whose text fields would lose focus and dismiss the IME.
                item(key = "add-control") {
                    AddControl(
                        editor = state.editor,
                        canSubmit = !state.isEditorBusy,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }

    // `if`, not `?.let` — the dialog needs only the fact that a deletion is pending; the id itself
    // lives in the state and is read by the ViewModel when the confirmation arrives.
    if (state.pendingDeletionId != null) {
        DeleteConfirmationDialog(
            canConfirm = !state.isEditorBusy,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(canConfirm: Boolean, onEvent: (RoutinesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(RoutinesEvent.DeleteCancelled) },
        title = { Text(stringResource(Res.string.routines_delete_title)) },
        text = { Text(stringResource(Res.string.routines_delete_message)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(RoutinesEvent.DeleteConfirmed) },
                enabled = canConfirm,
            ) {
                Text(stringResource(CoreRes.string.core_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(RoutinesEvent.DeleteCancelled) }) {
                Text(stringResource(CoreRes.string.core_action_cancel))
            }
        },
    )
}

@Composable
private fun AddControl(
    editor: RoutineEditor?,
    canSubmit: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.lg).animateContentSize()) {
        if (editor != null && editor.target == EditorTarget.New) {
            InlineEditCard(editor = editor, canSubmit = canSubmit, onEvent = onEvent)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingActionButton(
                    onClick = { onEvent(RoutinesEvent.EditorOpened(EditorTarget.New)) },
                    modifier = Modifier.padding(Spacing.lg),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.routines_add_routine))
                }
            }
        }
    }
}

@Composable
private fun InlineEditCard(
    editor: RoutineEditor,
    canSubmit: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(Sizing.CARD_WIDTH_FRACTION).padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = Sizing.inlineEditorMinHeight)
                    .weight(1f)
                    .padding(end = Spacing.sm),
            ) {
                PlainTextField(
                    value = editor.name,
                    onValueChange = { onEvent(RoutinesEvent.EditorNameChanged(it)) },
                    textStyle = MaterialTheme.typography.itemTitle,
                    placeholder = stringResource(Res.string.routines_field_name),
                    isError = editor.nameError,
                )
                if (editor.nameError) {
                    FieldError(
                        text = stringResource(Res.string.routines_error_name_blank),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                PlainTextField(
                    value = editor.description,
                    onValueChange = { onEvent(RoutinesEvent.EditorDescriptionChanged(it)) },
                    textStyle = LocalTextStyle.current,
                    placeholder = stringResource(Res.string.routines_field_description),
                    singleLine = false,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onEvent(RoutinesEvent.EditorConfirmed) },
                    enabled = canSubmit,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(Res.string.routines_action_confirm),
                    )
                }
                // Deleting is reachable only from the editor of an existing row: it is rare and
                // deliberate, unlike the tick, so it sits behind opening the row and a confirmation.
                (editor.target as? EditorTarget.Existing)?.let { target ->
                    IconButton(onClick = { onEvent(RoutinesEvent.DeleteRequested(target.id)) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(Res.string.routines_delete_routine),
                        )
                    }
                }
                TextButton(onClick = { onEvent(RoutinesEvent.EditorCancelled) }) {
                    Text(stringResource(CoreRes.string.core_action_cancel))
                }
            }
        }
    }
}

/**
 * A row: a checkbox for today, and editing reachable two ways.
 *
 * The long press stays, labelled so assistive technology can offer it, and the explicit button
 * makes it visible for everyone else — the grocery list's convention, for the same reason.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineRow(
    routine: TodayRoutine,
    canToggle: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    val editLabel = stringResource(Res.string.routines_edit_routine)
    val doneLabel = stringResource(Res.string.routines_mark_done)
    val openEditor = { onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(routine.id))) }
    Card(
        colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .animateContentSize()
            .combinedClickable(
                onLongClickLabel = editLabel,
                onLongClick = openEditor,
                onClick = openEditor,
                role = Role.Button,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(Sizing.CARD_WIDTH_FRACTION).padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The checkbox carries its own description: it has no label of its own, and the card
            // around it announces the edit action rather than this one.
            Checkbox(
                checked = routine.isDoneToday,
                onCheckedChange = { onEvent(RoutinesEvent.ToggleDone(routine.id, it)) },
                enabled = canToggle,
                modifier = Modifier.semantics { contentDescription = doneLabel },
            )
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(text = routine.routine.name, style = MaterialTheme.typography.itemTitle)
                routine.routine.description?.let {
                    Text(text = it, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
            IconButton(onClick = openEditor) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = editLabel)
            }
        }
    }
}

private fun RoutineMessage.toStringResource() = when (this) {
    RoutineMessage.LoadFailed -> Res.string.routines_error_load_failed
    RoutineMessage.SaveFailed -> Res.string.routines_error_save_failed
    RoutineMessage.DeleteFailed -> Res.string.routines_error_delete_failed
    RoutineMessage.ToggleFailed -> Res.string.routines_error_toggle_failed
    RoutineMessage.RoutineNoLongerExists -> Res.string.routines_error_routine_gone
}
