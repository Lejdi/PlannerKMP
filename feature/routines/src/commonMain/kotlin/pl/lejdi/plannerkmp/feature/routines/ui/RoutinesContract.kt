package pl.lejdi.plannerkmp.feature.routines.ui

import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.routines.domain.TodayRoutine

data class RoutinesState(
    override val isLoading: Boolean = true,
    override val loadFailed: Boolean = false,
    /**
     * The routines whose tick is in flight, rather than one flag for the whole screen.
     *
     * Ticking is a tap per row and working down the list is how this screen is meant to be used, so
     * a screen-wide flag made the second tap land while the first write was still open: the guard
     * dropped it silently and the row's checkbox was disabled for a write that had nothing to do
     * with it.
     */
    val togglingRoutineIds: Set<Long> = emptySet(),
    /** True while the editor's save, or a delete, is in flight. Only one of those runs at a time. */
    val isEditorBusy: Boolean = false,
    val routines: List<TodayRoutine> = emptyList(),
    val editor: RoutineEditor? = null,
    /** The routine the confirmation dialog is about, or null when no dialog is up. */
    val pendingDeletionId: Long? = null,
    override val message: UiMessage<RoutineMessage>? = null,
) : LoadableState<RoutineMessage> {
    override val isEmpty: Boolean get() = routines.isEmpty()

    override val isSubmitting: Boolean get() = isEditorBusy || togglingRoutineIds.isNotEmpty()

    /** Whether *this* row has a write open — the only thing that should disable *this* row. */
    fun isToggling(id: Long): Boolean = id in togglingRoutineIds
}

/**
 * One editor rather than parallel add- and edit-shaped fields: adding and editing are the same
 * form with a different target, and modelling them twice means every change has to be made twice.
 */
@Serializable
data class RoutineEditor(
    val target: EditorTarget,
    val name: String = "",
    val description: String = "",
    val nameError: Boolean = false,
)

@Serializable
sealed interface EditorTarget {
    @Serializable
    data object New : EditorTarget

    @Serializable
    data class Existing(val id: Long) : EditorTarget
}

/**
 * The restorable slice of [RoutinesState].
 *
 * The open editor is the only thing on this screen the user typed; the list is in the database and
 * re-emits by itself. [RoutinesState.pendingDeletionId] is deliberately absent: a destructive
 * confirmation that comes back after process death, under a thumb already moving towards where the
 * confirm button was, is worse than one that is simply gone.
 */
@Serializable
data class RoutinesInput(val editor: RoutineEditor? = null)

enum class RoutineMessage {
    LoadFailed,
    SaveFailed,
    DeleteFailed,

    /** A tick or un-tick could not be written. */
    ToggleFailed,

    /** The row was deleted elsewhere while the editor had it open. */
    RoutineNoLongerExists,
}

sealed interface RoutinesEvent : MviEvent {
    /** Ids, never entities: a whole routine here would act on the snapshot drawn with the row. */
    data class ToggleDone(val id: Long, val done: Boolean) : RoutinesEvent
    data class EditorOpened(val target: EditorTarget) : RoutinesEvent
    data class EditorNameChanged(val value: String) : RoutinesEvent
    data class EditorDescriptionChanged(val value: String) : RoutinesEvent
    data object EditorConfirmed : RoutinesEvent
    data object EditorCancelled : RoutinesEvent
    data class DeleteRequested(val id: Long) : RoutinesEvent
    data object DeleteConfirmed : RoutinesEvent
    data object DeleteCancelled : RoutinesEvent
    data object RetryClicked : RoutinesEvent
    data object MessageShown : RoutinesEvent
}

/**
 * This screen raises no one-shot events: everything it shows is in [RoutinesState].
 *
 * [BaseViewModel][pl.lejdi.plannerkmp.core.mvi.BaseViewModel] is typed on an effect type, so the
 * screen needs one to name — but an *uninhabited* type says what this one says, and says it to the
 * compiler: `sendEffect` cannot be called, because there is no value to call it with. The screen
 * correspondingly does not collect the effect flow.
 */
sealed interface RoutinesEffect : MviEffect
