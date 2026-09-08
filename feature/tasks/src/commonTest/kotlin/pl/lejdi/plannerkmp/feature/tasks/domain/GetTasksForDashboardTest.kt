package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetTasksForDashboardTest {

    private val today = LocalDate(2026, 9, 8)

    private fun task(
        id: Long = 1,
        name: String = "Task",
        startDate: LocalDate = today,
        endDate: LocalDate? = null,
        hour: LocalTime? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = Task(id, name, null, startDate, endDate, hour, daysInterval, asap)

    private suspend fun days(vararg tasks: Task): List<DashboardDay> {
        val datasource = FakeTasksDatasource(initialTasks = tasks.toList())
        val useCase = GetTasksForDashboard(datasource, FakeTodayProvider(today))
        val result = useCase.invoke(Unit)
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
        val asapTask = task(asap = true, startDate = today.minus(3, kotlinx.datetime.DateTimeUnit.DAY))

        val result = days(asapTask)

        assertEquals(listOf(asapTask), result[0].tasks)
        (1..7).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun oneTimeTaskShowsOnlyOnItsStartDate() = runTest {
        val futureDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY)
        val oneTimeTask = task(startDate = futureDate, daysInterval = 0, asap = false)

        val result = days(oneTimeTask)

        assertEquals(listOf(oneTimeTask), result[3].tasks)
        (0..7).filter { it != 3 }.forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun periodicTaskRecursEveryIntervalWithNoEndDate() = runTest {
        val periodicTask = task(startDate = today, daysInterval = 3, endDate = null)

        val result = days(periodicTask)

        assertEquals(listOf(periodicTask), result[0].tasks)
        assertTrue(result[1].tasks.isEmpty())
        assertTrue(result[2].tasks.isEmpty())
        assertEquals(listOf(periodicTask), result[3].tasks)
        assertEquals(listOf(periodicTask), result[6].tasks)
    }

    @Test
    fun periodicTaskStopsAfterEndDate() = runTest {
        val periodicTask = task(startDate = today, daysInterval = 3, endDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY))

        val result = days(periodicTask)

        assertEquals(listOf(periodicTask), result[0].tasks)
        assertEquals(listOf(periodicTask), result[3].tasks)
        assertTrue(result[6].tasks.isEmpty())
    }

    @Test
    fun periodicTaskNotYetStartedDoesNotShow() = runTest {
        val periodicTask = task(startDate = today.plus(5, kotlinx.datetime.DateTimeUnit.DAY), daysInterval = 2)

        val result = days(periodicTask)

        (0..4).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
        assertEquals(listOf(periodicTask), result[5].tasks)
    }

    @Test
    fun tasksWithHourSortBeforeAsapBeforeOneTimeBeforePeriodic() = runTest {
        val withHour = task(id = 1, hour = LocalTime(14, 0), startDate = today)
        val asapTask = task(id = 2, asap = true)
        val oneTime = task(id = 3, startDate = today, daysInterval = 0, asap = false)
        val periodic = task(id = 4, startDate = today, daysInterval = 1)

        val result = days(periodic, oneTime, asapTask, withHour)

        assertEquals(listOf(withHour, asapTask, oneTime, periodic), result[0].tasks)
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
        val datasource = FakeTasksDatasource().apply { failNextCall = true }
        val useCase = GetTasksForDashboard(datasource, FakeTodayProvider(today))

        val result = useCase.invoke(Unit)

        assertTrue(result is AppResult.Failure)
    }
}
