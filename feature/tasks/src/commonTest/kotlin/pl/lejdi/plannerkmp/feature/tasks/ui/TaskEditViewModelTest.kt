package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModelTest {

    private val today = LocalDate(2026, 9, 8)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeTasksDatasource, initialTask: Task? = null) = TaskEditViewModel(
        initialTask = initialTask,
        addTask = AddTask(datasource),
        editTask = EditTask(datasource),
        deleteTask = DeleteTask(datasource),
        todayProvider = FakeTodayProvider(today),
    )

    @Test
    fun newTaskDefaultsToAsapStartingToday() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        assertEquals(TaskType.Asap, viewModel.state.value.type)
        assertEquals(today, viewModel.state.value.startDate)
    }

    @Test
    fun editingExistingTaskPrefillsAllFields() = runTest {
        val existing = Task(1, "Water plants", "Every plant", today, null, null, 0, false)

        val viewModel = viewModel(FakeTasksDatasource(), initialTask = existing)

        assertEquals("Water plants", viewModel.state.value.name)
        assertEquals("Every plant", viewModel.state.value.description)
        assertEquals(TaskType.OneTime, viewModel.state.value.type)
    }

    @Test
    fun saveWithBlankNameShowsErrorAndDoesNotSave() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertTrue(viewModel.state.value.nameError)
        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun savingAsapTaskAlwaysResetsStartDateToToday() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Call mom"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Asap))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(10, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(today, datasource.tasks.single().startDate)
        assertTrue(datasource.tasks.single().asap)
    }

    @Test
    fun savingNonPeriodicTypeClearsEndDate() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("One-off"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.OneTime))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today.plus(30, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertNull(datasource.tasks.single().endDate)
    }

    @Test
    fun savingPeriodicTypeKeepsEndDate() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        val endDate = today.plus(30, DateTimeUnit.DAY)
        viewModel.onEvent(TaskEditEvent.NameChanged("Recurring"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(endDate))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("7"))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(endDate, datasource.tasks.single().endDate)
        assertEquals(7, datasource.tasks.single().daysInterval)
    }

    @Test
    fun savingPeriodicTypeWithBlankIntervalSilentlySavesZero() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Recurring"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(0, datasource.tasks.single().daysInterval)
    }

    @Test
    fun savingOneTimeTypeForcesDaysIntervalToZeroEvenIfFieldHadAStaleValue() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("One-off"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("5"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.OneTime))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(0, datasource.tasks.single().daysInterval)
    }

    @Test
    fun changingStartDatePastCurrentEndDateSnapsEndDateForward() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today.plus(2, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(5, DateTimeUnit.DAY)))

        assertEquals(today.plus(5, DateTimeUnit.DAY), viewModel.state.value.endDate)
    }

    @Test
    fun deletingBrandNewTaskJustNavigatesBackWithoutCallingAddTask() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource, initialTask = null)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun deletingExistingTaskCallsDeleteTask() = runTest {
        val existing = Task(1, "Task", null, today, null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(existing))
        val viewModel = viewModel(datasource, initialTask = existing)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)

        assertTrue(datasource.tasks.isEmpty())
    }
}
