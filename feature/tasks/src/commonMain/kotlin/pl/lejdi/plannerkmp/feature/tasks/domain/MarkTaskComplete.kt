package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.UseCase

/**
 * Completing a task advances it past the occurrence that was completed, or removes it when there
 * is none left.
 *
 * The policy is here; what "next occurrence" means is [Task.nextOccurrenceAfter]'s business.
 */
class MarkTaskComplete(
    private val datasource: TasksDatasource,
) : UseCase<MarkTaskComplete.Params, AppResult<Unit>> {

    /**
     * [completedOn] is the day whose card the user actually ticked, not the task's own next due
     * date.
     *
     * A periodic task appears on every one of its occurrences inside the dashboard's 8-day window,
     * so a two-day task is on screen four times. Advancing from `task.startDate` regardless meant
     * ticking the card four days out moved the task by one interval: the card the user pressed
     * stayed exactly where it was, and a different one — today's — disappeared instead.
     */
    data class Params(val task: Task, val completedOn: LocalDate)

    override suspend fun invoke(params: Params): AppResult<Unit> {
        val task = params.task
        val next = task.nextOccurrenceAfter(params.completedOn)
        return if (next == null) {
            datasource.deleteTask(task.id)
        } else {
            // rescheduleTask, not editTask: this only moves the schedule, and `task` is a
            // snapshot the dashboard has been holding, so writing its whole row would push a stale
            // name and description over anything edited since it was read.
            datasource.rescheduleTask(task.startingFrom(next))
        }
    }
}
