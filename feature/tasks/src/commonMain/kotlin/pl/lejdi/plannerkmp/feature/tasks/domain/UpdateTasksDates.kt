package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.UseCase
import pl.lejdi.plannerkmp.core.common.flatMap
import pl.lejdi.plannerkmp.core.common.fold

/**
 * The once-a-day catch-up: drops tasks whose schedule has run out and rolls periodic tasks forward
 * to their next occurrence.
 *
 * Takes the day to run for as its parameter rather than reading the clock itself. Its only caller,
 * [TasksCleanupInitializer], is driven by `TodayProvider.todayFlow()` and so already *has* the date
 * — it used to discard it and have this re-read `TodayProvider.today()`, which is two reads of
 * "today" per run with a window between them for the two to disagree. The one moment that window is
 * open is the midnight rollover, which is precisely the moment this use case exists for.
 */
class UpdateTasksDates(
    private val datasource: TasksDatasource,
    private val cleanupDateStore: CleanupDateStore,
) : UseCase<LocalDate, AppResult<Unit>> {

    override suspend fun invoke(params: LocalDate): AppResult<Unit> {
        val today = params

        // A failed *read* is not fatal, which is why this folds rather than chaining. The cleanup is
        // idempotent, so the safe answer to "I cannot tell when this last ran" is to run it.
        // Aborting instead meant a single unreadable stored value — the realistic cause being the
        // stored shape changing between app versions — disabled the cleanup for the life of the
        // install: the write that would have replaced the bad value sits behind the read that keeps
        // failing, so nothing could ever clear it.
        val lastCleanupDate = cleanupDateStore.getLastCleanupDate().fold(
            onSuccess = { it },
            onFailure = { null },
        )
        if (lastCleanupDate != null && lastCleanupDate >= today) return AppResult.Success(Unit)

        // One transaction for the read, the decision and the writes — see TasksDatasource.runCleanup.
        return datasource.runCleanup { tasks -> planCleanup(tasks, today) }
            .flatMap { cleanupDateStore.setLastCleanupDate(today) }
    }
}

/** What a cleanup run would change. Pure, so the decision is testable without a datasource. */
data class CleanupPlan(
    val deletedIds: List<Long>,
    val updatedTasks: List<Task>,
)

/**
 * Policy only: *which* tasks to drop and which to move. Whether a task has run out, and where it
 * moves to, are [Task.hasExpiredBy] and [Task.occurrenceOnOrAfter].
 */
internal fun planCleanup(tasks: List<Task>, today: LocalDate): CleanupPlan {
    val deletedIds = mutableListOf<Long>()
    val updatedTasks = mutableListOf<Task>()

    for (task in tasks) {
        val schedule = task.schedule
        when {
            task.hasExpiredBy(today) -> deletedIds += task.id
            // Periodic only, and named through the schedule type rather than through a raw
            // `startDate < today`: an ASAP task's own date is in the past the moment it is a day
            // old, and it is meant to sit there until the user completes it.
            schedule is TaskSchedule.Periodic && schedule.startDate < today -> {
                // A task still within its end date but with no occurrence left (its next one would
                // land past that end date) is finished too, and is dropped rather than rolled
                // forward into a row that can never be shown again.
                val next = task.occurrenceOnOrAfter(today)
                if (next == null) deletedIds += task.id else updatedTasks += task.startingFrom(next)
            }
            else -> Unit
        }
    }

    return CleanupPlan(deletedIds = deletedIds, updatedTasks = updatedTasks)
}
