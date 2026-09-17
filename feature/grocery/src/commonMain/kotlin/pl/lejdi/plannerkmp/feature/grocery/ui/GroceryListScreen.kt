package pl.lejdi.plannerkmp.feature.grocery.ui

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.components.PlainTextField
import pl.lejdi.plannerkmp.core.ui.resources.core_action_cancel
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.resources.core_action_undo
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.core.ui.theme.itemTitle
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import pl.lejdi.plannerkmp.feature.grocery.resources.Res
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_action_confirm
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_add_item
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_complete_item
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_edit_item
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_empty_list
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_error_delete_failed
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_error_item_gone
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_error_load_failed
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_error_save_failed
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_field_description
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_field_name
import pl.lejdi.plannerkmp.feature.grocery.resources.grocery_item_completed
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

@Composable
fun GroceryListScreen(viewModel: GroceryListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(GroceryListEvent.MessageShown) },
        // The same snackbar slot carries two different actions, chosen by the message: a retry for
        // a failed load, and an undo for the completion that just removed a row.
        actionLabel = when (message?.value) {
            GroceryMessage.ItemCompleted -> stringResource(CoreRes.string.core_action_undo)
            else -> stringResource(CoreRes.string.core_action_retry)
        },
        onAction = when (message?.value) {
            GroceryMessage.LoadFailed -> {
                { viewModel.onEvent(GroceryListEvent.RetryClicked) }
            }
            GroceryMessage.ItemCompleted -> {
                { viewModel.onEvent(GroceryListEvent.UndoCompleteClicked) }
            }
            else -> null
        },
        suppressed = state.hasTerminalLoadFailure,
    )

    GroceryListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

/** Stateless: takes the state and one event sink, never the ViewModel. */
@Composable
internal fun GroceryListContent(
    state: GroceryListState,
    snackbarHostState: SnackbarHostState,
    onEvent: (GroceryListEvent) -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.grocery_error_load_failed),
            onRetry = { onEvent(GroceryListEvent.RetryClicked) },
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "top-spacer") { Spacer(modifier = Modifier.height(Spacing.lg)) }

                    if (state.items.isEmpty() && state.editor?.target != EditorTarget.New) {
                        item(key = "empty-state") {
                            Text(
                                text = stringResource(Res.string.grocery_empty_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.xl, horizontal = Spacing.lg),
                            )
                        }
                    }

                    items(state.items, key = { it.id }) { item ->
                        val editor = state.editor
                        if (editor != null && editor.target == EditorTarget.Existing(item.id)) {
                            InlineEditCard(
                                editor = editor,
                                canSubmit = !state.isEditorBusy,
                                onEvent = onEvent,
                            )
                        } else {
                            GroceryRow(
                                item = item,
                                // This row only, so completing one item leaves the rest tappable.
                                canSubmit = !state.isCompleting(item.id),
                                onEvent = onEvent,
                            )
                        }
                    }

                    // Keyed: without one the trailing item's identity is its index, so it is
                    // disposed and rebuilt whenever the list length changes — and it hosts the
                    // "new item" editor, whose text fields would lose focus and dismiss the IME.
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
    }
}

@Composable
private fun AddControl(
    editor: GroceryEditor?,
    canSubmit: Boolean,
    onEvent: (GroceryListEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.lg).animateContentSize()) {
        if (editor != null && editor.target == EditorTarget.New) {
            InlineEditCard(editor = editor, canSubmit = canSubmit, onEvent = onEvent)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingActionButton(
                    onClick = { onEvent(GroceryListEvent.EditorOpened(EditorTarget.New)) },
                    modifier = Modifier.padding(Spacing.lg),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.grocery_add_item))
                }
            }
        }
    }
}

@Composable
private fun InlineEditCard(
    editor: GroceryEditor,
    canSubmit: Boolean,
    onEvent: (GroceryListEvent) -> Unit,
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
                    onValueChange = { onEvent(GroceryListEvent.EditorNameChanged(it)) },
                    textStyle = MaterialTheme.typography.itemTitle,
                    placeholder = stringResource(Res.string.grocery_field_name),
                    isError = editor.nameError,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                PlainTextField(
                    value = editor.description,
                    onValueChange = { onEvent(GroceryListEvent.EditorDescriptionChanged(it)) },
                    textStyle = LocalTextStyle.current,
                    placeholder = stringResource(Res.string.grocery_field_description),
                    singleLine = false,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onEvent(GroceryListEvent.EditorConfirmed) },
                    enabled = canSubmit,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(Res.string.grocery_action_confirm),
                    )
                }
                TextButton(onClick = { onEvent(GroceryListEvent.EditorCancelled) }) {
                    Text(stringResource(CoreRes.string.core_action_cancel))
                }
            }
        }
    }
}

/**
 * A row, with editing reachable two ways.
 *
 * Editing used to be a long-press and nothing else, on a card whose `onClick` was an empty lambda:
 * an affordance with no visual sign that it existed, no way to discover it, and — because a long
 * press carries no announced action — no way for a screen-reader user to reach it at all. The
 * long press stays, now labelled so assistive technology can offer it, and an explicit button
 * makes it visible for everyone else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroceryRow(item: GroceryItem, canSubmit: Boolean, onEvent: (GroceryListEvent) -> Unit) {
    val editLabel = stringResource(Res.string.grocery_edit_item)
    val openEditor = { onEvent(GroceryListEvent.EditorOpened(EditorTarget.Existing(item.id))) }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.itemTitle)
                item.description?.let { Text(text = it, modifier = Modifier.padding(top = Spacing.xs)) }
            }
            IconButton(onClick = openEditor) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = editLabel)
            }
            IconButton(
                onClick = { onEvent(GroceryListEvent.CompleteItem(item.id)) },
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(Res.string.grocery_complete_item),
                )
            }
        }
    }
}

private fun GroceryMessage.toStringResource() = when (this) {
    GroceryMessage.ItemCompleted -> Res.string.grocery_item_completed
    GroceryMessage.LoadFailed -> Res.string.grocery_error_load_failed
    GroceryMessage.SaveFailed -> Res.string.grocery_error_save_failed
    GroceryMessage.DeleteFailed -> Res.string.grocery_error_delete_failed
    GroceryMessage.ItemNoLongerExists -> Res.string.grocery_error_item_gone
}
