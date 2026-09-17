package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.validationFields
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskDraft
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskField
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.type
import pl.lejdi.plannerkmp.feature.tasks.domain.withId

/**
 * Takes the task's *id*, not the task.
 *
 * Navigation carries an id so the back stack can be serialized and restored, and so the form is
 * built from the current stored row rather than from a snapshot taken whenever the user tapped.
 *
 * Depends on [TasksDatasource] rather than on four one-line use cases wrapping it: reading, adding,
 * editing and deleting a task carry no rule of their own, and the domain boundary is the port
 * living in `domain/`, not a class per verb.
 *
 * @param initialTaskId seeds the state and nothing else. The id the screen *works from* is
 *   `state.taskId` throughout — this used to be read here in some methods and from the state in
 *   others, two sources for one fact that every method had to pick between. The one place the two
 *   still meet is [applyInput], where a restored input and a freshly seeded state each carry a
 *   copy; see there for why the restored one wins.
 */
class TaskEditViewModel(
    private val initialTaskId: Long?,
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val tasks: TasksDatasource,
    private val todayProvider: TodayProvider,
) : RestorableViewModel<TaskEditState, TaskEditEvent, TaskEditEffect, TaskEditInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = TaskEditInput.serializer(),
    logger = logger,
) {

    override fun createInitialState(): TaskEditState = TaskEditState(
        isLoading = initialTaskId != null,
        taskId = initialTaskId,
        today = todayProvider.today(),
    )

    override fun captureInput(state: TaskEditState) = TaskEditInput(
        taskId = state.taskId,
        isFormSeeded = state.isFormSeeded,
        form = state.form,
    )

    override fun applyInput(state: TaskEditState, input: TaskEditInput) = state.copy(
        // Not loading any more if the form already came back filled in — the user's own text wins
        // over a re-read, and the observation below keeps the row live from here on.
        isLoading = state.isLoading && !input.isFormSeeded,
        // The restored id wins, with the seeded one as the fallback. They cannot currently
        // disagree — `captureInput` only ever writes back what the nav key put there — so this is
        // the ordering an id that *could* move (a draft that acquires one on first save) would
        // need, not a reconciliation of a live split.
        taskId = input.taskId ?: state.taskId,
        isFormSeeded = input.isFormSeeded,
        form = input.form,
    )

    init {
        observeToday()
        if (state.value.taskId != null) observeTask()
    }

    // An MVI dispatch table: its "complexity" is the number of events this screen has, one line
    // each, which is not the kind of complexity the rule is about. Suppressed here rather than by
    // raising the project threshold, so a genuinely branchy method elsewhere still trips it.
    @Suppress("CyclomaticComplexMethod")
    override fun onEvent(event: TaskEditEvent) {
        when (event) {
            is TaskEditEvent.NameChanged -> updateForm {
                copy(name = event.value).withoutError(TaskField.Name)
            }
            is TaskEditEvent.DescriptionChanged -> updateForm { copy(description = event.value) }
            is TaskEditEvent.TypeChanged -> updateForm { copy(type = event.value) }
            is TaskEditEvent.StartDateChanged -> updateForm {
                // An end date that now precedes the start is dragged along rather than left behind
                // to fail validation the user cannot see the cause of.
                val snappedEnd = endDate?.takeIf { it >= event.value } ?: event.value
                copy(startDate = event.value, endDate = snappedEnd).withoutError(TaskField.EndDate)
            }
            is TaskEditEvent.EndDateChanged -> updateForm {
                copy(endDate = event.value).withoutError(TaskField.EndDate)
            }
            is TaskEditEvent.HourChanged -> updateForm { copy(hour = event.value) }
            is TaskEditEvent.DaysIntervalChanged -> updateForm {
                copy(daysInterval = event.value).withoutError(TaskField.DaysInterval)
            }
            is TaskEditEvent.DialogRequested -> updateForm { copy(activeDialog = event.dialog) }
            is TaskEditEvent.DialogDismissed -> updateForm { copy(activeDialog = null) }
            is TaskEditEvent.SaveClicked -> save()
            is TaskEditEvent.DeleteClicked -> updateForm { copy(activeDialog = TaskEditDialog.ConfirmDelete) }
            is TaskEditEvent.DeleteConfirmed -> delete()
            is TaskEditEvent.RetryClicked -> observeTask()
            is TaskEditEvent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun updateForm(change: TaskForm.() -> TaskForm) = setState { copy(form = form.change()) }

    /**
     * Keeps [TaskEditState.today] current, so a form left open overnight still defaults to the
     * right day. Costs nothing: the provider shares one midnight timer across the whole app.
     */
    private fun observeToday() = observeValues(
        key = TODAY_KEY,
        source = todayProvider.todayFlow(),
        onData = { today -> copy(today = today) },
    )

    /**
     * Holds the stored row live rather than reading it once.
     *
     * The form is seeded from the first emission only — later ones must not overwrite what the
     * user is in the middle of typing — but staying subscribed is what lets the screen notice the
     * row being deleted underneath it, which a one-shot read never could.
     */
    private fun observeTask() {
        val id = state.value.taskId ?: return
        observe(
            key = TASK_KEY,
            source = tasks.observeTask(id),
            onStart = { copy(isLoading = !isFormSeeded, loadFailed = false, message = null) },
            onData = { task ->
                when {
                    task == null -> copy(isLoading = false)
                    isFormSeeded -> copy(isLoading = false, loadFailed = false)
                    else -> seededFrom(task)
                }
            },
            onError = { copy(isLoading = false, loadFailed = true, message = raise(TaskEditMessage.LoadFailed)) },
            // Not folded into onData: a reducer can be re-run under contention, and an effect
            // raised twice would pop two entries off the back stack.
            onEmission = { task -> if (task == null) sendEffect(TaskEditEffect.NavigateBack) },
        )
    }

    private fun save() {
        val current = state.value
        // Re-entry guard. Save used to read the state, launch and return, so two taps landed two
        // inserts before the first had come back — the screen looked identical throughout.
        if (!current.canSubmit) return
        val form = current.form

        // The rules live in the domain: this only routes the failures back to the fields that
        // caused them.
        val draftResult = TaskDraft.create(
            name = form.name,
            description = form.description,
            type = form.type,
            startDate = current.effectiveStartDate,
            endDate = form.endDate,
            hour = form.hour,
            daysInterval = form.daysInterval.toIntOrNull(),
            today = current.today,
        )

        val draft = when (draftResult) {
            is AppResult.Success -> draftResult.data
            is AppResult.Failure -> {
                applyValidationFailure(draftResult.error)
                return
            }
        }

        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            val existingId = current.taskId
            val result = if (existingId != null) {
                tasks.editTask(draft.withId(existingId))
            } else {
                tasks.addTask(draft)
            }
            setState { copy(isSubmitting = false) }
            result.fold(
                onSuccess = { sendEffect(TaskEditEffect.NavigateBack) },
                onFailure = { error -> setState { copy(message = raise(error.toSaveMessage())) } },
            )
        }
    }

    /**
     * Marks every input the domain objected to, in one pass.
     *
     * [validationFields] replaces a `(error as? Validation)?.field as? TaskField` double cast that
     * could only ever see the first failure, so a form with two bad inputs reported them one Save
     * at a time. A failure naming no field of this screen's — which an open [ValidationField]
     * hierarchy permits — is a screen-level problem and says so.
     */
    private fun applyValidationFailure(error: DomainError) {
        val fields = error.validationFields<TaskField>()
        if (fields.isEmpty()) {
            setState { copy(message = raise(TaskEditMessage.SaveFailed)) }
        } else {
            updateForm { copy(invalidFields = fields) }
        }
    }

    private fun delete() {
        val current = state.value
        if (!current.canSubmit) return
        updateForm { copy(activeDialog = null) }

        val existingId = current.taskId
        if (existingId == null) {
            sendEffect(TaskEditEffect.NavigateBack)
            return
        }

        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = tasks.deleteTask(existingId)
            setState { copy(isSubmitting = false) }
            result.fold(
                onSuccess = { sendEffect(TaskEditEffect.NavigateBack) },
                onFailure = { error ->
                    // The row already being gone is the outcome delete wanted, not a failure to
                    // report — only a real malfunction keeps the user on the screen.
                    if (error is DomainError.NotFound) {
                        sendEffect(TaskEditEffect.NavigateBack)
                    } else {
                        setState { copy(message = raise(TaskEditMessage.DeleteFailed)) }
                    }
                },
            )
        }
    }

    private companion object {
        const val TASK_KEY = "task"
        const val TODAY_KEY = "today"
    }
}

