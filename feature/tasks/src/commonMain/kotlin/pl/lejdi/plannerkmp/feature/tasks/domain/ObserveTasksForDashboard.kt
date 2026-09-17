package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.FlowUseCase
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.map

private const val NUMBER_OF_VISIBLE_DAYS = 8

/**
 * The dashboard's 8-day window, recomputed on every change to the stored tasks *and* every time
 * the date changes.
 *
 * A [FlowUseCase]: it hands back a stream rather than awaiting a result, so a task edited on
 * another screen shows up here without the dashboard having to notice it was re-entered and
 * re-query.
 *
 * The window is combined with [TodayProvider.todayFlow] rather than reading the date off each task
 * emission. Reading it per emission looks like it survives midnight but does not: the task flow
 * only re-emits when the table is written, so a phone left on this screen overnight crossed
 * midnight with nothing to trigger a recompute and went on labelling yesterday's column "today".
 */
class ObserveTasksForDashboard(
    private val datasource: TasksDatasource,
    private val todayProvider: TodayProvider,
    private val dispatchers: CoroutineDispatchers,
) : FlowUseCase<Unit, AppResult<List<DashboardDay>>> {

    /**
     * `flowOn(default)` is not decoration. `combine` runs its transform in the *collector's*
     * context, and every collector of this is a `viewModelScope` — so [buildDays], which filters
     * the whole task list eight times and sorts each result, ran on the UI thread on every write
     * and every midnight tick. That silently undid the `flowOn(io)` the datasource pays for one
     * layer down: confining the query buys nothing if the work done with its result is not.
     *
     * `default` rather than `io`: this is CPU work over data already in memory, not a query.
     */
    override fun invoke(params: Unit): Flow<AppResult<List<DashboardDay>>> =
        combine(datasource.observeTasks(), todayProvider.todayFlow()) { result, today ->
            result.map { tasks -> buildDays(tasks, today) }
        }.flowOn(dispatchers.default)

    private fun buildDays(tasks: List<Task>, today: LocalDate): List<DashboardDay> =
        (0 until NUMBER_OF_VISIBLE_DAYS).map { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            DashboardDay(
                date = date,
                tasks = tasks
                    .filter { it.occursOn(date, today) }
                    .sortedWith(dashboardTaskComparator),
            )
        }
}

// Timed tasks first and in clock order, then ASAP, then one-offs, then everything repeating.
// The final `id` key makes the order total: without it two untimed tasks in the same bucket
// compared equal, so their relative order came from whatever order storage happened to return —
// which SQL does not promise and which a LazyColumn turns into cards swapping places.
private val dashboardTaskComparator = compareBy<Task>(
    { priorityBucket(it) },
    // LocalTime is Comparable, so compare it directly. This used to be `hour?.toString() ?: ""`,
    // which happened to order correctly only because ISO times sort the same as their text.
    { it.schedule.hour },
    { it.id },
)

private fun priorityBucket(task: Task): Int = when {
    task.schedule.hour != null -> 0
    task.type == TaskType.Asap -> 1
    task.type == TaskType.OneTime -> 2
    else -> 3
}
