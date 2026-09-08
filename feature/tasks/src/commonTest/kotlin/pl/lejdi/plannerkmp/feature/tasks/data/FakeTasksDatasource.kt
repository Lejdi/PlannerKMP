package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

class FakeTasksDatasource(
    initialTasks: List<Task> = emptyList(),
    initialLastCleanupDate: LocalDate? = null,
) : TasksDatasource {

    val tasks = mutableListOf<Task>().apply { addAll(initialTasks) }
    var lastCleanupDate: LocalDate? = initialLastCleanupDate
    var nextId: Long = (initialTasks.maxOfOrNull { it.id } ?: 0L) + 1
    var failNextCall: Boolean = false

    override suspend fun getAllTasks(): AppResult<List<Task>> {
        if (consumeFailure()) return failure()
        return AppResult.Success(tasks.toList())
    }

    override suspend fun addTask(task: Task): AppResult<Unit> {
        if (consumeFailure()) return failure()
        tasks.add(task.copy(id = nextId++))
        return AppResult.Success(Unit)
    }

    override suspend fun editTask(task: Task): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index == -1) return failure()
        tasks[index] = task
        return AppResult.Success(Unit)
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val removed = tasks.removeAll { it.id == id }
        return if (removed) AppResult.Success(Unit) else failure()
    }

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> {
        if (consumeFailure()) return failure()
        return AppResult.Success(lastCleanupDate)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> {
        if (consumeFailure()) return failure()
        lastCleanupDate = date
        return AppResult.Success(Unit)
    }

    private fun consumeFailure(): Boolean {
        if (!failNextCall) return false
        failNextCall = false
        return true
    }

    private fun <T> failure(): AppResult<T> = AppResult.Failure(DomainError.Database("fake failure"))
}
