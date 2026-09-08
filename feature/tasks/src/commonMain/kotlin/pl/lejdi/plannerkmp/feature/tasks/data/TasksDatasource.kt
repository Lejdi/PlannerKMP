package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

interface TasksDatasource {
    suspend fun getAllTasks(): AppResult<List<Task>>
    suspend fun addTask(task: Task): AppResult<Unit>
    suspend fun editTask(task: Task): AppResult<Unit>
    suspend fun deleteTask(id: Long): AppResult<Unit>
    suspend fun getLastCleanupDate(): AppResult<LocalDate?>
    suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit>
}
