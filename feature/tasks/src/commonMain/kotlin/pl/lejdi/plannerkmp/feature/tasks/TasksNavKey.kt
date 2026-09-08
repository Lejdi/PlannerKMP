package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.NavKey
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

sealed interface TasksNavKey : NavKey {
    data object Dashboard : TasksNavKey
    data class TaskEdit(val task: Task?) : TasksNavKey
}