/**
 * A save onto a row that no longer exists cannot be retried into working, so it gets its own
 * wording instead of a "save failed" the user would keep pressing.
 */
private fun DomainError.toSaveMessage(): TaskEditMessage = when (this) {
    is DomainError.NotFound -> TaskEditMessage.TaskNoLongerExists
    else -> TaskEditMessage.SaveFailed
}

private fun TaskEditState.seededFrom(task: Task): TaskEditState {
    val schedule = task.schedule
    return copy(
        isLoading = false,
        loadFailed = false,
        isFormSeeded = true,
        taskId = task.id,
        form = form.copy(
            name = task.name,
            description = task.description.orEmpty(),
            type = schedule.type,
            startDate = schedule.anchorDate,
            endDate = (schedule as? TaskSchedule.Periodic)?.endDate,
            hour = schedule.hour,
            daysInterval = (schedule as? TaskSchedule.Periodic)?.daysInterval?.toString().orEmpty(),
            invalidFields = emptySet(),
        ),
    )
}

/** The date the form's start-date field should show for this schedule. */
private val TaskSchedule.anchorDate
    get() = when (this) {
        is TaskSchedule.Asap -> createdOn
        is TaskSchedule.OneTime -> date
        is TaskSchedule.Periodic -> startDate
    }
