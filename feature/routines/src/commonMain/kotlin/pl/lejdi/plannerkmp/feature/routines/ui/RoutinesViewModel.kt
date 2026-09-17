package pl.lejdi.plannerkmp.feature.routines.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.invoke
import pl.lejdi.plannerkmp.core.common.onFailure
import pl.lejdi.plannerkmp.core.common.validationFields
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.routines.domain.ObserveRoutinesForToday
import pl.lejdi.plannerkmp.feature.routines.domain.RoutineDraft
import pl.lejdi.plannerkmp.feature.routines.domain.RoutineField
import pl.lejdi.plannerkmp.feature.routines.domain.RoutinesDatasource
import pl.lejdi.plannerkmp.feature.routines.domain.ToggleRoutineDone

/**
 * Takes [RoutinesDatasource] directly for adding, renaming and deleting: none of those carries a
 * rule a use case would hold, and the domain boundary is the port in `domain/`, not a class per
 * verb. The two operations that *do* carry one — what "done" means, and what the list looks like
 * today — arrive as use cases.
 */
class RoutinesViewModel(
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val observeRoutinesForToday: ObserveRoutinesForToday,
    private val toggleRoutineDone: ToggleRoutineDone,
    // Named `datasource`, not `routines`: the state's own list is called `routines`, and a property
    // of that name would be shadowed by the reducer's parameter in `observeRoutines()` below —
    // correct, but a reader has to work out which one each mention is.
    private val datasource: RoutinesDatasource,
) : RestorableViewModel<RoutinesState, RoutinesEvent, RoutinesEffect, RoutinesInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = RoutinesInput.serializer(),
    logger = logger,
) {

    override fun createInitialState() = RoutinesState()

    override fun captureInput(state: RoutinesState) = RoutinesInput(state.editor)

    override fun applyInput(state: RoutinesState, input: RoutinesInput) =
        state.copy(editor = input.editor)

    init {
        observeRoutines()
    }

    override fun onEvent(event: RoutinesEvent) {
        when (event) {
            is RoutinesEvent.ToggleDone -> toggleDone(event.id, event.done)
            is RoutinesEvent.EditorOpened -> openEditor(event.target)
            is RoutinesEvent.EditorNameChanged -> setState {
                copy(editor = editor?.copy(name = event.value, nameError = false))
            }
            is RoutinesEvent.EditorDescriptionChanged -> setState {
                copy(editor = editor?.copy(description = event.value))
            }
            is RoutinesEvent.EditorConfirmed -> confirmEditor()
            is RoutinesEvent.EditorCancelled -> setState { copy(editor = null) }
            is RoutinesEvent.DeleteRequested -> setState { copy(pendingDeletionId = event.id) }
            is RoutinesEvent.DeleteConfirmed -> confirmDeletion()
            is RoutinesEvent.DeleteCancelled -> setState { copy(pendingDeletionId = null) }
            is RoutinesEvent.RetryClicked -> observeRoutines()
            is RoutinesEvent.MessageShown -> setState { copy(message = null) }
        }
    }

    /** Stays subscribed: a write re-emits the list, and so does midnight. */
    private fun observeRoutines() = observe(
        source = observeRoutinesForToday(),
        onData = { routines -> copy(isLoading = false, loadFailed = false, routines = routines) },
        onError = { copy(isLoading = false, loadFailed = true, message = raise(RoutineMessage.LoadFailed)) },
        onStart = { copy(isLoading = true, loadFailed = false, message = null) },
    )

    private fun toggleDone(id: Long, done: Boolean) {
        val current = state.value
        // Per row: a second tap on the *same* row is the double-tap this guards against, while a
        // tap on a different row is the user working down their list.
        if (current.isToggling(id)) return
        if (current.routines.none { it.id == id }) return

        setState { copy(togglingRoutineIds = togglingRoutineIds + id) }
        viewModelScope.launch {
            val result = toggleRoutineDone(ToggleRoutineDone.Params(id = id, done = done))
            setState { copy(togglingRoutineIds = togglingRoutineIds - id) }
            result.onFailure { error ->
                // Already gone is the outcome the tap wanted; the list flow has re-emitted without
                // it, so there is nothing to tell the user.
                if (error !is DomainError.NotFound) {
                    setState { copy(message = raise(RoutineMessage.ToggleFailed)) }
                }
            }
        }
    }

    private fun openEditor(target: EditorTarget) {
        val existing = (target as? EditorTarget.Existing)
            ?.let { t -> state.value.routines.find { it.id == t.id } }
            ?.routine
        setState {
            copy(
                editor = RoutineEditor(
                    target = target,
                    name = existing?.name.orEmpty(),
                    description = existing?.description.orEmpty(),
                ),
            )
        }
    }

    private fun confirmEditor() {
        val current = state.value
        // Re-entry guard: without it a double tap on the tick added the same routine twice. Gated
        // on the editor alone, so a tick elsewhere in the list does not block a save.
        if (current.isEditorBusy) return
        val editor = current.editor ?: return

        val draft = when (val result = RoutineDraft.create(editor.name, editor.description)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                val badFields = result.error.validationFields<RoutineField>()
                setState {
                    if (RoutineField.Name in badFields) {
                        copy(editor = this.editor?.copy(nameError = true))
                    } else {
                        copy(message = raise(RoutineMessage.SaveFailed))
                    }
                }
                return
            }
        }

        setState { copy(isEditorBusy = true) }
        viewModelScope.launch {
            val result = when (val target = editor.target) {
                is EditorTarget.New -> datasource.addRoutine(draft)
                // updateDetails, not a whole-row write: the tick this row may have received while
                // the editor was open is not in `draft`, and must not be overwritten by its absence.
                is EditorTarget.Existing -> datasource.updateDetails(target.id, draft)
            }
            setState { copy(isEditorBusy = false) }
            result.fold(
                onSuccess = { setState { copy(editor = null) } },
                onFailure = { error ->
                    // A vanished row cannot be saved onto, and retrying will not change that, so
                    // the editor closes and the message says what actually happened.
                    if (error is DomainError.NotFound) {
                        setState { copy(editor = null, message = raise(RoutineMessage.RoutineNoLongerExists)) }
                    } else {
                        setState { copy(message = raise(RoutineMessage.SaveFailed)) }
                    }
                },
            )
        }
    }

    private fun confirmDeletion() {
        val current = state.value
        if (current.isEditorBusy) return
        val id = current.pendingDeletionId ?: return

        setState { copy(pendingDeletionId = null, isEditorBusy = true) }
        viewModelScope.launch {
            val result = datasource.deleteRoutine(id)
            setState { copy(isEditorBusy = false) }
            result.fold(
                // Nothing left to edit, so the editor goes with the row.
                onSuccess = { setState { copy(editor = null) } },
                onFailure = { error ->
                    if (error is DomainError.NotFound) {
                        setState { copy(editor = null) }
                    } else {
                        setState { copy(message = raise(RoutineMessage.DeleteFailed)) }
                    }
                },
            )
        }
    }
}
