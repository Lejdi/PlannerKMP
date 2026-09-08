package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

class MarkTaskComplete(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {

    override suspend fun invoke(params: Task): AppResult<Unit> {
        if (params.daysInterval == 0) return datasource.deleteTask(params.id)

        val newStartDate = params.startDate.plus(params.daysInterval, DateTimeUnit.DAY)
        val endDate = params.endDate
        return if (endDate != null && newStartDate > endDate) {
            datasource.deleteTask(params.id)
        } else {
            datasource.editTask(params.copy(startDate = newStartDate))
        }
    }
}
