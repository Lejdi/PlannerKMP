package pl.lejdi.plannerkmp.feature.gym.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.gym.domain.GymField

/**
 * Everything the user typed or chose on this screen, in one object.
 *
 * Nested rather than spread flat across [GymExerciseEditState] and restated in
 * [GymExerciseEditInput], for the reason `TaskForm` documents: a flat form meant the same fields
 * listed in five places, every one with a default, so **adding a field compiled cleanly while
 * silently not surviving a restart**. Nesting makes the restorable slice *be* this object, so
 * capture and apply are one line each.
 *
 * [invalidFields] rides along deliberately. A failed Save marks its fields, and a user who
 * backgrounds the app at that moment should come back to the same marks rather than to a form that
 * looks fine and fails again the instant they press Save.
 */
@Serializable
data class GymExerciseForm(
    val name: String = "",
    val comment: String = "",
    @Serializable(with = DayOfWeekSerializer::class)
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Kept as text, not numbers: a field is mid-edit for as long as the user is typing in it. */
    val setsCount: String = "",
    val repsPerSet: String = "",
    val weight: String = "",
    val activeDialog: GymExerciseEditDialog? = null,
    val invalidFields: Set<GymField> = emptySet(),
) {
    /** Clears the mark on one input, for the moment the user starts correcting it. */
    fun withoutError(field: GymField): GymExerciseForm =
        if (field in invalidFields) copy(invalidFields = invalidFields - field) else this

    operator fun contains(field: GymField): Boolean = field in invalidFields
}

/**
 * [GymExerciseForm.activeDialog] is part of the state rather than a `remember { mutableStateOf }`
 * in the composable: MVI owns the screen's state, and a dialog left open across a rotation should
 * survive with the form it belongs to.
 */
data class GymExerciseEditState(
    override val isLoading: Boolean = false,
    override val loadFailed: Boolean = false,
    override val isSubmitting: Boolean = false,
    val exerciseId: Long? = null,
    /**
     * Whether the stored exercise has been read into the form yet.
     *
     * This is what makes [isEmpty] true after a failed load, and so what stops the screen
     * rendering a blank but fully live form over an exercise it never managed to read — a Save
     * from that state would overwrite the real row with empty fields.
     */
    val isFormSeeded: Boolean = false,
    val form: GymExerciseForm = GymExerciseForm(),
    override val message: UiMessage<GymExerciseEditMessage>? = null,
) : LoadableState<GymExerciseEditMessage> {

    val isEditingExisting: Boolean get() = exerciseId != null

    /** A new exercise starts with an empty form on purpose; only an unread *existing* one is empty. */
    override val isEmpty: Boolean get() = isEditingExisting && !isFormSeeded

    /** No Save or Delete while one is already in flight, and none over a form that never loaded. */
    val canSubmit: Boolean get() = !isSubmitting && !hasTerminalLoadFailure
}

/**
 * The restorable slice: the form, plus the two facts that say which row it belongs to and whether
 * it has been filled from that row yet.
 *
 * Loaded data is not here and never should be — it is in the database and re-emits by itself.
 */
@Serializable
data class GymExerciseEditInput(
    val exerciseId: Long? = null,
    val isFormSeeded: Boolean = false,
    val form: GymExerciseForm = GymExerciseForm(),
)

@Serializable
enum class GymExerciseEditDialog {
    /**
     * Delete asks first.
     *
     * It is the one irreversible thing this screen can do and it sits next to Save — the same
     * reason `:feature:tasks` puts its delete behind a confirmation, and the reason this feature
     * does not copy grocery's undo snackbar: removing an exercise from a plan is rare and
     * deliberate, not a tap made forty times a trip.
     */
    ConfirmDelete,
}

enum class GymExerciseEditMessage {
    LoadFailed,
    SaveFailed,
    DeleteFailed,

    /** The row was deleted while this screen had it open, so there is nothing left to save onto. */
    ExerciseNoLongerExists,
}

sealed interface GymExerciseEditEvent : MviEvent {
    data class NameChanged(val value: String) : GymExerciseEditEvent
    data class CommentChanged(val value: String) : GymExerciseEditEvent
    data class DayOfWeekChanged(val value: DayOfWeek) : GymExerciseEditEvent
    data class SetsCountChanged(val value: String) : GymExerciseEditEvent
    data class RepsPerSetChanged(val value: String) : GymExerciseEditEvent
    data class WeightChanged(val value: String) : GymExerciseEditEvent
    data class DialogRequested(val dialog: GymExerciseEditDialog) : GymExerciseEditEvent
    data object DialogDismissed : GymExerciseEditEvent
    data object SaveClicked : GymExerciseEditEvent

    /** Opens the confirmation; [DeleteConfirmed] is what actually deletes. */
    data object DeleteClicked : GymExerciseEditEvent
    data object DeleteConfirmed : GymExerciseEditEvent

    /** Re-subscribes after a failed load, so the full-screen error can offer a retry. */
    data object RetryClicked : GymExerciseEditEvent
    data object MessageShown : GymExerciseEditEvent
}

sealed interface GymExerciseEditEffect : MviEffect {
    data object NavigateBack : GymExerciseEditEffect
}
