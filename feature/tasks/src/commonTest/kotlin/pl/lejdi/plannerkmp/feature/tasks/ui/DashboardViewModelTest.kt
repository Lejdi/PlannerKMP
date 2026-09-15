package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val today = LocalDate(2026, 9, 8)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeTasksDatasource, todayProvider: FakeTodayProvider = FakeTodayProvider(today)) =
        DashboardViewModel(
            getTasksForDashboard = GetTasksForDashboard(datasource, todayProvider),
            updateTasksDates = UpdateTasksDates(datasource, todayProvider),
            markTaskComplete = MarkTaskComplete(datasource),
        )

    @Test
    fun loadsEightDaysOnScreenResumed() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        viewModel.onEvent(DashboardEvent.ScreenResumed)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(8, viewModel.state.value.days.size)
        assertEquals(today, viewModel.state.value.days.first().date)
    }

    @Test
    fun runsCleanupBeforeLoadingSoStaleTasksAreAlreadyGone() = runTest {
        // A one-time task with a past startDate is deleted by the cleanup rule (daysInterval == 0,
        // not asap, startDate < today). Asserting against the datasource's own state (rather than the
        // dashboard's returned days) is what actually proves cleanup ran as part of the reload: the
        // dashboard's forward-looking 8-day window would never show this task either way.
        val staleTask = Task(1, "Stale", null, today.minus(5, DateTimeUnit.DAY), null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask))
        val viewModel = viewModel(datasource)

        viewModel.onEvent(DashboardEvent.ScreenResumed)

        assertTrue(datasource.tasks.isEmpty(), "cleanup should have deleted the stale task from the datasource")
        assertEquals(today, datasource.lastCleanupDate)
    }

    @Test
    fun screenResumedReloadsTasksAddedOnAnotherScreenWhileThisViewModelWasRetained() = runTest {
        // Regression test: Nav3 retains this ViewModel instance across the back stack, so a task
        // added via TaskEditScreen (a different ViewModel) must show up here without recreating
        // DashboardViewModel - only re-firing ScreenResumed, exactly like returning from that screen.
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(DashboardEvent.ScreenResumed)
        assertEquals(0, viewModel.state.value.days.sumOf { it.tasks.size })

        datasource.tasks.add(Task(1, "Added elsewhere", null, today, null, null, 0, false))
        viewModel.onEvent(DashboardEvent.ScreenResumed)

        assertEquals(1, viewModel.state.value.days.sumOf { it.tasks.size })
    }

    @Test
    fun revealActionsSetsRevealedTaskId() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        viewModel.onEvent(DashboardEvent.RevealActions(taskId = 5))

        assertEquals(5, viewModel.state.value.revealedTaskId)
    }

    @Test
    fun dismissActionsClearsRevealedTaskId() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())
        viewModel.onEvent(DashboardEvent.RevealActions(taskId = 5))

        viewModel.onEvent(DashboardEvent.DismissActions)

        assertNull(viewModel.state.value.revealedTaskId)
    }

    @Test
    fun completeTaskDeletesOneTimeTaskAndReloads() = runTest {
        val oneTimeTask = Task(1, "Task", null, today, null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(oneTimeTask))
        val viewModel = viewModel(datasource)

        viewModel.onEvent(DashboardEvent.CompleteTask(oneTimeTask))

        assertEquals(0, viewModel.state.value.days.sumOf { it.tasks.size })
    }

    @Test
    fun cleanupFailureOnLoadShowsErrorEffectAndStillLoadsTheDashboard() = runTest {
        // The first datasource call of the load is the cleanup's getLastCleanupDate().
        val datasource = FakeTasksDatasource()
        datasource.failNextCall = true
        val viewModel = viewModel(datasource)

        viewModel.onEvent(DashboardEvent.ScreenResumed)

        assertEquals(DashboardEffect.ShowError("fake failure"), viewModel.effect.first())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(8, viewModel.state.value.days.size)
    }

    @Test
    fun completeTaskFailureShowsErrorEffectAndKeepsTheTask() = runTest {
        val oneTimeTask = Task(1, "Task", null, today, null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(oneTimeTask))
        val viewModel = viewModel(datasource)
        datasource.failNextCall = true

        viewModel.onEvent(DashboardEvent.CompleteTask(oneTimeTask))

        assertEquals(DashboardEffect.ShowError("fake failure"), viewModel.effect.first())
        assertEquals(listOf(oneTimeTask), datasource.tasks)
    }
}
