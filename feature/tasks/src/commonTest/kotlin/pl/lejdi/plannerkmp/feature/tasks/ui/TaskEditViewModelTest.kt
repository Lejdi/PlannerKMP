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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.feature.tasks.domain.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskField
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksWrite
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private val storedTask = Task(
        id = 1,
        name = "Water plants",
        description = "The big ones",
        schedule = TaskSchedule.OneTime(date = today, hour = LocalTime(9, 30)),
    )

    private fun viewModel(
        taskId: Long?,
        datasource: FakeTasksDatasource = FakeTasksDatasource(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = TaskEditViewModel(
        taskId,
        savedStateHandle,
        NoOpLogger(),
        datasource,
        FakeTodayProvider(today),
    )

    @Test
    fun anEmptyFormStartsAsapAndDatedToday() = runTest {
        val viewModel = viewModel(taskId = null)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(TaskType.Asap, state.form.type)
        // The field itself stays unset until the user picks a date; the screen shows today.
        assertNull(state.form.startDate)
        assertEquals(today, state.effectiveStartDate)
        assertFalse(state.isEditingExistingTask)
    }

    @Test
    fun anExistingTaskIsLoadedByIdRatherThanPassedIn() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Water plants", state.form.name)
        assertEquals("The big ones", state.form.description)
        assertEquals(LocalTime(9, 30), state.form.hour)
        assertTrue(state.isEditingExistingTask)
    }

    @Test
    fun theFormReflectsTheCurrentStoredRowNotAStaleSnapshot() = runTest {
        // The daily cleanup rolled the task forward after the user tapped it on the dashboard.
        val rolledForward = storedTask.copy(
            schedule = TaskSchedule.Periodic(startDate = today.plus(3, DateTimeUnit.DAY), daysInterval = 3),
        )
        val datasource = FakeTasksDatasource(initialTasks = listOf(rolledForward))

        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        assertEquals(today.plus(3, DateTimeUnit.DAY), viewModel.state.value.form.startDate)
    }

    @Test
    fun navigatesBackWhenTheTaskIsAlreadyGone() = runTest {
        val viewModel = viewModel(taskId = 404)
        runCurrent()

        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun aFailedLoadReportsAMessage() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        datasource.observeTaskFailure = DomainError.Database("boom")

        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        assertEquals(TaskEditMessage.LoadFailed, viewModel.state.value.message?.value)
    }

    /**
     * The failure this screen used to have. A failed load left `isLoading = false` with an empty
     * form that still carried the real taskId, and the form rendered as if it were editable — so
     * typing a name and pressing Save wrote a name-only task over the stored row, wiping its
     * description, dates, hour and recurrence.
     */
    @Test
    fun aFailedLoadIsTerminalSoTheFormIsNeverShownEmptyOverARealTask() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        datasource.observeTaskFailure = DomainError.Database("boom")

        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isFormSeeded)
        assertTrue(state.hasTerminalLoadFailure, "the screen must render an error, not a blank form")
        assertEquals(storedTask, datasource.tasks.single())
    }

    @Test
    fun retryResubscribesAfterAFailedLoad() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        datasource.observeTaskFailure = DomainError.Database("boom")
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        assertTrue(viewModel.state.value.hasTerminalLoadFailure)

        datasource.observeTaskFailure = null
        viewModel.onEvent(TaskEditEvent.RetryClicked)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.hasTerminalLoadFailure)
        assertEquals("Water plants", state.form.name)
    }

    /** A new task's form is empty on purpose, so it must not be mistaken for an unread one. */
    @Test
    fun anEmptyNewTaskFormIsNotATerminalFailure() = runTest {
        val viewModel = viewModel(taskId = null)
        runCurrent()

        assertFalse(viewModel.state.value.hasTerminalLoadFailure)
    }

    /** A later emission must not overwrite what the user is in the middle of typing. */
    @Test
    fun aLaterEmissionDoesNotClobberTheUsersEdits() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        viewModel.onEvent(TaskEditEvent.NameChanged("Half-typed n"))
        datasource.editTask(storedTask.copy(description = "Changed elsewhere"))
        runCurrent()

        assertEquals("Half-typed n", viewModel.state.value.form.name)
    }

    /** Staying subscribed is what lets the screen notice a delete it did not make. */
    @Test
    fun navigatesBackWhenTheRowIsDeletedWhileOpen() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        datasource.deleteTask(1)
        runCurrent()

        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun savingOntoADeletedRowSaysSoRatherThanOfferingAnotherFailedSave() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        viewModel.onEvent(TaskEditEvent.NameChanged("Renamed"))

        datasource.deleteTask(1)
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertEquals(TaskEditMessage.TaskNoLongerExists, viewModel.state.value.message?.value)
    }

    /**
     * Process death: the ViewModel is destroyed while navigation restores the same screen. The
     * half-filled form has to come back with it — restored navigation makes losing it more
     * conspicuous, not less.
     */
    @Test
    fun theHalfFilledFormSurvivesProcessDeath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val before = viewModel(taskId = null, savedStateHandle = savedStateHandle)
        before.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        before.onEvent(TaskEditEvent.DescriptionChanged("Twice a day"))
        before.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        before.onEvent(TaskEditEvent.DaysIntervalChanged("3"))
        runCurrent()

        val after = viewModel(taskId = null, savedStateHandle = savedStateHandle)
        runCurrent()

        val state = after.state.value
        assertEquals("Feed cat", state.form.name)
        assertEquals("Twice a day", state.form.description)
        assertEquals(TaskType.Periodic, state.form.type)
        assertEquals("3", state.form.daysInterval)
    }

    @Test
    fun anUnreadableSavedFormFallsBackToAnEmptyOneRatherThanCrashing() = runTest {
        val savedStateHandle = SavedStateHandle()
        savedStateHandle["pl.lejdi.plannerkmp.mvi.input"] = "{not json"

        val viewModel = viewModel(taskId = null, savedStateHandle = savedStateHandle)
        runCurrent()

        assertEquals("", viewModel.state.value.form.name)
    }

    @Test
    fun editingFieldsUpdatesTheState() = runTest {
        val viewModel = viewModel(taskId = null)

        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        viewModel.onEvent(TaskEditEvent.DescriptionChanged("Twice"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("2"))
        viewModel.onEvent(TaskEditEvent.HourChanged(LocalTime(7, 0)))

        val state = viewModel.state.value
        assertEquals("Feed cat", state.form.name)
        assertEquals("Twice", state.form.description)
        assertEquals(TaskType.Periodic, state.form.type)
        assertEquals("2", state.form.daysInterval)
        assertEquals(LocalTime(7, 0), state.form.hour)
    }

    @Test
    fun movingTheStartDateForwardDragsAnEarlierEndDateWithIt() = runTest {
        val viewModel = viewModel(taskId = null)
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today))

        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(5, DateTimeUnit.DAY)))

        assertEquals(today.plus(5, DateTimeUnit.DAY), viewModel.state.value.form.endDate)
    }

    @Test
    fun dialogVisibilityIsStateNotComposableLocals() = runTest {
        val viewModel = viewModel(taskId = null)

        viewModel.onEvent(TaskEditEvent.DialogRequested(TaskEditDialog.StartDate))
        assertEquals(TaskEditDialog.StartDate, viewModel.state.value.form.activeDialog)

        viewModel.onEvent(TaskEditEvent.DialogDismissed)
        assertNull(viewModel.state.value.form.activeDialog)
    }

    @Test
    fun aBlankNameIsRejectedByTheDomainAndMarkedOnTheField() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("   "))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertTrue(TaskField.Name in viewModel.state.value.form)
        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun typingInTheNameClearsItsError() = runTest {
        val viewModel = viewModel(taskId = null)
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()
        assertTrue(TaskField.Name in viewModel.state.value.form)

        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))

        assertFalse(TaskField.Name in viewModel.state.value.form)
    }

    @Test
    fun aNonPositiveIntervalIsRejectedOnTheIntervalField() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("0"))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertTrue(TaskField.DaysInterval in viewModel.state.value.form)
        assertFalse(TaskField.Name in viewModel.state.value.form)
        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun anEndDateBeforeTheStartDateIsRejectedOnTheEndDateField() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("2"))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        // Set directly: StartDateChanged deliberately snaps the end date forward.
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today.minus(1, DateTimeUnit.DAY)))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertTrue(TaskField.EndDate in viewModel.state.value.form)
        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun savingANewTaskAddsItAndNavigatesBack() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertEquals("Feed cat", datasource.tasks.single().name)
        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun savingAnExistingTaskUpdatesItInPlace() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        viewModel.onEvent(TaskEditEvent.NameChanged("Water plants twice"))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertEquals(1, datasource.tasks.size)
        assertEquals("Water plants twice", datasource.tasks.single().name)
        assertEquals(1L, datasource.tasks.single().id)
    }

    @Test
    fun aFailedSaveReportsAMessageAndStaysOnTheForm() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))
        datasource.failNext(TasksWrite.Add)

        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertEquals(TaskEditMessage.SaveFailed, viewModel.state.value.message?.value)
        assertEquals("Feed cat", viewModel.state.value.form.name)
    }

    @Test
    fun deletingAnExistingTaskRemovesItAndNavigatesBack() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        viewModel.onEvent(TaskEditEvent.DeleteConfirmed)
        runCurrent()

        assertTrue(datasource.tasks.isEmpty())
        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun deletingAnUnsavedTaskJustNavigatesBack() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        viewModel.onEvent(TaskEditEvent.DeleteConfirmed)

        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun aFailedDeleteReportsAMessage() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        datasource.failNext(TasksWrite.Delete)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        viewModel.onEvent(TaskEditEvent.DeleteConfirmed)
        runCurrent()

        assertEquals(TaskEditMessage.DeleteFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun anAsapTaskIsSavedAsStartingToday() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("Call plumber"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Asap))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(9, DateTimeUnit.DAY)))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        val saved = datasource.tasks.single()
        assertEquals(TaskSchedule.Asap(createdOn = today), saved.schedule)
    }

    /**
     * The re-entry guard. Save used to read the state, launch and return, so two taps before the
     * first write came back ran two inserts and created two rows — and the screen looked identical
     * throughout, which is what invited the second tap.
     */
    @Test
    fun savingTwiceInARowAddsOneTask() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.blockWrites = true
        val viewModel = viewModel(taskId = null, datasource = datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))

        viewModel.onEvent(TaskEditEvent.SaveClicked)
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        datasource.releaseWrites()
        runCurrent()

        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun theFormIsMarkedSubmittingWhileAWriteIsInFlight() = runTest {
        val datasource = FakeTasksDatasource()
        datasource.blockWrites = true
        val viewModel = viewModel(taskId = null, datasource = datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Feed cat"))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertTrue(viewModel.state.value.isSubmitting)
        assertFalse(viewModel.state.value.canSubmit, "the screen disables Save and Delete on this")

        datasource.releaseWrites()
        runCurrent()
        assertFalse(viewModel.state.value.isSubmitting)
    }

    /** Delete is irreversible and sits next to Save, so it asks first. */
    @Test
    fun deleteAsksBeforeItDeletes() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        runCurrent()

        assertEquals(TaskEditDialog.ConfirmDelete, viewModel.state.value.form.activeDialog)
        assertEquals(storedTask, datasource.tasks.single(), "nothing is deleted until confirmed")
    }

    @Test
    fun dismissingTheDeleteConfirmationKeepsTheTask() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        viewModel.onEvent(TaskEditEvent.DialogDismissed)
        runCurrent()

        assertNull(viewModel.state.value.form.activeDialog)
        assertEquals(storedTask, datasource.tasks.single())
    }

    /**
     * Every broken field is marked from one Save.
     *
     * Validation used to stop at the first failure, so a form with a blank name *and* a zero
     * interval reported them one submission at a time.
     */
    @Test
    fun everyInvalidFieldIsMarkedFromASingleSave() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(taskId = null, datasource = datasource)

        viewModel.onEvent(TaskEditEvent.NameChanged("  "))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("0"))
        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        val form = viewModel.state.value.form
        assertTrue(TaskField.Name in form)
        assertTrue(TaskField.DaysInterval in form)
        assertTrue(datasource.tasks.isEmpty())
    }

    /**
     * The default start date tracks the clock instead of being captured once at construction.
     *
     * CLAUDE.md is emphatic that anything rendering or scheduling against a date must use
     * `todayFlow`, and this screen was the one place still calling `today()` — so a form left open
     * across midnight defaulted to yesterday.
     */
    @Test
    fun theDefaultStartDateFollowsTheDateRollingOver() = runTest {
        val todayProvider = FakeTodayProvider(today)
        val viewModel = TaskEditViewModel(
            null,
            SavedStateHandle(),
            NoOpLogger(),
            FakeTasksDatasource(),
            todayProvider,
        )
        runCurrent()
        assertEquals(today, viewModel.state.value.effectiveStartDate)

        todayProvider.setToday(today.plus(1, DateTimeUnit.DAY))
        runCurrent()

        assertEquals(today.plus(1, DateTimeUnit.DAY), viewModel.state.value.effectiveStartDate)
    }

    /**
     * Deleting a row that is already gone is the outcome the tap wanted.
     *
     * Unreachable until the fake could name the error it returns: with only a generic Database
     * failure available, this branch could not be told apart from a real malfunction, and the user
     * would have been kept on a screen for a task that no longer exists.
     */
    @Test
    fun deletingAnAlreadyDeletedTaskNavigatesBackRatherThanReportingAFailure() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        datasource.failNext(TasksWrite.Delete, DomainError.NotFound("already gone"))

        viewModel.onEvent(TaskEditEvent.DeleteClicked)
        viewModel.onEvent(TaskEditEvent.DeleteConfirmed)
        runCurrent()

        assertNull(viewModel.state.value.message, "a vanished row is not a failure to report")
        assertEquals(TaskEditEffect.NavigateBack, viewModel.effect.first())
    }

    /** A save onto a vanished row gets its own wording, because retrying cannot fix it. */
    @Test
    fun savingOntoAVanishedRowSaysSoRatherThanOfferingAnotherAttempt() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        datasource.failNext(TasksWrite.Edit, DomainError.NotFound("already gone"))

        viewModel.onEvent(TaskEditEvent.SaveClicked)
        runCurrent()

        assertEquals(TaskEditMessage.TaskNoLongerExists, viewModel.state.value.message?.value)
    }

    /**
     * A stream that fails *after* the form is seeded is a snackbar, not a full-screen error.
     *
     * Only reachable now that the fake can fail a live stream: with the failure decided at
     * subscription time the form could never be populated first, so the non-terminal branch — the
     * one that keeps the user's typing on screen — was untested.
     */
    @Test
    fun aFailureAfterTheFormIsSeededKeepsTheFormOnScreen() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val viewModel = viewModel(taskId = 1, datasource = datasource)
        runCurrent()
        assertTrue(viewModel.state.value.isFormSeeded)

        datasource.failLiveStreams()
        runCurrent()

        assertEquals(TaskEditMessage.LoadFailed, viewModel.state.value.message?.value)
        assertFalse(
            viewModel.state.value.hasTerminalLoadFailure,
            "the form is populated, so this is a snackbar rather than a blank screen",
        )
        assertEquals("Water plants", viewModel.state.value.form.name)
    }

    /**
     * Restoring a form that was already seeded must not go back to the spinner.
     *
     * The existing process-death test uses a new task, so `isFormSeeded = true` — the branch that
     * stops a re-read clobbering what the user typed — was never exercised.
     */
    @Test
    fun aSeededFormComesBackWithoutReturningToTheSpinner() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(storedTask))
        val handle = SavedStateHandle()
        val first = viewModel(taskId = 1, datasource = datasource, savedStateHandle = handle)
        runCurrent()
        first.onEvent(TaskEditEvent.NameChanged("Half typed"))

        val restored = viewModel(taskId = 1, datasource = datasource, savedStateHandle = handle)

        assertFalse(restored.state.value.isLoading, "the form came back filled in")
        assertTrue(restored.state.value.isFormSeeded)
        assertEquals("Half typed", restored.state.value.form.name)
    }
}
