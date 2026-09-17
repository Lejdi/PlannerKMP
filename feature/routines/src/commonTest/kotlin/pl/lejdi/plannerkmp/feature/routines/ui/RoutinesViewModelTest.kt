package pl.lejdi.plannerkmp.feature.routines.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.feature.routines.domain.FakeRoutinesDatasource
import pl.lejdi.plannerkmp.feature.routines.domain.ObserveRoutinesForToday
import pl.lejdi.plannerkmp.feature.routines.domain.Routine
import pl.lejdi.plannerkmp.feature.routines.domain.RoutinesWrite
import pl.lejdi.plannerkmp.feature.routines.domain.ToggleRoutineDone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    private val today = LocalDate(2026, 9, 17)
    private val stretch = Routine(id = 1, name = "Stretch", description = "Ten minutes", completedOn = null)
    private val read = Routine(id = 2, name = "Read", description = null, completedOn = null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        datasource: FakeRoutinesDatasource,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
    ): RoutinesViewModel {
        val dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined)
        return RoutinesViewModel(
            savedStateHandle,
            NoOpLogger(),
            ObserveRoutinesForToday(datasource, todayProvider, dispatchers),
            ToggleRoutineDone(datasource, todayProvider),
            datasource,
        )
    }

    @Test
    fun loadsTheListOnCreation() = runTest {
        val viewModel = viewModel(FakeRoutinesDatasource(listOf(stretch)))
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf(1L), viewModel.state.value.routines.map { it.id })
        assertFalse(viewModel.state.value.routines.single().isDoneToday)
    }

    @Test
    fun aFailedLoadIsTerminalAndRetryResubscribes() = runTest {
        val datasource = FakeRoutinesDatasource()
        datasource.observeFailure = DomainError.Database("driver gone")
        val viewModel = viewModel(datasource)
        runCurrent()

        assertTrue(viewModel.state.value.hasTerminalLoadFailure)

        datasource.observeFailure = null
        viewModel.onEvent(RoutinesEvent.RetryClicked)
        runCurrent()

        assertFalse(viewModel.state.value.loadFailed)
    }

    /**
     * A stream that dies *after* emitting leaves content on screen, so the failure is a snackbar
     * rather than a full-screen error — the non-terminal branch, which is unreachable except
     * through a source that fails late.
     */
    @Test
    fun aStreamThatFailsAfterEmittingKeepsTheListAndReportsIt() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()

        datasource.failLiveStream(DomainError.Database("driver gone"))
        runCurrent()

        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.hasTerminalLoadFailure, "there is still a list to show")
        assertEquals(RoutineMessage.LoadFailed, viewModel.state.value.message?.value)
        assertEquals(1, viewModel.state.value.routines.size)
    }

    @Test
    fun tickingARoutineMarksItDoneToday() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        assertTrue(viewModel.state.value.routines.single().isDoneToday)
        assertEquals(today, datasource.routines.single().completedOn)
        assertTrue(viewModel.state.value.togglingRoutineIds.isEmpty())
    }

    @Test
    fun untickingARoutineClearsIt() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch.copy(completedOn = today)))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = false))
        runCurrent()

        assertFalse(viewModel.state.value.routines.single().isDoneToday)
        assertNull(datasource.routines.single().completedOn)
        // Back out of the done section and into the list proper.
        assertEquals(listOf(1L), viewModel.state.value.pendingRoutines.map { it.id })
        assertTrue(viewModel.state.value.doneRoutines.isEmpty())
    }

    /** Ticking is what takes a routine out of the list the user is working through. */
    @Test
    fun tickingARoutineMovesItFromPendingToDone() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch, read))
        val viewModel = viewModel(datasource)
        runCurrent()
        assertEquals(listOf(1L, 2L), viewModel.state.value.pendingRoutines.map { it.id })
        assertTrue(viewModel.state.value.doneRoutines.isEmpty())

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        assertEquals(listOf(2L), viewModel.state.value.pendingRoutines.map { it.id })
        assertEquals(listOf(1L), viewModel.state.value.doneRoutines.map { it.id })
    }

    /** A routine ticked on an earlier day is pending again, not filed under done. */
    @Test
    fun aRoutineTickedYesterdayCountsAsPending() = runTest {
        val yesterday = LocalDate(2026, 9, 16)
        val datasource = FakeRoutinesDatasource(listOf(stretch.copy(completedOn = yesterday)))
        val viewModel = viewModel(datasource)
        runCurrent()

        assertEquals(listOf(1L), viewModel.state.value.pendingRoutines.map { it.id })
        assertTrue(viewModel.state.value.doneRoutines.isEmpty())
    }

    @Test
    fun theDoneSectionStartsCollapsedAndToggles() = runTest {
        val viewModel = viewModel(FakeRoutinesDatasource(listOf(stretch.copy(completedOn = today))))
        runCurrent()

        assertFalse(viewModel.state.value.doneSectionExpanded, "it starts out of the way")

        viewModel.onEvent(RoutinesEvent.DoneSectionToggled)
        assertTrue(viewModel.state.value.doneSectionExpanded)

        viewModel.onEvent(RoutinesEvent.DoneSectionToggled)
        assertFalse(viewModel.state.value.doneSectionExpanded)
    }

    /** Unlike the delete dialog, an expanded section is a harmless choice worth restoring. */
    @Test
    fun theDoneSectionExpansionSurvivesProcessDeath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeRoutinesDatasource(listOf(stretch.copy(completedOn = today)))
        val first = viewModel(datasource, savedStateHandle)
        runCurrent()
        first.onEvent(RoutinesEvent.DoneSectionToggled)
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle)
        runCurrent()

        assertTrue(restored.state.value.doneSectionExpanded)
    }

    /** Per row: a second tap on the *same* row is the double-tap this guards against. */
    @Test
    fun aSecondTapOnTheSameRowIsDroppedWhileItsWriteIsOpen() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()
        assertTrue(viewModel.state.value.isToggling(1))

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = false))
        runCurrent()
        datasource.releaseWrites()
        runCurrent()

        assertEquals(today, datasource.routines.single().completedOn, "the second tap was dropped")
    }

    /** A tap on a *different* row is the user working down the list, and must not be blocked. */
    @Test
    fun anotherRowStaysTappableWhileOneWriteIsOpen() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch, read))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        assertFalse(viewModel.state.value.isToggling(2), "row 2 has no write of its own open")

        // Released so the test does not leave a coroutine parked on the gate.
        datasource.releaseWrites()
        runCurrent()
    }

    @Test
    fun aFailedTickReportsItAndClearsTheRow() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(RoutinesWrite.UpdateCompletedOn, DomainError.Database("disk full"))

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        assertEquals(RoutineMessage.ToggleFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.isToggling(1))
    }

    /** Already gone is the outcome the tap wanted; the list flow has re-emitted without it. */
    @Test
    fun tickingAVanishedRoutineReportsNothing() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(RoutinesWrite.UpdateCompletedOn, DomainError.NotFound("gone"))

        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun openingTheAddEditorStartsItEmpty() = runTest {
        val viewModel = viewModel(FakeRoutinesDatasource(listOf(stretch)))
        runCurrent()

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))

        val editor = viewModel.state.value.editor
        assertEquals(EditorTarget.New, editor?.target)
        assertEquals("", editor?.name)
        assertEquals("", editor?.description)
    }

    @Test
    fun openingTheEditEditorPrefillsFromTheRoutine() = runTest {
        val viewModel = viewModel(FakeRoutinesDatasource(listOf(stretch)))
        runCurrent()

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(1)))

        val editor = viewModel.state.value.editor
        assertEquals("Stretch", editor?.name)
        assertEquals("Ten minutes", editor?.description)
    }

    @Test
    fun confirmingAnAddStoresTheRoutineAndClosesTheEditor() = runTest {
        val datasource = FakeRoutinesDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(RoutinesEvent.EditorNameChanged("Stretch"))
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()

        assertNull(viewModel.state.value.editor)
        assertEquals("Stretch", datasource.routines.single().name)
    }

    @Test
    fun aBlankNameMarksTheFieldRatherThanRaisingAMessage() = runTest {
        val datasource = FakeRoutinesDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(RoutinesEvent.EditorNameChanged("   "))
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()

        assertTrue(viewModel.state.value.editor?.nameError == true)
        assertNull(viewModel.state.value.message)
        assertTrue(datasource.routines.isEmpty())
    }

    @Test
    fun typingClearsTheNameError() = runTest {
        val viewModel = viewModel(FakeRoutinesDatasource())
        runCurrent()
        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()
        assertTrue(viewModel.state.value.editor?.nameError == true)

        viewModel.onEvent(RoutinesEvent.EditorNameChanged("S"))

        assertFalse(viewModel.state.value.editor?.nameError == true)
    }

    @Test
    fun aDoubleTapOnSaveWritesOnce() = runTest {
        val datasource = FakeRoutinesDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(RoutinesEvent.EditorNameChanged("Stretch"))
        datasource.blockWrites = true

        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()
        datasource.releaseWrites()
        runCurrent()

        assertEquals(1, datasource.routines.size)
    }

    /** Editing writes the text columns only, so it must not disturb today's tick. */
    @Test
    fun renamingARoutineLeavesItTicked() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch.copy(completedOn = today)))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(1)))
        viewModel.onEvent(RoutinesEvent.EditorNameChanged("Stretch well"))
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()

        assertEquals("Stretch well", datasource.routines.single().name)
        assertTrue(viewModel.state.value.routines.single().isDoneToday)
    }

    @Test
    fun savingOntoAVanishedRowClosesTheEditorAndSaysSo() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(RoutinesWrite.UpdateDetails, DomainError.NotFound("gone"))

        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(1)))
        viewModel.onEvent(RoutinesEvent.EditorNameChanged("Stretch well"))
        viewModel.onEvent(RoutinesEvent.EditorConfirmed)
        runCurrent()

        assertNull(viewModel.state.value.editor)
        assertEquals(RoutineMessage.RoutineNoLongerExists, viewModel.state.value.message?.value)
    }

    @Test
    fun requestingADeleteOpensTheDialogAndWritesNothing() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(RoutinesEvent.DeleteRequested(1))
        runCurrent()

        assertEquals(1L, viewModel.state.value.pendingDeletionId)
        assertEquals(1, datasource.routines.size)
    }

    @Test
    fun cancellingADeleteLeavesTheRoutineAlone() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(1)))
        viewModel.onEvent(RoutinesEvent.DeleteRequested(1))

        viewModel.onEvent(RoutinesEvent.DeleteCancelled)
        runCurrent()

        assertNull(viewModel.state.value.pendingDeletionId)
        assertEquals(1, datasource.routines.size)
        assertNotNull(viewModel.state.value.editor, "the editor stays open behind the dialog")
    }

    @Test
    fun confirmingADeleteRemovesTheRoutineAndClosesTheEditor() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(RoutinesEvent.EditorOpened(EditorTarget.Existing(1)))
        viewModel.onEvent(RoutinesEvent.DeleteRequested(1))

        viewModel.onEvent(RoutinesEvent.DeleteConfirmed)
        runCurrent()

        assertTrue(datasource.routines.isEmpty())
        assertNull(viewModel.state.value.editor)
        assertNull(viewModel.state.value.pendingDeletionId)
    }

    @Test
    fun aFailedDeleteReportsItAndKeepsTheRoutine() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(RoutinesWrite.Delete, DomainError.Database("disk full"))
        viewModel.onEvent(RoutinesEvent.DeleteRequested(1))

        viewModel.onEvent(RoutinesEvent.DeleteConfirmed)
        runCurrent()

        assertEquals(RoutineMessage.DeleteFailed, viewModel.state.value.message?.value)
        assertEquals(1, datasource.routines.size)
        assertFalse(viewModel.state.value.isEditorBusy)
    }

    @Test
    fun theOpenEditorSurvivesProcessDeath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val first = viewModel(datasource, savedStateHandle)
        runCurrent()
        first.onEvent(RoutinesEvent.EditorOpened(EditorTarget.New))
        first.onEvent(RoutinesEvent.EditorNameChanged("Half typed"))
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle)
        runCurrent()

        assertEquals("Half typed", restored.state.value.editor?.name)
    }

    /** A confirmation dialog must not come back under a thumb already moving to confirm it. */
    @Test
    fun theDeleteDialogDoesNotSurviveProcessDeath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val first = viewModel(datasource, savedStateHandle)
        runCurrent()
        first.onEvent(RoutinesEvent.DeleteRequested(1))
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle)
        runCurrent()

        assertNull(restored.state.value.pendingDeletionId)
    }

    @Test
    fun theMessageIsClearedOnceShown() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(stretch))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(RoutinesWrite.UpdateCompletedOn, DomainError.Database("disk full"))
        viewModel.onEvent(RoutinesEvent.ToggleDone(id = 1, done = true))
        runCurrent()

        viewModel.onEvent(RoutinesEvent.MessageShown)

        assertNull(viewModel.state.value.message)
    }
}
