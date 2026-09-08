package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskCrudUseCasesTest {

    private val sampleTask = Task(
        id = 0,
        name = "Water plants",
        description = null,
        startDate = LocalDate(2026, 9, 8),
        endDate = null,
        hour = null,
        daysInterval = 0,
        asap = false,
    )

    @Test
    fun addTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource()

        val result = AddTask(datasource).invoke(sampleTask)

        assertTrue(result is AppResult.Success)
        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun editTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(sampleTask.copy(id = 1)))

        val result = EditTask(datasource).invoke(sampleTask.copy(id = 1, name = "Water plants twice"))

        assertTrue(result is AppResult.Success)
        assertEquals("Water plants twice", datasource.tasks.single().name)
    }

    @Test
    fun deleteTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(sampleTask.copy(id = 1)))

        val result = DeleteTask(datasource).invoke(1)

        assertTrue(result is AppResult.Success)
        assertTrue(datasource.tasks.isEmpty())
    }
}
