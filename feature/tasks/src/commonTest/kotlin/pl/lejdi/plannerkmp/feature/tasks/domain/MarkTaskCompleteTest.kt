package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkTaskCompleteTest {

    private val startDate = LocalDate(2026, 9, 1)

    private fun task(daysInterval: Int, endDate: LocalDate? = null, asap: Boolean = false) = Task(
        id = 1,
        name = "Task",
        description = null,
        startDate = startDate,
        endDate = endDate,
        hour = null,
        daysInterval = daysInterval,
        asap = asap,
    )

    @Test
    fun asapTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0, asap = true)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateAdvancesByOneInterval() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().startDate)
    }

    @Test
    fun periodicTaskAdvancingWithinEndDateIsUpdatedNotDeleted() = runTest {
        val endDate = startDate.plus(10, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().startDate)
    }

    @Test
    fun periodicTaskAdvancingPastEndDateIsDeleted() = runTest {
        val endDate = startDate.plus(5, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun propagatesDatasourceFailure() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0))).apply { failNextCall = true }

        val result = MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(result is AppResult.Failure)
    }
}
