package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import pl.lejdi.plannerkmp.feature.tasks.domain.type

class TaskEditViewModel(
    private val initialTask: Task?,
    private val addTask: AddTask,
    private val editTask: EditTask,
    private val deleteTask: DeleteTask,
    private val todayProvider: TodayProvider,
) : BaseViewModel<TaskEditState, TaskEditEvent, TaskEditEffect>() {

    private val originalTaskId: Long? = initialTask?.id

    override fun createInitialState(): TaskEditState = if (initialTask != null) {
        TaskEditState(
            taskId = initialTask.id,
            name = initialTask.name,
            description = initialTask.description.orEmpty(),
            type = initialTask.type,
            startDate = initialTask.startDate,
            endDate = initialTask.endDate,
            hour = initialTask.hour,
            daysInterval = if (initialTask.daysInterval > 0) initialTask.daysInterval.toString() else "",
        )
    } else {
        TaskEditState(type = TaskType.Asap, startDate = todayProvider.today())
    }

    override fun onEvent(event: TaskEditEvent) {
        when (event) {
            is TaskEditEvent.NameChanged -> setState { copy(name = event.value, nameError = false) }
            is TaskEditEvent.DescriptionChanged -> setState { copy(description = event.value) }
            is TaskEditEvent.TypeChanged -> setState { copy(type = event.value) }
            is TaskEditEvent.StartDateChanged -> setState {
                val snappedEndDate = endDate?.takeIf { it >= event.value } ?: event.value
                copy(startDate = event.value, endDate = snappedEndDate)
            }
            is TaskEditEvent.EndDateChanged -> setState { copy(endDate = event.value) }
            is TaskEditEvent.HourChanged -> setState { copy(hour = event.value) }
            is TaskEditEvent.DaysIntervalChanged -> setState { copy(daysInterval = event.value) }
            is TaskEditEvent.SaveClicked -> save()
            is TaskEditEvent.DeleteClicked -> delete()
        }
    }

    private fun save() {
        val current = state.value
        if (current.name.isBlank()) {
            setState { copy(nameError = true) }
            return
        }

        val task = Task(
            id = current.taskId ?: 0L,
            name = current.name,
            description = current.description.ifBlank { null },
            startDate = if (current.type == TaskType.Asap) todayProvider.today() else current.startDate ?: todayProvider.today(),
            endDate = if (current.type == TaskType.Periodic) current.endDate else null,
            hour = current.hour,
            // A negative interval would walk UpdateTasksDates' catch-up math backwards
            // forever, so anything not strictly positive falls back to 0.
            daysInterval = if (current.type == TaskType.Periodic) {
                current.daysInterval.toIntOrNull()?.takeIf { it > 0 } ?: 0
            } else {
                0
            },
            asap = current.type == TaskType.Asap,
        )

        viewModelScope.launch {
            val result = if (current.taskId != null) editTask(task) else addTask(task)
            when (result) {
                is AppResult.Success -> sendEffect(TaskEditEffect.NavigateBack)
                is AppResult.Failure -> sendEffect(TaskEditEffect.ShowError(result.error.message))
            }
        }
    }

    private fun delete() {
        val taskId = originalTaskId
        if (taskId == null) {
            sendEffect(TaskEditEffect.NavigateBack)
            return
        }
        viewModelScope.launch {
            when (val result = deleteTask(taskId)) {
                is AppResult.Success -> sendEffect(TaskEditEffect.NavigateBack)
                is AppResult.Failure -> sendEffect(TaskEditEffect.ShowError(result.error.message))
            }
        }
    }
}
