package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.checkSingleRowAffected
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

class SqlDelightTasksDatasource(
    private val queries: TaskEntityQueries,
    private val lastCleanupDateCache: KeyValueCache<Unit, LocalDate>,
    private val dispatchers: CoroutineDispatchers,
) : TasksDatasource {

    override suspend fun getAllTasks(): AppResult<List<Task>> = safeQuery(dispatchers) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addTask(task: Task): AppResult<Unit> = safeQuery(dispatchers) {
        val rowsAffected = queries.insert(
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        ).await()
        checkSingleRowAffected(rowsAffected, "task insert")
    }

    override suspend fun editTask(task: Task): AppResult<Unit> = safeQuery(dispatchers) {
        val rowsAffected = queries.update(
            id = task.id,
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        ).await()
        checkSingleRowAffected(rowsAffected, "task update")
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> = safeQuery(dispatchers) {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "task delete")
    }

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> = safeQuery(dispatchers) {
        lastCleanupDateCache.get(Unit)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> = safeQuery(dispatchers) {
        lastCleanupDateCache.put(Unit, date)
    }
}

private fun TaskEntity.toDomain(): Task = Task(
    id = id,
    name = name,
    description = description,
    startDate = startDate,
    endDate = endDate,
    hour = hour,
    daysInterval = daysInterval.toInt(),
    asap = asap != 0L,
)
