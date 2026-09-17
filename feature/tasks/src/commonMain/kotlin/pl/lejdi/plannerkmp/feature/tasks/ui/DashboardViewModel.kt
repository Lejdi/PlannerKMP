package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.fold
import pl.lejdi.plannerkmp.core.common.invoke
import pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.ObserveTasksForDashboard

class DashboardViewModel(
    savedStateHandle: SavedStateHandle,
    logger: Logger,
    private val observeTasksForDashboard: ObserveTasksForDashboard,
    private val markTaskComplete: MarkTaskComplete,
) : RestorableViewModel<DashboardState, DashboardEvent, DashboardEffect, DashboardInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = DashboardInput.serializer(),
    logger = logger,
) {

    override fun createInitialState() = DashboardState()

    override fun captureInput(state: DashboardState) = DashboardInput(state.revealedTaskId)

    override fun applyInput(state: DashboardState, input: DashboardInput) =
        state.copy(revealedTaskId = input.revealedTaskId)

    init {
        observeDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RevealActions -> setState { copy(revealedTaskId = event.taskId) }
            is DashboardEvent.DismissActions -> setState { copy(revealedTaskId = null) }
            is DashboardEvent.CompleteTask -> completeTask(event.taskId, event.onDate)
            is DashboardEvent.AddTaskClicked -> sendEffect(DashboardEffect.NavigateToAddTask)
            is DashboardEvent.EditTaskClicked -> sendEffect(DashboardEffect.NavigateToEditTask(event.taskId))
            is DashboardEvent.RetryClicked -> observeDashboard()
            is DashboardEvent.MessageShown -> setState { copy(message = null) }
        }
    }

    /**
     * Subscribes to the stored tasks and stays subscribed: an edit made on the task screen, a
     * completion here, or the date rolling over re-emits on its own. Nothing re-queries on entry.
     *
     * Called again by [DashboardEvent.RetryClicked], because a database error terminates the
     * upstream flow and only a fresh subscription can recover.
     */
    private fun observeDashboard() = observe(
        source = observeTasksForDashboard(),
        onData = { days -> copy(isLoading = false, loadFailed = false, days = days) },
        onError = { copy(isLoading = false, loadFailed = true, message = raise(DashboardMessage.LoadFailed)) },
        onStart = { copy(isLoading = true, loadFailed = false, message = null) },
    )

    /**
     * Completing is guarded against re-entry: the tick used to launch and return, so a second tap
     * before the write came back advanced a periodic task by two intervals — the card stayed on
     * screen throughout, which is exactly what invited the second tap.
     */
    private fun completeTask(taskId: Long, onDate: LocalDate) {
        val current = state.value
        // Per task: a second tap on the same card is the double-tap this guards against; a tap on
        // another card is the user working through the day.
        if (current.isCompleting(taskId)) return
        // Resolved from the state the screen is observing rather than taken from the event, so the
        // schedule this completion is planned from is the one the data layer last emitted. A row
        // that has gone in the meantime simply has nothing to complete.
        val task = current.findTask(taskId) ?: return
        setState { copy(completingTaskIds = completingTaskIds + taskId) }
        viewModelScope.launch {
            val result = markTaskComplete(MarkTaskComplete.Params(task, onDate))
            setState { copy(completingTaskIds = completingTaskIds - taskId) }
            result.fold(
                // No reload: the flow re-emits the new list by itself.
                onSuccess = { setState { copy(revealedTaskId = null) } },
                onFailure = { setState { copy(message = raise(DashboardMessage.CompleteFailed)) } },
            )
        }
    }
}
