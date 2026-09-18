package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.invoke
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.gym.domain.GymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymExerciseDraft
import pl.lejdi.plannerkmp.feature.gym.domain.ObserveGymWeek
import pl.lejdi.plannerkmp.feature.gym.domain.ToggleExerciseSet

/**
 * The weekday pager's ViewModel.
 *
 * Depends on [GymDatasource] directly for the inline weight edit rather than on a one-line use
 * case wrapping it: changing a number carries no rule of its own, and the domain boundary is the
 * port in `domain/`, not a class per verb. The two operations that *do* carry rules — the week
 * window and its derived reset, and what tapping a series means — are use cases.
 */
class GymViewModel(
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val observeGymWeek: ObserveGymWeek,
    private val toggleExerciseSet: ToggleExerciseSet,
    private val gym: GymDatasource,
    private val todayProvider: TodayProvider,
) : RestorableViewModel<GymState, GymEvent, GymEffect, GymInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = GymInput.serializer(),
    logger = logger,
) {

    override fun createInitialState() = GymState(today = todayProvider.today().dayOfWeek)

    override fun captureInput(state: GymState) = GymInput(state.weightEditor)

    override fun applyInput(state: GymState, input: GymInput) =
        state.copy(weightEditor = input.weightEditor)

    init {
        observeWeek()
        observeToday()
    }

    override fun onEvent(event: GymEvent) {
        when (event) {
            is GymEvent.SerieToggled -> toggleSerie(event.exerciseId, event.serieNumber)
            is GymEvent.WeightEditStarted -> startWeightEdit(event.exerciseId)
            is GymEvent.WeightTextChanged -> setState {
                // Clearing the mark as soon as the text moves: leaving it set means the field goes
                // on looking wrong while the user is fixing it.
                copy(weightEditor = weightEditor?.copy(text = event.text, isInvalid = false))
            }
            is GymEvent.WeightEditCommitted -> commitWeightEdit()
            is GymEvent.WeightEditCancelled -> setState { copy(weightEditor = null) }
            is GymEvent.EditExerciseClicked ->
                sendEffect(GymEffect.NavigateToEditExercise(event.exerciseId))
            is GymEvent.AddExerciseClicked ->
                sendEffect(GymEffect.NavigateToAddExercise(event.dayOfWeek))
            is GymEvent.RetryClicked -> observeWeek()
            is GymEvent.MessageShown -> setState { copy(message = null) }
        }
    }

    /**
     * Subscribes to the week and stays subscribed: an exercise edited on the form screen, a tick
     * here, or the date rolling over re-emits on its own. Nothing re-queries on entry.
     *
     * Called again by [GymEvent.RetryClicked], because a database error terminates the upstream
     * flow and only a fresh subscription can recover.
     */
    private fun observeWeek() = observe(
        key = WEEK_KEY,
        source = observeGymWeek(),
        onData = { days -> copy(isLoading = false, loadFailed = false, days = days) },
        onError = { copy(isLoading = false, loadFailed = true, message = raise(GymMessage.LoadFailed)) },
        onStart = { copy(isLoading = true, loadFailed = false, message = null) },
    )

    /**
     * Keeps [GymState.today] current, so a screen left open overnight moves its "Today" marker to
     * the next page. Costs nothing: the provider shares one midnight timer across the whole app.
     */
    private fun observeToday() = observeValues(
        key = TODAY_KEY,
        source = todayProvider.todayFlow().map { it.dayOfWeek },
        onData = { weekday -> copy(today = weekday) },
    )

    /**
     * Resolved from the state the screen is observing rather than taken from the event, so the
     * count this tick is planned from is the one the data layer last emitted. A row that has gone
     * in the meantime simply has nothing to tick.
     */
    private fun toggleSerie(exerciseId: Long, serieNumber: Int) {
        val exercise = state.value.findExercise(exerciseId) ?: return
        write(exerciseId, GymMessage.ToggleFailed) {
            toggleExerciseSet(ToggleExerciseSet.Params(exercise, serieNumber))
        }
    }

    private fun startWeightEdit(exerciseId: Long) {
        val exercise = state.value.findExercise(exerciseId) ?: return
        setState {
            copy(
                weightEditor = WeightEditor(
                    exerciseId = exerciseId,
                    text = exercise.exercise.weight.toWeightText(),
                ),
            )
        }
    }

    /**
     * Three outcomes, and they are genuinely different.
     *
     * An empty field is not a rejected edit: it means "no weight", which is how a bodyweight
     * exercise is expressed. Text that is not a number, or a number outside the bounds the domain
     * accepts, leaves the editor open and marked rather than writing — and the bound is
     * [GymExerciseDraft.MAX_WEIGHT] rather than a second copy of the same figure, because two
     * copies of one rule is how the form and the list row start disagreeing about what is valid.
     */
    private fun commitWeightEdit() {
        val editor = state.value.weightEditor ?: return
        if (state.value.findExercise(editor.exerciseId) == null) {
            setState { copy(weightEditor = null) }
            return
        }
        val text = editor.text.trim()
        val weight = if (text.isEmpty()) null else text.toWeightOrNull()
        if (text.isNotEmpty() && (weight == null || weight !in 0.0..GymExerciseDraft.MAX_WEIGHT)) {
            setState {
                copy(
                    weightEditor = editor.copy(isInvalid = true),
                    message = raise(GymMessage.WeightInvalid),
                )
            }
            return
        }
        write(
            exerciseId = editor.exerciseId,
            failureMessage = GymMessage.WeightSaveFailed,
            onSuccess = { copy(weightEditor = null) },
        ) {
            gym.updateWeight(editor.exerciseId, weight)
        }
    }

    /**
     * The shape both writes on this screen share: guard re-entry per exercise, mark it in flight,
     * write, release it, then fold the outcome.
     *
     * Per exercise rather than per screen — a second tap on the same checkbox is the double tap
     * this guards against, while a tap on another row is the user working through the day. There is
     * no reload on success: the week flow re-emits by itself.
     */
    private fun write(
        exerciseId: Long,
        failureMessage: GymMessage,
        onSuccess: GymState.() -> GymState = { this },
        operation: suspend () -> AppResult<Unit>,
    ) {
        if (state.value.isPending(exerciseId)) return
        setState { copy(pendingExerciseIds = pendingExerciseIds + exerciseId) }
        viewModelScope.launch {
            val result = operation()
            setState { copy(pendingExerciseIds = pendingExerciseIds - exerciseId) }
            result.fold(
                onSuccess = { setState { onSuccess() } },
                onFailure = { error -> setState { copy(message = raise(error.toMessage(failureMessage))) } },
            )
        }
    }

    private companion object {
        const val WEEK_KEY = "week"
        const val TODAY_KEY = "today"
    }
}

/**
 * A write onto a row that no longer exists cannot be retried into working, so it gets its own
 * wording instead of a "failed" the user would keep pressing.
 */
private fun DomainError.toMessage(default: GymMessage): GymMessage = when (this) {
    is DomainError.NotFound -> GymMessage.ExerciseNoLongerExists
    else -> default
}
