package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkTaskCompleteTest {

    private val startDate = LocalDate(2026, 9, 1)

    private fun periodic(daysInterval: Int, endDate: LocalDate? = null) =
        periodicTask(startDate = startDate, daysInterval = daysInterval, endDate = endDate)

    @Test
    fun asapTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(asapTask(createdOn = startDate)))

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(oneTimeTask(date = startDate)))

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateAdvancesByOneInterval() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 7)))

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().anchorDate)
    }

    @Test
    fun periodicTaskAdvancingWithinEndDateIsUpdatedNotDeleted() = runTest {
        val endDate = startDate.plus(10, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().anchorDate)
    }

    @Test
    fun periodicTaskAdvancingPastEndDateIsDeleted() = runTest {
        val endDate = startDate.plus(5, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertTrue(datasource.tasks.isEmpty())
    }

    /**
     * The bug this signature exists to prevent.
     *
     * A periodic task is rendered on every occurrence inside the dashboard's 8-day window, so a
     * two-day task is on screen four times. Advancing from the task's own startDate regardless of
     * which card was ticked meant pressing "complete" four days out moved the task by a single
     * interval: the card the user pressed stayed put, and today's disappeared instead.
     */
    @Test
    fun completingAFutureOccurrenceAdvancesPastThatOccurrenceNotTheNearestOne() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 2)))
        val fourthDay = startDate.plus(4, DateTimeUnit.DAY)

        MarkTaskComplete(datasource).invoke(
            MarkTaskComplete.Params(datasource.tasks.single(), completedOn = fourthDay),
        )

        assertEquals(startDate.plus(6, DateTimeUnit.DAY), datasource.tasks.single().anchorDate)
    }

    @Test
    fun completingTodaysOccurrenceStillAdvancesByOneInterval() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 2)))

        MarkTaskComplete(datasource).invoke(
            MarkTaskComplete.Params(datasource.tasks.single(), completedOn = startDate),
        )

        assertEquals(startDate.plus(2, DateTimeUnit.DAY), datasource.tasks.single().anchorDate)
    }

    @Test
    fun completingTheLastOccurrenceBeforeTheEndDateDeletesTheTask() = runTest {
        val endDate = startDate.plus(5, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic(daysInterval = 2, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(
            MarkTaskComplete.Params(datasource.tasks.single(), completedOn = startDate.plus(4, DateTimeUnit.DAY)),
        )

        assertTrue(datasource.tasks.isEmpty(), "the next occurrence would fall past the end date")
    }

    @Test
    fun propagatesDatasourceFailure() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(oneTimeTask(date = startDate)))
        datasource.failNext(TasksWrite.Delete)

        val result = MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(datasource.tasks.single(), startDate))

        assertTrue(result is AppResult.Failure)
    }

    /**
     * The dashboard hands over the [Task] it is displaying, which is a snapshot.
     *
     * Completing used to write that whole snapshot back, so ticking a card whose row had been
     * renamed on the edit screen in the meantime replaced the new name with the one the dashboard
     * had been holding — and reported success, because the write itself was fine. Only the schedule
     * is this caller's business.
     */
    @Test
    fun completingFromAStaleSnapshotDoesNotRestoreItsOldText() = runTest {
        val datasource = FakeTasksDatasource(
            initialTasks = listOf(periodicTask(id = 1, name = "Renamed", startDate = startDate, daysInterval = 7)),
        )
        val staleSnapshot = periodicTask(id = 1, name = "Old name", startDate = startDate, daysInterval = 7)

        MarkTaskComplete(datasource).invoke(MarkTaskComplete.Params(staleSnapshot, startDate))

        val stored = datasource.tasks.single()
        assertEquals("Renamed", stored.name, "the stale snapshot's name must not be written back")
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), stored.anchorDate, "but the schedule did move")
    }
}
