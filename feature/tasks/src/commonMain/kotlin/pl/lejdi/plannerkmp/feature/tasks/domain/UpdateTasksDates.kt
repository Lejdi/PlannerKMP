package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource
import kotlin.math.ceil

class UpdateTasksDates(
    private val datasource: TasksDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<Unit, AppResult<Unit>> {

    override suspend fun invoke(params: Unit): AppResult<Unit> {
        val today = todayProvider.today()

        val lastCleanupResult = datasource.getLastCleanupDate()
        if (lastCleanupResult is AppResult.Failure) return lastCleanupResult
        val lastCleanupDate = (lastCleanupResult as AppResult.Success).data
        if (lastCleanupDate != null && lastCleanupDate >= today) return AppResult.Success(Unit)

        val tasksResult = datasource.getAllTasks()
        if (tasksResult is AppResult.Failure) return tasksResult

        for (task in (tasksResult as AppResult.Success).data) {
            val result = processTask(task, today)
            if (result is AppResult.Failure) return result
        }

        return datasource.setLastCleanupDate(today)
    }

    private suspend fun processTask(task: Task, today: LocalDate): AppResult<Unit> = when {
        task.asap -> AppResult.Success(Unit)
        task.daysInterval == 0 -> {
            if (task.startDate < today) datasource.deleteTask(task.id) else AppResult.Success(Unit)
        }
        task.endDate != null && task.endDate < today -> datasource.deleteTask(task.id)
        task.startDate < today -> {
            val daysSinceStart = task.startDate.daysUntil(today)
            val intervalsElapsed = ceil(daysSinceStart.toDouble() / task.daysInterval).toInt()
            val newStartDate = task.startDate.plus(intervalsElapsed * task.daysInterval, DateTimeUnit.DAY)
            datasource.editTask(task.copy(startDate = newStartDate))
        }
        else -> AppResult.Success(Unit)
    }
}
