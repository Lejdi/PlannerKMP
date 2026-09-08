package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates

class DashboardViewModel(
    private val getTasksForDashboard: GetTasksForDashboard,
    private val updateTasksDates: UpdateTasksDates,
    private val markTaskComplete: MarkTaskComplete,
) : BaseViewModel<DashboardState, DashboardEvent, DashboardEffect>() {

    override fun createInitialState() = DashboardState()

    init {
        loadDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RevealActions -> setState { copy(revealedTaskId = event.taskId) }
            is DashboardEvent.DismissActions -> setState { copy(revealedTaskId = null) }
            is DashboardEvent.CompleteTask -> completeTask(event.task)
            is DashboardEvent.AddTaskClicked -> sendEffect(DashboardEffect.NavigateToAddTask)
            is DashboardEvent.EditTaskClicked -> sendEffect(DashboardEffect.NavigateToEditTask(event.task))
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            updateTasksDates(Unit)
            when (val result = getTasksForDashboard(Unit)) {
                is AppResult.Success -> setState {
                    copy(isLoading = false, days = result.data, today = result.data.firstOrNull()?.date)
                }
                is AppResult.Failure -> {
                    setState { copy(isLoading = false) }
                    sendEffect(DashboardEffect.ShowError(result.error.message))
                }
            }
        }
    }

    private fun completeTask(task: Task) {
        viewModelScope.launch {
            when (val result = markTaskComplete(task)) {
                is AppResult.Success -> {
                    setState { copy(revealedTaskId = null) }
                    loadDashboard()
                }
                is AppResult.Failure -> sendEffect(DashboardEffect.ShowError(result.error.message))
            }
        }
    }
}
