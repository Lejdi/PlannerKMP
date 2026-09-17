package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.validationFields
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryField
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemDraft
import pl.lejdi.plannerkmp.feature.grocery.domain.withId

/**
 * Depends on [GroceryDatasource] directly: none of this screen's operations carry a rule that a use
 * case would hold, and the domain boundary is the port living in `domain/`, not a class per verb.
 */
class GroceryListViewModel(
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val groceries: GroceryDatasource,
) : RestorableViewModel<GroceryListState, GroceryListEvent, GroceryListEffect, GroceryListInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = GroceryListInput.serializer(),
    logger = logger,
) {

    override fun createInitialState() = GroceryListState()

    override fun captureInput(state: GroceryListState) = GroceryListInput(state.editor)

    override fun applyInput(state: GroceryListState, input: GroceryListInput) =
        state.copy(editor = input.editor)

    init {
        observeItems()
    }

    override fun onEvent(event: GroceryListEvent) {
        when (event) {
            is GroceryListEvent.EditorOpened -> openEditor(event.target)
            is GroceryListEvent.EditorNameChanged -> setState {
                copy(editor = editor?.copy(name = event.value, nameError = false))
            }
            is GroceryListEvent.EditorDescriptionChanged -> setState {
                copy(editor = editor?.copy(description = event.value))
            }
            is GroceryListEvent.EditorConfirmed -> confirmEditor()
            is GroceryListEvent.EditorCancelled -> setState { copy(editor = null) }
            is GroceryListEvent.CompleteItem -> completeItem(event.id)
            is GroceryListEvent.UndoCompleteClicked -> undoCompletion()
            is GroceryListEvent.RetryClicked -> observeItems()
            // Clearing the message drops the undo with it: the offer lasts exactly as long as the
            // snackbar that carries it, so there is never an Undo with nothing behind it.
            is GroceryListEvent.MessageShown -> setState { copy(message = null, undoableDeletion = null) }
        }
    }

    private fun openEditor(target: EditorTarget) {
        val existing = (target as? EditorTarget.Existing)?.let { t -> state.value.items.find { it.id == t.id } }
        setState {
            copy(
                editor = GroceryEditor(
                    target = target,
                    name = existing?.name.orEmpty(),
                    description = existing?.description.orEmpty(),
                ),
            )
        }
    }

    /** Stays subscribed: a write re-emits the list, so nothing here re-reads after a mutation. */
    private fun observeItems() = observe(
        source = groceries.observeItems(),
        onData = { items -> copy(isLoading = false, loadFailed = false, items = items) },
        onError = { copy(isLoading = false, loadFailed = true, message = raise(GroceryMessage.LoadFailed)) },
        onStart = { copy(isLoading = true, loadFailed = false, message = null) },
    )

    private fun confirmEditor() {
        val current = state.value
        // Re-entry guard: confirming used to launch and return, so a double tap on the tick added
        // the same item twice. Gated on the editor alone, so a completion elsewhere in the list
        // does not block a save that has nothing to do with it.
        if (current.isEditorBusy) return
        val editor = current.editor ?: return

        val draftResult = GroceryItemDraft.create(editor.name, editor.description)
        val draft = when (draftResult) {
            is AppResult.Success -> draftResult.data
            is AppResult.Failure -> {
                // One question instead of the `as? Validation`/`== GroceryField.Name` pair, and it
                // reports every field the domain objected to rather than only the first.
                val badFields = draftResult.error.validationFields<GroceryField>()
                setState {
                    if (GroceryField.Name in badFields) {
                        copy(editor = this.editor?.copy(nameError = true))
                    } else {
                        copy(message = raise(GroceryMessage.SaveFailed))
                    }
                }
                return
            }
        }

        setState { copy(isEditorBusy = true) }
        viewModelScope.launch {
            val target = editor.target
            val result = when (target) {
                is EditorTarget.New -> groceries.addItem(draft)
                is EditorTarget.Existing -> groceries.editItem(draft.withId(target.id))
            }
            setState { copy(isEditorBusy = false) }
            result.fold(
                onSuccess = { setState { copy(editor = null) } },
                onFailure = { error ->
                    // A vanished row cannot be saved onto, and retrying will not change that, so
                    // the editor closes and the message says what actually happened.
                    if (error is DomainError.NotFound) {
                        setState { copy(editor = null, message = raise(GroceryMessage.ItemNoLongerExists)) }
                    } else {
                        setState { copy(message = raise(GroceryMessage.SaveFailed)) }
                    }
                },
            )
        }
    }

    /**
     * Removes the item and offers it back.
     *
     * The item is captured *before* the delete, because after it the row is gone from the list flow
     * and there is nothing left to reconstruct it from.
     */
    private fun completeItem(id: Long) {
        val current = state.value
        // Per item: a second tap on the *same* row is the double-tap this guards against, while a
        // tap on a different row is the user working down their shopping list.
        if (current.isCompleting(id)) return
        val item = current.items.find { it.id == id } ?: return

        setState { copy(completingItemIds = completingItemIds + id) }
        viewModelScope.launch {
            val result = groceries.deleteItem(id)
            setState { copy(completingItemIds = completingItemIds - id) }
            result.fold(
                onSuccess = {
                    setState {
                        copy(undoableDeletion = item, message = raise(GroceryMessage.ItemCompleted))
                    }
                },
                onFailure = { error ->
                    // Already gone is the outcome the tap wanted; the list flow has re-emitted it.
                    if (error !is DomainError.NotFound) {
                        setState { copy(message = raise(GroceryMessage.DeleteFailed)) }
                    }
                },
            )
        }
    }

    /**
     * Puts a completed item back.
     *
     * It comes back with a new id, because the row it had is gone and SQLite hands out the next one
     * — which for a shopping list is immaterial: nothing references an item by id but the list
     * itself, and what the user asked for was the *item* back, not the row.
     */
    private fun undoCompletion() {
        val current = state.value
        if (current.isEditorBusy) return
        val item = current.undoableDeletion ?: return

        setState { copy(isEditorBusy = true, undoableDeletion = null, message = null) }
        viewModelScope.launch {
            val result = groceries.addItem(GroceryItemDraft.ofStored(item.name, item.description))
            setState { copy(isEditorBusy = false) }
            result.fold(
                onSuccess = { },
                onFailure = { setState { copy(message = raise(GroceryMessage.SaveFailed)) } },
            )
        }
    }
}
