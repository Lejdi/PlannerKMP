package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

class SqlDelightTasksDatasource(
    private val queries: TaskEntityQueries,
    private val lastCleanupDateCache: KeyValueCache<Unit, LocalDate>,
) : TasksDatasource {

    override suspend fun getAllTasks(): AppResult<List<Task>> = safeQuery {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addTask(task: Task): AppResult<Unit> = safeQuery {
        queries.insert(
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        )
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row inserted, got $rowsAffected" }
    }

    override suspend fun editTask(task: Task): AppResult<Unit> = safeQuery {
        queries.update(
            id = task.id,
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        )
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row updated, got $rowsAffected" }
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> = safeQuery {
        queries.deleteById(id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row deleted, got $rowsAffected" }
    }

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> = safeQuery {
        lastCleanupDateCache.get(Unit)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> = safeQuery {
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
