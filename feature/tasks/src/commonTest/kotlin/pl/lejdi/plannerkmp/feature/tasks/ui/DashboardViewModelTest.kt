package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.feature.tasks.domain.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.ObserveTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskDraft
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksWrite
import pl.lejdi.plannerkmp.feature.tasks.domain.anchorDate
import pl.lejdi.plannerkmp.feature.tasks.domain.oneTimeTask
import pl.lejdi.plannerkmp.feature.tasks.domain.periodicTask
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

    private fun task(id: Long = 1, startDate: LocalDate = today, daysInterval: Int = 0) =
        if (daysInterval > 0) {
            periodicTask(id = id, name = "Task $id", startDate = startDate, daysInterval = daysInterval)
        } else {
            oneTimeTask(id = id, name = "Task $id", date = startDate)
        }

    private fun viewModel(
        datasource: FakeTasksDatasource = FakeTasksDatasource(),
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): DashboardViewModel = DashboardViewModel(
        savedStateHandle,
        NoOpLogger(),
        // Unconfined for both dispatchers, so the use case's flowOn runs inline.
        ObserveTasksForDashboard(datasource, todayProvider, TestCoroutineDispatchers(Dispatchers.Unconfined)),
        MarkTaskComplete(datasource),
    )

    @Test
    fun loadsTheEightDayWindowOnCreation() = runTest {
        val viewModel = viewModel(FakeTasksDatasource(initialTasks = listOf(task())))
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(8, state.days.size)
        assertEquals(1, state.days.first().tasks.size)
    }

    @Test
    fun picksUpAChangeMadeElsewhereWithoutBeingAskedToReload() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        assertTrue(viewModel.state.value.days.first().tasks.isEmpty())

        // Stands in for the edit screen saving a task while the dashboard is on the back stack.
        datasource.addTask(
            TaskDraft.ofStored("Added elsewhere", null, TaskSchedule.OneTime(today)),
        )
        runCurrent()

        assertEquals("Added elsewhere", viewModel.state.value.days.first().tasks.single().name)
    }

    @Test
    fun revealAndDismissToggleTheRevealedTask() = runTest {
        val viewModel = viewModel(FakeTasksDatasource(initialTasks = listOf(task())))
        runCurrent()

        viewModel.onEvent(DashboardEvent.RevealActions(1))
        assertEquals(1L, viewModel.state.value.revealedTaskId)

        viewModel.onEvent(DashboardEvent.DismissActions)
        assertNull(viewModel.state.value.revealedTaskId)
    }

    @Test
    fun completingATaskRemovesItAndClearsTheReveal() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task()))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(DashboardEvent.RevealActions(1))

        viewModel.onEvent(DashboardEvent.CompleteTask(task().id, today))
        runCurrent()

        assertTrue(datasource.tasks.isEmpty())
        assertNull(viewModel.state.value.revealedTaskId)
        assertTrue(viewModel.state.value.days.first().tasks.isEmpty())
    }

    @Test
    fun addAndEditRaiseNavigationEffectsCarryingOnlyTheId() = runTest {
        val viewModel = viewModel(FakeTasksDatasource(initialTasks = listOf(task(id = 7))))
        runCurrent()

        viewModel.onEvent(DashboardEvent.AddTaskClicked)
        assertEquals(DashboardEffect.NavigateToAddTask, viewModel.effect.first())

        viewModel.onEvent(DashboardEvent.EditTaskClicked(7))
        assertEquals(DashboardEffect.NavigateToEditTask(7), viewModel.effect.first())
    }

    @Test
    fun aFailedLoadSurfacesAMessageInsteadOfRawDriverText() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.observeFailure = DomainError.Database("UNIQUE constraint failed: taskEntity.id")
        val viewModel = viewModel(datasource)
        runCurrent()

        assertEquals(DashboardMessage.LoadFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun aFailedCompletionReportsWithoutLosingTheList() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task()))
        val viewModel = viewModel(datasource)
        runCurrent()
        // task() is a one-off, so completing it deletes rather than reschedules.
        datasource.failNext(TasksWrite.Delete)

        viewModel.onEvent(DashboardEvent.CompleteTask(task().id, today))
        runCurrent()

        assertEquals(DashboardMessage.CompleteFailed, viewModel.state.value.message?.value)
        assertEquals(1, viewModel.state.value.days.first().tasks.size)
    }

    @Test
    fun aLoadFailureWithNothingToShowIsTerminal() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()

        // Nothing to put a snackbar over, so the screen shows a persistent retry instead.
        assertTrue(viewModel.state.value.hasTerminalLoadFailure)
    }

    @Test
    fun aFailureWithContentStillOnScreenIsNotTerminal() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task()))
        val viewModel = viewModel(datasource)
        runCurrent()
        // task() is a one-off, so completing it deletes rather than reschedules.
        datasource.failNext(TasksWrite.Delete)

        viewModel.onEvent(DashboardEvent.CompleteTask(task().id, today))
        runCurrent()

        assertEquals(DashboardMessage.CompleteFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.hasTerminalLoadFailure)
    }

    @Test
    fun messageShownClearsTheMessage() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()
        assertEquals(DashboardMessage.LoadFailed, viewModel.state.value.message?.value)

        viewModel.onEvent(DashboardEvent.MessageShown)

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun retryResubscribesAfterAFailure() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task()))
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()
        assertEquals(DashboardMessage.LoadFailed, viewModel.state.value.message?.value)

        // Whatever was wrong is no longer wrong; Retry has to actually re-observe.
        datasource.observeFailure = null
        viewModel.onEvent(DashboardEvent.RetryClicked)
        runCurrent()

        assertNull(viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.days.first().tasks.size)
    }

    /**
     * Two taps on one card must land one completion.
     *
     * The fake has had `blockWrites` for exactly this since it was written, and no dashboard test
     * ever used it — so the guard whose absence advanced a periodic task by two intervals was
     * covered by nothing.
     */
    @Test
    fun tappingTheSameCardTwiceCompletesItOnce() = runTest {
        val periodic = task(id = 1, startDate = today, daysInterval = 2)
        val datasource = FakeTasksDatasource(initialTasks = listOf(periodic))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(DashboardEvent.CompleteTask(periodic.id, today))
        viewModel.onEvent(DashboardEvent.CompleteTask(periodic.id, today))
        datasource.releaseWrites()
        runCurrent()

        assertEquals(
            today.plus(2, DateTimeUnit.DAY),
            datasource.tasks.single().anchorDate,
            "the second tap must not have advanced it a second interval",
        )
    }

    /**
     * A completion in flight on one card must not disable another.
     *
     * `isSubmitting` was one boolean for the whole screen, so the guard dropped a tap on any other
     * card — on a day with several tasks, which is the ordinary case.
     */
    @Test
    fun completingOneTaskLeavesTheOthersTappable() = runTest {
        val first = task(id = 1, startDate = today)
        val second = task(id = 2, startDate = today)
        val datasource = FakeTasksDatasource(initialTasks = listOf(first, second))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(DashboardEvent.CompleteTask(first.id, today))
        assertTrue(viewModel.state.value.isCompleting(1))
        assertFalse(viewModel.state.value.isCompleting(2), "the other card stays live")

        viewModel.onEvent(DashboardEvent.CompleteTask(second.id, today))
        datasource.releaseWrites()
        runCurrent()

        assertTrue(datasource.tasks.isEmpty(), "both one-off tasks were completed")
    }

    /** The one field this screen persists, and the only reason it is a RestorableViewModel. */
    @Test
    fun theRevealedCardSurvivesProcessDeath() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task()))
        val handle = SavedStateHandle()
        val first = viewModel(datasource, savedStateHandle = handle)
        runCurrent()
        first.onEvent(DashboardEvent.RevealActions(1))

        val restored = viewModel(datasource, savedStateHandle = handle)

        assertEquals(1L, restored.state.value.revealedTaskId)
    }
}
