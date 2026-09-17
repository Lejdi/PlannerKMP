package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskField
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

/**
 * Everything the user typed or chose on this screen, in one object.
 *
 * This used to be ten fields spread flat across [TaskEditState], restated in `TaskEditInput`, and
 * copied between the two by hand in `captureInput` and `applyInput` — plus once more in
 * `seededFrom`. Five places, every one of them with a default value, so **adding a field compiled
 * cleanly while silently not surviving a restart**: the compiler had nothing to object to. Nesting
 * the form makes the restorable slice *be* this object, so capture and apply are one line each and
 * there is one list of fields to add to.
 *
 * [invalidFields] rides along deliberately. A failed Save marks its fields, and a user who
 * backgrounds the app at that moment should come back to the same marks rather than to a form that
 * looks fine and fails again the instant they press Save.
 */
@Serializable
data class TaskForm(
    val name: String = "",
    val description: String = "",
    val type: TaskType = TaskType.Asap,
    /** Null until the user picks one; the screen falls back to today. See [TaskEditState.effectiveStartDate]. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val hour: LocalTime? = null,
    /** Kept as text, not an Int: the field is mid-edit for as long as the user is typing in it. */
    val daysInterval: String = "",
    val activeDialog: TaskEditDialog? = null,
    val invalidFields: Set<TaskField> = emptySet(),
) {
    /** Clears the mark on one input, for the moment the user starts correcting it. */
    fun withoutError(field: TaskField): TaskForm =
        if (field in invalidFields) copy(invalidFields = invalidFields - field) else this

    operator fun contains(field: TaskField): Boolean = field in invalidFields
}

/**
 * [activeDialog] is part of the state rather than several `remember { mutableStateOf(false) }`
 * flags in the composable: MVI owns the screen's state, and a dialog left open across a rotation
 * should survive with the form it belongs to.
 */
data class TaskEditState(
    override val isLoading: Boolean = false,
    override val loadFailed: Boolean = false,
    override val isSubmitting: Boolean = false,
    val taskId: Long? = null,
    /**
     * Whether the stored task has been read into the form yet.
     *
     * This is what makes [isEmpty] true after a failed load, and so what stops the screen
     * rendering a blank but fully live form over a task it never managed to read — a Save from
     * that state used to overwrite the real row with empty fields.
     */
    val isFormSeeded: Boolean = false,
    val form: TaskForm = TaskForm(),
    /**
     * Today, kept live rather than read once at construction.
     *
     * The default start date of a new task is today, and this screen used to capture it with a
     * one-shot `todayProvider.today()` in `createInitialState`. A form left open across midnight
     * then defaulted to yesterday — the one rule this app is otherwise emphatic about, applied
     * everywhere except here.
     */
    val today: LocalDate,
    override val message: UiMessage<TaskEditMessage>? = null,
) : LoadableState<TaskEditMessage> {

    val isEditingExistingTask: Boolean get() = taskId != null

    /** A new task starts with an empty form on purpose; only an unread *existing* task is empty. */
    override val isEmpty: Boolean get() = isEditingExistingTask && !isFormSeeded

    /** The date the schedule is anchored at: what the user picked, or today if they have not. */
    val effectiveStartDate: LocalDate get() = form.startDate ?: today

    /** No Save or Delete while one is already in flight, and none over a form that never loaded. */
    val canSubmit: Boolean get() = !isSubmitting && !hasTerminalLoadFailure
}

/**
 * The restorable slice of [TaskEditState]: the form, plus the two facts that say which row it
 * belongs to and whether it has been filled from that row yet.
 *
 * Loaded data is not here and never should be — it is in the database and re-emits by itself.
 * [today] is not here either: re-reading the clock is always correct and cannot go stale.
 */
@Serializable
data class TaskEditInput(
    val taskId: Long? = null,
    val isFormSeeded: Boolean = false,
    val form: TaskForm = TaskForm(),
)

@Serializable
enum class TaskEditDialog {
    StartDate,
    EndDate,
    Hour,

    /**
     * Delete asks first.
     *
     * It was a single tap that destroyed a task with no confirmation and no way back — the one
     * irreversible thing this screen can do, and the easiest to hit by accident next to Save.
     */
    ConfirmDelete,
}

enum class TaskEditMessage {
    LoadFailed,
    SaveFailed,
    DeleteFailed,

    /** The row was deleted while this screen had it open, so there is nothing left to save onto. */
    TaskNoLongerExists,
}

sealed interface TaskEditEvent : MviEvent {
    data class NameChanged(val value: String) : TaskEditEvent
    data class DescriptionChanged(val value: String) : TaskEditEvent
    data class TypeChanged(val value: TaskType) : TaskEditEvent
    data class StartDateChanged(val value: LocalDate) : TaskEditEvent
    data class EndDateChanged(val value: LocalDate) : TaskEditEvent
    data class HourChanged(val value: LocalTime?) : TaskEditEvent
    data class DaysIntervalChanged(val value: String) : TaskEditEvent
    data class DialogRequested(val dialog: TaskEditDialog) : TaskEditEvent
    data object DialogDismissed : TaskEditEvent
    data object SaveClicked : TaskEditEvent

    /** Opens the confirmation; [DeleteConfirmed] is what actually deletes. */
    data object DeleteClicked : TaskEditEvent
    data object DeleteConfirmed : TaskEditEvent

    /** Re-subscribes after a failed load, so the full-screen error can offer a retry. */
    data object RetryClicked : TaskEditEvent
    data object MessageShown : TaskEditEvent
}

sealed interface TaskEditEffect : MviEffect {
    data object NavigateBack : TaskEditEffect
}
