package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.database.asAppResult
import pl.lejdi.plannerkmp.core.database.checkSingleRowAffected
import pl.lejdi.plannerkmp.core.database.safeMutation
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupPlan
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskDraft
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksDatasource

internal class SqlDelightTasksDatasource(
    private val queries: TaskEntityQueries,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val logger: Logger,
) : TasksDatasource {

    /**
     * SQLDelight re-runs the query and re-emits whenever `taskEntity` changes, so callers never
     * have to ask again after a write — including a write made from another screen.
     *
     * `flowOn` is what keeps the *mapping* off the main thread. `mapToList(io)` confines only the
     * query execution; every operator after it runs in the collector's context, which for every
     * caller here is `viewModelScope` — so turning a full table of rows into domain objects, on
     * every emission, was happening on the UI thread.
     */
    override fun observeTasks(): Flow<AppResult<List<Task>>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { entities -> entities.map { it.toDomain() } }
            .asAppResult(logger, "observe tasks")
            .flowOn(dispatchers.io)

    override fun observeTask(id: Long): Flow<AppResult<Task?>> =
        queries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { entity -> entity?.toDomain() }
            .asAppResult(logger, "observe task $id")
            .flowOn(dispatchers.io)

    /**
     * No `checkSingleRowAffected` here, unlike every other mutation: an `INSERT ... VALUES` with one
     * row either throws or inserts exactly that row, so there is no "matched nothing" case to
     * detect — and reporting one as [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] told the
     * user "the row no longer exists" about a row that was never supposed to exist yet.
     */
    override suspend fun addTask(draft: TaskDraft): AppResult<Unit> = mutate("task insert") {
        val columns = draft.schedule.toColumns()
        queries.insert(
            name = draft.name,
            description = draft.description,
            startDate = columns.startDate,
            endDate = columns.endDate,
            hour = columns.hour,
            daysInterval = columns.daysInterval,
            asap = columns.asap,
        ).await()
        Unit
    }

    override suspend fun editTask(task: Task): AppResult<Unit> = mutate("task update") {
        val rowsAffected = queries.update(task).await()
        checkSingleRowAffected(rowsAffected, "task update")
    }

    override suspend fun rescheduleTask(task: Task): AppResult<Unit> = mutate("task reschedule") {
        val rowsAffected = queries.updateSchedule(task).await()
        checkSingleRowAffected(rowsAffected, "task reschedule")
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> = mutate("task delete") {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "task delete")
    }

    /**
     * The read, the decision and the writes all inside one transaction.
     *
     * Per-row `checkSingleRowAffected` is deliberately not used: inside a transaction the mutators'
     * row counts are not awaited, and an all-or-nothing batch is the point. A row the plan names
     * but that has since been deleted simply matches nothing, which is the right outcome for a
     * catch-up pass.
     */
    override suspend fun runCleanup(plan: (List<Task>) -> CleanupPlan): AppResult<Unit> =
        mutate("run cleanup") {
            queries.transaction {
                val tasks = queries.selectAll().executeAsList().map { it.toDomain() }
                val cleanup = plan(tasks)
                cleanup.deletedIds.forEach { queries.deleteById(it) }
                // Schedule columns only: the cleanup has no opinion about a task's text.
                cleanup.updatedTasks.forEach { queries.updateSchedule(it) }
            }
        }

    // Every write on this port goes through safeMutation, never safeQuery: a deadline on a write
    // is what made "the save failed" and "the save is still running" the same answer to the caller.
    private suspend fun <T> mutate(operation: String, block: suspend () -> T): AppResult<T> =
        safeMutation(scope, dispatchers, logger, operation, block)
}

/**
 * The stored shape of a [TaskSchedule].
 *
 * The table predates the sealed type and still keeps the four flat columns, which is fine — a
 * schema is a storage format, not a domain model. This type is the one place that knows how the two
 * correspond, so the translation is written once in each direction and both are visible together.
 */
private data class ScheduleColumns(
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hour: LocalTime?,
    val daysInterval: Long,
    val asap: Long,
)

private fun TaskSchedule.toColumns(): ScheduleColumns = when (this) {
    is TaskSchedule.Asap -> ScheduleColumns(createdOn, null, hour, 0L, 1L)
    is TaskSchedule.OneTime -> ScheduleColumns(date, null, hour, 0L, 0L)
    is TaskSchedule.Periodic -> ScheduleColumns(startDate, endDate, hour, daysInterval.toLong(), 0L)
}

/**
 * Rebuilds the schedule from its columns.
 *
 * The order of the branches is the invariant: `asap` wins, then a positive interval means periodic,
 * and anything else is a one-off. Because `Periodic` is only ever built where `daysInterval > 0`,
 * its own `require` cannot fire on a row read back from storage — including a row written by an
 * older version of the app.
 */
private fun TaskEntity.toSchedule(): TaskSchedule = when {
    asap != 0L -> TaskSchedule.Asap(createdOn = startDate, hour = hour)
    daysInterval > 0L -> TaskSchedule.Periodic(
        startDate = startDate,
        daysInterval = daysInterval.toInt(),
        endDate = endDate,
        hour = hour,
    )
    else -> TaskSchedule.OneTime(date = startDate, hour = hour)
}

// One place that knows how a Task maps onto the update statement's eight parameters, rather than
// the same eight-line argument list written out at every call site.
private fun TaskEntityQueries.update(task: Task) = task.schedule.toColumns().let { columns ->
    update(
        id = task.id,
        name = task.name,
        description = task.description,
        startDate = columns.startDate,
        endDate = columns.endDate,
        hour = columns.hour,
        daysInterval = columns.daysInterval,
        asap = columns.asap,
    )
}

private fun TaskEntityQueries.updateSchedule(task: Task) = task.schedule.toColumns().let { columns ->
    updateSchedule(
        id = task.id,
        startDate = columns.startDate,
        endDate = columns.endDate,
        hour = columns.hour,
        daysInterval = columns.daysInterval,
        asap = columns.asap,
    )
}

private fun TaskEntity.toDomain(): Task = Task(
    id = id,
    name = name,
    description = description,
    schedule = toSchedule(),
)
