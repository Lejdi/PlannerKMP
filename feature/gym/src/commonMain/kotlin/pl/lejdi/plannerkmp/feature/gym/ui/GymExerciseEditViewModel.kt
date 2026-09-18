package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.validationFields
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.gym.domain.GymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymExerciseDraft
import pl.lejdi.plannerkmp.feature.gym.domain.GymField

/**
 * Takes the exercise's *id*, not the exercise.
 *
 * Navigation carries an id so the back stack can be serialized and restored, and so the form is
 * built from the current stored row rather than from a snapshot taken whenever the user
 * long-pressed.
 *
 * Depends on [GymDatasource] rather than on three one-line use cases wrapping it: reading, adding,
 * editing and deleting an exercise carry no rule of their own, and the domain boundary is the port
 * living in `domain/`, not a class per verb.
 *
 * @param initialExerciseId seeds the state and nothing else. The id the screen *works from* is
 *   `state.exerciseId` throughout.
 * @param initialDayOfWeek the page a *new* exercise was added from, and null when editing an
 *   existing one — which takes its weekday from the row it loads, the only value that could be
 *   right after the exercise has been moved. Null rather than an arbitrary weekday nobody reads:
 *   the form's own default is then the single place a fallback lives.
 */
class GymExerciseEditViewModel(
    private val initialExerciseId: Long?,
    private val initialDayOfWeek: DayOfWeek?,
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val gym: GymDatasource,
) : RestorableViewModel<
    GymExerciseEditState,
    GymExerciseEditEvent,
    GymExerciseEditEffect,
    GymExerciseEditInput,
    >(
    savedStateHandle = savedStateHandle,
    inputSerializer = GymExerciseEditInput.serializer(),
    logger = logger,
) {

    override fun createInitialState() = GymExerciseEditState(
        isLoading = initialExerciseId != null,
        exerciseId = initialExerciseId,
        // Without a seed the form keeps its own default, which the stored row then overwrites.
        form = initialDayOfWeek?.let { GymExerciseForm(dayOfWeek = it) } ?: GymExerciseForm(),
    )

    override fun captureInput(state: GymExerciseEditState) = GymExerciseEditInput(
        exerciseId = state.exerciseId,
        isFormSeeded = state.isFormSeeded,
        form = state.form,
    )

    override fun applyInput(state: GymExerciseEditState, input: GymExerciseEditInput) = state.copy(
        // Not loading any more if the form already came back filled in — the user's own text wins
        // over a re-read, and the observation below keeps the row live from here on.
        isLoading = state.isLoading && !input.isFormSeeded,
        exerciseId = input.exerciseId ?: state.exerciseId,
        isFormSeeded = input.isFormSeeded,
        form = input.form,
    )

    init {
        if (state.value.exerciseId != null) observeExercise()
    }

    // An MVI dispatch table: its "complexity" is the number of events this screen has, one line
    // each, which is not the kind of complexity the rule is about. Suppressed here rather than by
    // raising the project threshold, so a genuinely branchy method elsewhere still trips it.
    @Suppress("CyclomaticComplexMethod")
    override fun onEvent(event: GymExerciseEditEvent) {
        when (event) {
            is GymExerciseEditEvent.NameChanged -> updateForm {
                copy(name = event.value).withoutError(GymField.Name)
            }
            is GymExerciseEditEvent.CommentChanged -> updateForm { copy(comment = event.value) }
            is GymExerciseEditEvent.DayOfWeekChanged -> updateForm { copy(dayOfWeek = event.value) }
            is GymExerciseEditEvent.SetsCountChanged -> updateForm {
                copy(setsCount = event.value).withoutError(GymField.Sets)
            }
            is GymExerciseEditEvent.RepsPerSetChanged -> updateForm {
                copy(repsPerSet = event.value).withoutError(GymField.Reps)
            }
            is GymExerciseEditEvent.WeightChanged -> updateForm {
                copy(weight = event.value).withoutError(GymField.Weight)
            }
            is GymExerciseEditEvent.DialogRequested -> updateForm { copy(activeDialog = event.dialog) }
            is GymExerciseEditEvent.DialogDismissed -> updateForm { copy(activeDialog = null) }
            is GymExerciseEditEvent.SaveClicked -> save()
            is GymExerciseEditEvent.DeleteClicked -> updateForm {
                copy(activeDialog = GymExerciseEditDialog.ConfirmDelete)
            }
            is GymExerciseEditEvent.DeleteConfirmed -> delete()
            is GymExerciseEditEvent.RetryClicked -> observeExercise()
            is GymExerciseEditEvent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun updateForm(change: GymExerciseForm.() -> GymExerciseForm) =
        setState { copy(form = form.change()) }

    /**
     * Holds the stored row live rather than reading it once.
     *
     * The form is seeded from the first emission only — later ones must not overwrite what the
     * user is in the middle of typing — but staying subscribed is what lets the screen notice the
     * row being deleted underneath it, which a one-shot read never could.
     */
    private fun observeExercise() {
        val id = state.value.exerciseId ?: return
        observe(
            source = gym.observeExercise(id),
            onStart = { copy(isLoading = !isFormSeeded, loadFailed = false, message = null) },
            onData = { exercise ->
                when {
                    exercise == null -> copy(isLoading = false)
                    isFormSeeded -> copy(isLoading = false, loadFailed = false)
                    else -> seededFrom(exercise)
                }
            },
            onError = {
                copy(
                    isLoading = false,
                    loadFailed = true,
                    message = raise(GymExerciseEditMessage.LoadFailed),
                )
            },
            // Not folded into onData: a reducer can be re-run under contention, and an effect
            // raised twice would pop two entries off the back stack.
            onEmission = { exercise ->
                if (exercise == null) sendEffect(GymExerciseEditEffect.NavigateBack)
            },
        )
    }

    private fun save() {
        val current = state.value
        // Re-entry guard. Save used to read the state, launch and return, so two taps landed two
        // inserts before the first had come back — the screen looked identical throughout.
        if (!current.canSubmit) return
        val form = current.form
        val weightText = form.weight.trim()
        val weight = if (weightText.isEmpty()) null else weightText.toWeightOrNull()

        // Weight is the one *optional* number on this form, so the domain cannot tell "absent"
        // from "unparseable" — both reach it as null, and an empty field legitimately means a
        // bodyweight exercise. That single rejection therefore has to be decided here; it is
        // merged with the domain's own findings rather than returned early, so one Save still
        // reports every bad input the way the rest of this app does.
        val refusedWeight =
            if (weightText.isNotEmpty() && weight == null) setOf(GymField.Weight) else emptySet()

        // Every other rule lives in the domain: this parses the text and routes the failures back
        // to the inputs that caused them.
        val draftResult = GymExerciseDraft.create(
            name = form.name,
            comment = form.comment,
            dayOfWeek = form.dayOfWeek,
            setsCount = form.setsCount.trim().toIntOrNull(),
            repsPerSet = form.repsPerSet.trim().toIntOrNull(),
            weight = weight,
        )

        val invalidFields = draftResult.offendingFields() + refusedWeight
        when {
            invalidFields.isNotEmpty() -> updateForm { copy(invalidFields = invalidFields) }
            // A failure naming no field of this screen's — which an open `ValidationField`
            // hierarchy permits — is a screen-level problem and says so.
            draftResult is AppResult.Failure ->
                setState { copy(message = raise(GymExerciseEditMessage.SaveFailed)) }
            draftResult is AppResult.Success -> submit(current.exerciseId, draftResult.data)
        }
    }

    private fun submit(existingId: Long?, draft: GymExerciseDraft) {
        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = if (existingId != null) {
                gym.updateDetails(existingId, draft)
            } else {
                gym.addExercise(draft)
            }
            setState { copy(isSubmitting = false) }
            result.fold(
                onSuccess = { sendEffect(GymExerciseEditEffect.NavigateBack) },
                onFailure = { error -> setState { copy(message = raise(error.toSaveMessage())) } },
            )
        }
    }

    private fun delete() {
        val current = state.value
        if (!current.canSubmit) return
        updateForm { copy(activeDialog = null) }

        val existingId = current.exerciseId
        if (existingId == null) {
            sendEffect(GymExerciseEditEffect.NavigateBack)
            return
        }

        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = gym.deleteExercise(existingId)
            setState { copy(isSubmitting = false) }
            result.fold(
                onSuccess = { sendEffect(GymExerciseEditEffect.NavigateBack) },
                onFailure = { error ->
                    // The row already being gone is the outcome delete wanted, not a failure to
                    // report — only a real malfunction keeps the user on the screen.
                    if (error is DomainError.NotFound) {
                        sendEffect(GymExerciseEditEffect.NavigateBack)
                    } else {
                        setState { copy(message = raise(GymExerciseEditMessage.DeleteFailed)) }
                    }
                },
            )
        }
    }
}

/** The inputs of *this* screen the domain blamed, or none when it did not fail. */
private fun AppResult<GymExerciseDraft>.offendingFields(): Set<GymField> =
    (this as? AppResult.Failure)?.error?.validationFields<GymField>().orEmpty()

/**
 * A save onto a row that no longer exists cannot be retried into working, so it gets its own
 * wording instead of a "save failed" the user would keep pressing.
 */
private fun DomainError.toSaveMessage(): GymExerciseEditMessage = when (this) {
    is DomainError.NotFound -> GymExerciseEditMessage.ExerciseNoLongerExists
    else -> GymExerciseEditMessage.SaveFailed
}

private fun GymExerciseEditState.seededFrom(exercise: GymExercise): GymExerciseEditState = copy(
    isLoading = false,
    loadFailed = false,
    isFormSeeded = true,
    exerciseId = exercise.id,
    form = form.copy(
        name = exercise.name,
        comment = exercise.comment.orEmpty(),
        dayOfWeek = exercise.dayOfWeek,
        setsCount = exercise.setsCount.toString(),
        repsPerSet = exercise.repsPerSet.toString(),
        weight = exercise.weight.toWeightText(),
        invalidFields = emptySet(),
    ),
)
