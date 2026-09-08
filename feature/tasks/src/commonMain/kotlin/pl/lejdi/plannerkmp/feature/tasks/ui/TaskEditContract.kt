package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

data class TaskEditState(
    val taskId: Long? = null,
    val name: String = "",
    val description: String = "",
    val type: TaskType = TaskType.Asap,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val hour: LocalTime? = null,
    val daysInterval: String = "",
    val nameError: Boolean = false,
) : MviState

sealed interface TaskEditEvent : MviEvent {
    data class NameChanged(val value: String) : TaskEditEvent
    data class DescriptionChanged(val value: String) : TaskEditEvent
    data class TypeChanged(val value: TaskType) : TaskEditEvent
    data class StartDateChanged(val value: LocalDate) : TaskEditEvent
    data class EndDateChanged(val value: LocalDate) : TaskEditEvent
    data class HourChanged(val value: LocalTime?) : TaskEditEvent
    data class DaysIntervalChanged(val value: String) : TaskEditEvent
    data object SaveClicked : TaskEditEvent
    data object DeleteClicked : TaskEditEvent
}

sealed interface TaskEditEffect : MviEffect {
    data object NavigateBack : TaskEditEffect
    data class ShowError(val message: String) : TaskEditEffect
}
