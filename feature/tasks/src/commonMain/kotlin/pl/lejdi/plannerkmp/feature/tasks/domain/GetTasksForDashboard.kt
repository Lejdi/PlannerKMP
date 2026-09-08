package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

private const val NUMBER_OF_VISIBLE_DAYS = 8

class GetTasksForDashboard(
    private val datasource: TasksDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<Unit, AppResult<List<DashboardDay>>> {

    override suspend fun invoke(params: Unit): AppResult<List<DashboardDay>> {
        val tasksResult = datasource.getAllTasks()
        if (tasksResult is AppResult.Failure) return tasksResult
        val tasks = (tasksResult as AppResult.Success).data

        val today = todayProvider.today()
        val days = (0 until NUMBER_OF_VISIBLE_DAYS).map { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            DashboardDay(
                date = date,
                tasks = tasks.filter { isVisibleOn(it, date, today) }.sortedWith(dashboardTaskComparator),
            )
        }
        return AppResult.Success(days)
    }

    private fun isVisibleOn(task: Task, date: LocalDate, today: LocalDate): Boolean = when {
        task.asap -> date == today
        task.daysInterval == 0 -> date == task.startDate
        else -> date >= task.startDate &&
            task.startDate.daysUntil(date) % task.daysInterval == 0 &&
            (task.endDate == null || date <= task.endDate)
    }
}

private val dashboardTaskComparator = compareBy<Task>(
    { priorityBucket(it) },
    { it.hour?.toString() ?: "" },
)

private fun priorityBucket(task: Task): Int = when {
    task.hour != null -> 0
    task.asap -> 1
    task.daysInterval == 0 -> 2
    else -> 3
}
