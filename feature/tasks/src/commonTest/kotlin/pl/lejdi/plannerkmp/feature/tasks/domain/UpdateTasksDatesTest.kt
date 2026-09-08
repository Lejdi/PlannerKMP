package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateTasksDatesTest {

    private val today = LocalDate(2026, 9, 8)

    private fun task(
        id: Long = 1,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = Task(id, "Task", null, startDate, endDate, null, daysInterval, asap)

    @Test
    fun runsWhenNoCleanupDateStoredYet() = runTest {
        val datasource = FakeTasksDatasource(initialLastCleanupDate = null)

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(today, datasource.lastCleanupDate)
    }

    @Test
    fun skipsWhenLastCleanupWasAlreadyToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask), initialLastCleanupDate = today)

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(1, datasource.tasks.size, "a stale one-time task would have been deleted if cleanup ran")
    }

    @Test
    fun runsWhenLastCleanupWasBeforeToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(
            initialTasks = listOf(staleTask),
            initialLastCleanupDate = today.minus(1, DateTimeUnit.DAY),
        )

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
        assertEquals(today, datasource.lastCleanupDate)
    }

    @Test
    fun asapTaskIsNeverTouched() = runTest {
        val asapTask = task(startDate = today.minus(30, DateTimeUnit.DAY), asap = true)
        val datasource = FakeTasksDatasource(initialTasks = listOf(asapTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(listOf(asapTask), datasource.tasks)
    }

    @Test
    fun oneTimeTaskInThePastIsDeleted() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskTodayOrFutureIsKept() = runTest {
        val futureTask = task(startDate = today, daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(futureTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(listOf(futureTask), datasource.tasks)
    }

    @Test
    fun periodicTaskPastItsEndDateIsDeleted() = runTest {
        val expiredTask = task(startDate = today.minus(20, DateTimeUnit.DAY), daysInterval = 5, endDate = today.minus(1, DateTimeUnit.DAY))
        val datasource = FakeTasksDatasource(initialTasks = listOf(expiredTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateIsNeverDeletedByEndDateRule() = runTest {
        val longRunningTask = task(startDate = today, daysInterval = 5, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(longRunningTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun periodicTaskWithPastStartDateCatchesUpInOneJumpPreservingPhase() = runTest {
        // started 2026-08-01, every 3 days: due dates are 8/1, 8/4, ..., the next due date on/after
        // today (2026-09-08) is 9/9 (8/1 + 13*3 = 9/9), since 9/8 itself isn't a multiple of 3 away.
        val lateTask = task(startDate = LocalDate(2026, 8, 1), daysInterval = 3, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(lateTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        val updated = datasource.tasks.single()
        assertEquals(LocalDate(2026, 9, 9), updated.startDate)
        assertTrue(updated.startDate >= today)
        assertEquals(0, lateTask.startDate.daysUntil(updated.startDate) % 3)
    }

    @Test
    fun lastCleanupDateOnlyAdvancesWhenTheWholePassSucceeds() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask)).apply { failNextCall = true }

        val result = UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(result is AppResult.Failure)
        assertEquals(null, datasource.lastCleanupDate)
    }
}
