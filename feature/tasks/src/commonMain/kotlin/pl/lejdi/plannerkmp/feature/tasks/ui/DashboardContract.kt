package pl.lejdi.plannerkmp.feature.tasks.ui

import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

data class DashboardState(
    val isLoading: Boolean = true,
    val days: List<DashboardDay> = emptyList(),
    val revealedTaskId: Long? = null,
) : MviState

sealed interface DashboardEvent : MviEvent {
    data class RevealActions(val taskId: Long) : DashboardEvent
    data object DismissActions : DashboardEvent
    data class CompleteTask(val task: Task) : DashboardEvent
    data object AddTaskClicked : DashboardEvent
    data class EditTaskClicked(val task: Task) : DashboardEvent
}

sealed interface DashboardEffect : MviEffect {
    data object NavigateToAddTask : DashboardEffect
    data class NavigateToEditTask(val task: Task) : DashboardEffect
    data class ShowError(val message: String) : DashboardEffect
}
