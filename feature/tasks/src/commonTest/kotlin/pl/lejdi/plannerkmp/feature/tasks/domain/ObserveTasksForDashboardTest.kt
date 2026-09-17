package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveTasksForDashboardTest {

    // Unconfined for both io and default, so the flowOn the use case applies runs inline and the
    // assertions stay deterministic.
    private val dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined)

    private val today = LocalDate(2026, 9, 8)

    /**
     * Keeps these tests reading in the flat terms they were written in, while dispatching to the
     * safe constructors. Note it *cannot* build a contradictory task the way the flat model could:
     * the branch decides the kind, and the kind decides which fields survive.
     */
    private fun task(
        id: Long = 1,
        name: String = "Task",
        startDate: LocalDate = today,
        endDate: LocalDate? = null,
        hour: LocalTime? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = when {
        asap -> asapTask(id = id, name = name, createdOn = startDate, hour = hour)
        daysInterval > 0 -> periodicTask(
            id = id,
            name = name,
            startDate = startDate,
            daysInterval = daysInterval,
            endDate = endDate,
            hour = hour,
        )
        else -> oneTimeTask(id = id, name = name, date = startDate, hour = hour)
    }

    private suspend fun days(vararg tasks: Task): List<DashboardDay> {
        val datasource = FakeTasksDatasource(initialTasks = tasks.toList())
        val useCase = ObserveTasksForDashboard(datasource, FakeTodayProvider(today), dispatchers)
        val result = useCase.invoke(Unit).first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    @Test
    fun alwaysReturnsExactlyEightDaysStartingToday() = runTest {
        val result = days()

        assertEquals(8, result.size)
        assertEquals(today, result.first().date)
        assertEquals(today.plus(7, kotlinx.datetime.DateTimeUnit.DAY), result.last().date)
    }

    @Test
    fun asapTaskShowsOnlyOnToday() = runTest {
        val theAsapTask = task(asap = true, startDate = today.minus(3, kotlinx.datetime.DateTimeUnit.DAY))

        val result = days(theAsapTask)

        assertEquals(listOf(theAsapTask), result[0].tasks)
        (1..7).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun oneTimeTaskShowsOnlyOnItsStartDate() = runTest {
        val futureDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY)
        val theOneTimeTask = task(startDate = futureDate, daysInterval = 0, asap = false)

        val result = days(theOneTimeTask)

        assertEquals(listOf(theOneTimeTask), result[3].tasks)
        (0..7).filter { it != 3 }.forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun periodicTaskRecursEveryIntervalWithNoEndDate() = runTest {
        val thePeriodicTask = task(startDate = today, daysInterval = 3, endDate = null)

        val result = days(thePeriodicTask)

        assertEquals(listOf(thePeriodicTask), result[0].tasks)
        assertTrue(result[1].tasks.isEmpty())
        assertTrue(result[2].tasks.isEmpty())
        assertEquals(listOf(thePeriodicTask), result[3].tasks)
        assertEquals(listOf(thePeriodicTask), result[6].tasks)
    }

    @Test
    fun periodicTaskStopsAfterEndDate() = runTest {
        val endDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY)
        val thePeriodicTask = task(startDate = today, daysInterval = 3, endDate = endDate)

        val result = days(thePeriodicTask)

        assertEquals(listOf(thePeriodicTask), result[0].tasks)
        assertEquals(listOf(thePeriodicTask), result[3].tasks)
        assertTrue(result[6].tasks.isEmpty())
    }

    @Test
    fun periodicTaskNotYetStartedDoesNotShow() = runTest {
        val thePeriodicTask = task(startDate = today.plus(5, kotlinx.datetime.DateTimeUnit.DAY), daysInterval = 2)

        val result = days(thePeriodicTask)

        (0..4).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
        assertEquals(listOf(thePeriodicTask), result[5].tasks)
    }

    @Test
    fun tasksWithHourSortBeforeAsapBeforeOneTimeBeforePeriodic() = runTest {
        val withHour = task(id = 1, hour = LocalTime(14, 0), startDate = today)
        val theAsapTask = task(id = 2, asap = true)
        val oneTime = task(id = 3, startDate = today, daysInterval = 0, asap = false)
        val periodic = task(id = 4, startDate = today, daysInterval = 1)

        val result = days(periodic, oneTime, theAsapTask, withHour)

        assertEquals(listOf(withHour, theAsapTask, oneTime, periodic), result[0].tasks)
    }

    @Test
    fun multipleTasksWithHourSortByHourAscending() = runTest {
        val later = task(id = 1, hour = LocalTime(18, 0), startDate = today)
        val earlier = task(id = 2, hour = LocalTime(8, 0), startDate = today)

        val result = days(later, earlier)

        assertEquals(listOf(earlier, later), result[0].tasks)
    }

    @Test
    fun emptyDayStillRendersAsAnEntryWithNoTasks() = runTest {
        val result = days()

        result.forEach { day -> assertTrue(day.tasks.isEmpty()) }
    }

    @Test
    fun propagatesDatasourceFailure() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.observeFailure = DomainError.Database("boom")
        val useCase = ObserveTasksForDashboard(datasource, FakeTodayProvider(today), dispatchers)

        val result = useCase.invoke(Unit).first()

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun reEmitsWhenTheStoredTasksChange() = runTest {
        val datasource = FakeTasksDatasource()
        val useCase = ObserveTasksForDashboard(datasource, FakeTodayProvider(today), dispatchers)

        val emissions = mutableListOf<List<DashboardDay>>()
        val job = launch {
            useCase.invoke(Unit).collect { result ->
                if (result is AppResult.Success) emissions += result.data
            }
        }
        runCurrent()
        datasource.addTask(
            TaskDraft.ofStored(
                name = "Added elsewhere",
                description = null,
                schedule = TaskSchedule.OneTime(date = today),
            ),
        )
        runCurrent()
        job.cancel()

        // No one asked it to reload: the write itself produced the second emission.
        assertEquals(2, emissions.size)
        assertTrue(emissions.first()[0].tasks.isEmpty())
        assertEquals("Added elsewhere", emissions.last()[0].tasks.single().name)
    }

    /**
     * The window has to move at midnight, and nothing in the task table changes at midnight.
     *
     * Reading the date off each task emission looked like it handled this, but the upstream only
     * re-emits when the table is written — so a phone left on the dashboard overnight went on
     * labelling yesterday's column "today" until the user happened to save something.
     */
    @Test
    fun theWindowMovesWhenTheDateRollsOverWithNoWriteToTheTable() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(startDate = today)))
        val todayProvider = FakeTodayProvider(today)
        val useCase = ObserveTasksForDashboard(datasource, todayProvider, dispatchers)

        val emissions = mutableListOf<List<DashboardDay>>()
        val job = launch {
            useCase.invoke(Unit).collect { result ->
                if (result is AppResult.Success) emissions += result.data
            }
        }
        runCurrent()
        assertEquals(today, emissions.last().first().date)

        todayProvider.setToday(today.plus(1, DateTimeUnit.DAY))
        runCurrent()
        job.cancel()

        assertEquals(
            today.plus(1, DateTimeUnit.DAY),
            emissions.last().first().date,
            "the window should start at the new today",
        )
    }
}
