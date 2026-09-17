package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateTasksDatesTest {

    private val today = LocalDate(2026, 9, 8)

    /** Dispatches to the safe constructors; see the note on the equivalent in the dashboard test. */
    private fun task(
        id: Long = 1,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = when {
        asap -> asapTask(id = id, createdOn = startDate)
        daysInterval > 0 -> periodicTask(
            id = id,
            startDate = startDate,
            daysInterval = daysInterval,
            endDate = endDate,
        )
        else -> oneTimeTask(id = id, date = startDate)
    }

    private fun useCase(
        datasource: FakeTasksDatasource,
        store: FakeCleanupDateStore,
    ) = UpdateTasksDates(datasource, store)

    @Test
    fun runsWhenNoCleanupDateStoredYet() = runTest {
        val datasource = FakeTasksDatasource()
        val store = FakeCleanupDateStore(initialDate = null)

        useCase(datasource, store).invoke(today)

        assertEquals(today, store.lastCleanupDate)
    }

    @Test
    fun skipsWhenLastCleanupWasAlreadyToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask))
        val store = FakeCleanupDateStore(initialDate = today)

        useCase(datasource, store).invoke(today)

        assertEquals(1, datasource.tasks.size, "a stale one-time task would have been deleted if cleanup ran")
    }

    @Test
    fun runsWhenLastCleanupWasBeforeToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask))
        val store = FakeCleanupDateStore(initialDate = today.minus(1, DateTimeUnit.DAY))

        useCase(datasource, store).invoke(today)

        assertTrue(datasource.tasks.isEmpty())
        assertEquals(today, store.lastCleanupDate)
    }

    @Test
    fun asapTaskIsNeverTouched() = runTest {
        val theAsapTask = task(startDate = today.minus(30, DateTimeUnit.DAY), asap = true)
        val datasource = FakeTasksDatasource(initialTasks = listOf(theAsapTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertEquals(listOf(theAsapTask), datasource.tasks)
    }

    @Test
    fun oneTimeTaskInThePastIsDeleted() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskTodayOrFutureIsKept() = runTest {
        val futureTask = task(startDate = today, daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(futureTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertEquals(listOf(futureTask), datasource.tasks)
    }

    @Test
    fun periodicTaskPastItsEndDateIsDeleted() = runTest {
        val expiredTask = task(
            startDate = today.minus(20, DateTimeUnit.DAY),
            daysInterval = 5,
            endDate = today.minus(1, DateTimeUnit.DAY),
        )
        val datasource = FakeTasksDatasource(initialTasks = listOf(expiredTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateIsNeverDeletedByEndDateRule() = runTest {
        val longRunningTask = task(startDate = today, daysInterval = 5, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(longRunningTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun periodicTaskWithPastStartDateCatchesUpInOneJumpPreservingPhase() = runTest {
        // started 2026-08-01, every 3 days: due dates are 8/1, 8/4, ..., the next due date on/after
        // today (2026-09-08) is 9/9 (8/1 + 13*3 = 9/9), since 9/8 itself isn't a multiple of 3 away.
        val lateTask = task(startDate = LocalDate(2026, 8, 1), daysInterval = 3, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(lateTask))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        val updated = datasource.tasks.single()
        assertEquals(LocalDate(2026, 9, 9), updated.anchorDate)
        assertTrue(updated.anchorDate >= today)
        assertEquals(0, lateTask.anchorDate.daysUntil(updated.anchorDate) % 3)
    }

    @Test
    fun lastCleanupDateOnlyAdvancesWhenTheWholePassSucceeds() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask))
        val store = FakeCleanupDateStore()
        datasource.failNext(TasksWrite.Cleanup)

        val result = useCase(datasource, store).invoke(today)

        assertTrue(result is AppResult.Failure)
        assertEquals(null, store.lastCleanupDate)
    }

    @Test
    fun everyChangeIsAppliedInOneBatchRatherThanOneWritePerTask() = runTest {
        val expired = task(id = 1, startDate = today.minus(2, DateTimeUnit.DAY), daysInterval = 0)
        val alsoExpired = task(id = 2, startDate = today.minus(3, DateTimeUnit.DAY), daysInterval = 0)
        val late = task(id = 3, startDate = today.minus(4, DateTimeUnit.DAY), daysInterval = 2)
        val datasource = FakeTasksDatasource(initialTasks = listOf(expired, alsoExpired, late))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        assertEquals(1, datasource.appliedCleanups, "cleanup must reach the datasource as a single transaction")
        assertEquals(listOf(1L, 2L), datasource.lastCleanupDeletes)
        assertEquals(listOf(3L), datasource.lastCleanupUpdates.map { it.id })
    }

    @Test
    fun planCleanupDecidesWithoutTouchingStorage() {
        val expired = task(id = 1, startDate = today.minus(2, DateTimeUnit.DAY), daysInterval = 0)
        val late = task(id = 2, startDate = today.minus(4, DateTimeUnit.DAY), daysInterval = 2)
        val untouched = task(id = 3, startDate = today, daysInterval = 0)

        val plan = planCleanup(listOf(expired, late, untouched), today)

        assertEquals(listOf(1L), plan.deletedIds)
        assertEquals(listOf(2L), plan.updatedTasks.map { it.id })
        assertEquals(today, plan.updatedTasks.single().anchorDate)
    }

    /** A clock moved backwards, or a westward timezone change, leaves a date later than today. */
    @Test
    fun skipsWhenTheStoredCleanupDateIsAfterToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask))
        val store = FakeCleanupDateStore(initialDate = today.plus(1, DateTimeUnit.DAY))

        useCase(datasource, store).invoke(today)

        assertEquals(1, datasource.tasks.size, "a stale one-time task would have been deleted if cleanup ran")
    }

    /**
     * An unreadable stored date must not disable the cleanup.
     *
     * The read used to short-circuit the whole pass, and because the write that would replace a bad
     * value sits behind that read, one unparseable value stopped the cleanup for the life of the
     * install. The pass is idempotent, so not knowing when it last ran means running it.
     */
    @Test
    fun runsAnywayWhenTheStoredCleanupDateCannotBeRead() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask))
        val store = FakeCleanupDateStore(initialDate = null)
        store.failNextRead = true

        val result = useCase(datasource, store).invoke(today)

        assertTrue(result is AppResult.Success)
        assertTrue(datasource.tasks.isEmpty(), "the cleanup ran despite the unreadable date")
        assertEquals(today, store.lastCleanupDate, "and replaced the unreadable value")
    }

    @Test
    fun aFailedCleanupDateWriteFailsThePass() = runTest {
        val datasource = FakeTasksDatasource()
        val store = FakeCleanupDateStore()
        store.failNextWrite = true

        val result = useCase(datasource, store).invoke(today)

        assertTrue(result is AppResult.Failure)
    }

    /**
     * The cleanup moves schedules, never text.
     *
     * It plans from rows it read and then writes; when that write was the full row, a name the user
     * had changed in between was replaced by the one the cleanup had read, and the write reported
     * success. The narrow schedule write is what makes that impossible.
     */
    @Test
    fun rollingATaskForwardLeavesItsTextAlone() = runTest {
        val late = periodicTask(
            id = 1,
            name = "Renamed by the user",
            description = "and described",
            startDate = today.minus(4, DateTimeUnit.DAY),
            daysInterval = 2,
        )
        val datasource = FakeTasksDatasource(initialTasks = listOf(late))

        useCase(datasource, FakeCleanupDateStore()).invoke(today)

        val rolled = datasource.tasks.single()
        assertEquals(today, rolled.anchorDate)
        assertEquals("Renamed by the user", rolled.name)
        assertEquals("and described", rolled.description)
    }
}
