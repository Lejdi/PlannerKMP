package pl.lejdi.plannerkmp.feature.gym.ui

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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.feature.gym.domain.FakeGymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymWrite
import pl.lejdi.plannerkmp.feature.gym.domain.ObserveGymWeek
import pl.lejdi.plannerkmp.feature.gym.domain.ToggleExerciseSet
import pl.lejdi.plannerkmp.feature.gym.domain.exercise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GymViewModelTest {

    /** A Friday, so "today" is neither the first nor the last page. */
    private val today = LocalDate(2026, 9, 18)
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private val bench = exercise(id = 1L, name = "Bench press", setsCount = 4, weight = 60.0)
    private val rows = exercise(id = 2L, name = "Barbell row", setsCount = 3, weight = 50.0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        datasource: FakeGymDatasource,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
    ): GymViewModel {
        val dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined)
        return GymViewModel(
            savedStateHandle,
            NoOpLogger(),
            ObserveGymWeek(datasource, todayProvider, dispatchers),
            ToggleExerciseSet(datasource, todayProvider),
            datasource,
            todayProvider,
        )
    }

    private fun FakeGymDatasource.completions() =
        writes.filterIsInstance<FakeGymDatasource.RecordedWrite.UpdateCompletedSets>()

    private fun FakeGymDatasource.weightWrites() =
        writes.filterIsInstance<FakeGymDatasource.RecordedWrite.UpdateWeight>()

    @Test
    fun theWeekIsLoadedOnCreation() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(bench)))
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(7, state.days.size)
        assertEquals(listOf(1L), state.days.single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.map { it.id })
    }

    @Test
    fun todayIsSeededFromTheProvider() = runTest {
        val viewModel = viewModel(FakeGymDatasource())
        runCurrent()

        assertEquals(DayOfWeek.FRIDAY, viewModel.state.value.today)
    }

    /** A screen left open across midnight has to move its own marker. */
    @Test
    fun todayFollowsTheDateChanging() = runTest {
        val todayProvider = FakeTodayProvider(today)
        val viewModel = viewModel(FakeGymDatasource(), todayProvider = todayProvider)
        runCurrent()

        todayProvider.setToday(tomorrow)
        runCurrent()

        assertEquals(DayOfWeek.SATURDAY, viewModel.state.value.today)
    }

    @Test
    fun aFailedLoadIsTerminalAndRetryResubscribes() = runTest {
        val datasource = FakeGymDatasource()
        datasource.observeFailure = DomainError.Database("driver gone")
        val viewModel = viewModel(datasource)
        runCurrent()

        assertTrue(viewModel.state.value.hasTerminalLoadFailure)
        assertEquals(GymMessage.LoadFailed, viewModel.state.value.message?.value)

        datasource.observeFailure = null
        viewModel.onEvent(GymEvent.RetryClicked)
        runCurrent()

        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun togglingASeriesWritesThroughTheUseCase() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.SerieToggled(exerciseId = 1L, serieNumber = 1))
        runCurrent()

        val write = datasource.completions().single()
        assertEquals(1L, write.id)
        assertEquals(1, write.completedSets)
        assertEquals(today, write.completedOn)
    }

    /**
     * The event carries an id, so the count is planned from the list the ViewModel is observing
     * rather than from whatever the card held when it was drawn.
     */
    @Test
    fun togglingPlansFromTheLiveListNotTheEvent() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(id = 1L, setsCount = 4, completedSets = 2, completedOn = today)),
        )
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.SerieToggled(exerciseId = 1L, serieNumber = 3))
        runCurrent()

        assertEquals(3, datasource.completions().single().completedSets)
    }

    @Test
    fun aSecondToggleOfTheSameExerciseWhileOneIsInFlightIsDropped() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()
        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()
        datasource.releaseWrites()
        runCurrent()

        assertEquals(1, datasource.completions().size, "a double tap must not tick twice")
    }

    /** The reason the guard is a set of ids and not one boolean. */
    @Test
    fun anotherExerciseCanBeToggledWhileOneIsInFlight() = runTest {
        val datasource = FakeGymDatasource(listOf(bench, rows))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()
        viewModel.onEvent(GymEvent.SerieToggled(2L, 1))
        runCurrent()
        datasource.releaseWrites()
        runCurrent()

        assertEquals(listOf(1L, 2L), datasource.completions().map { it.id })
    }

    @Test
    fun aFailedToggleRaisesToggleFailed() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GymWrite.UpdateCompletedSets, DomainError.Database("boom"))

        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()

        assertEquals(GymMessage.ToggleFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.isSubmitting, "the in-flight id is released either way")
    }

    @Test
    fun aToggleOfADeletedRowSaysSoInsteadOfOfferingARetry() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GymWrite.UpdateCompletedSets, DomainError.NotFound("gone"))

        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()

        assertEquals(GymMessage.ExerciseNoLongerExists, viewModel.state.value.message?.value)
    }

    @Test
    fun theWeightEditorOpensSeededWithTheCurrentWeight() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(bench)))
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))

        assertEquals("60", viewModel.state.value.weightEditor?.text)
    }

    @Test
    fun theWeightEditorOpensEmptyForABodyweightExercise() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(exercise(id = 1L, weight = null))))
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))

        assertEquals("", viewModel.state.value.weightEditor?.text)
    }

    /**
     * The regression this exists for.
     *
     * `Modifier.onFocusChanged` fires once when the field is attached, reporting *unfocused* —
     * before the user can have touched anything. Treating that like "the user tapped away"
     * committed and closed the editor on the frame it opened, which on screen looked exactly like
     * a field refusing to take focus.
     */
    @Test
    fun theUnfocusedEventAFieldGetsOnOpeningIsIgnored() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightEditorFocusChanged(isFocused = false))
        runCurrent()

        assertNotNull(viewModel.state.value.weightEditor, "the editor must survive being attached")
        assertTrue(datasource.weightWrites().isEmpty(), "and must not have written anything")
    }

    @Test
    fun losingFocusAfterHoldingItCommits() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightEditorFocusChanged(isFocused = true))
        viewModel.onEvent(GymEvent.WeightTextChanged("82,5"))
        viewModel.onEvent(GymEvent.WeightEditorFocusChanged(isFocused = false))
        runCurrent()

        assertEquals(82.5, datasource.weightWrites().single().weight)
        assertNull(viewModel.state.value.weightEditor, "a committed editor closes")
    }

    /**
     * And again after process death: the restored field is attached anew and gets the same
     * spurious event, so "has held focus" must not come back from saved state as true.
     */
    @Test
    fun aRestoredEditorAlsoIgnoresTheUnfocusedEventOnOpening() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeGymDatasource(listOf(bench))
        val first = viewModel(datasource, savedStateHandle)
        runCurrent()
        first.onEvent(GymEvent.WeightEditStarted(1L))
        first.onEvent(GymEvent.WeightEditorFocusChanged(isFocused = true))
        first.onEvent(GymEvent.WeightTextChanged("77.5"))
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle)
        runCurrent()
        restored.onEvent(GymEvent.WeightEditorFocusChanged(isFocused = false))
        runCurrent()

        assertNotNull(restored.state.value.weightEditor, "a restored field has not been focused yet")
        assertEquals("77.5", restored.state.value.weightEditor?.text)
        assertTrue(datasource.weightWrites().isEmpty())
    }

    @Test
    fun aTypedWeightIsCommitted() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("82,5"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        assertEquals(82.5, datasource.weightWrites().single().weight)
        assertNull(viewModel.state.value.weightEditor, "a committed editor closes")
    }

    @Test
    fun anEmptiedFieldClearsTheWeight() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged(""))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        val write = datasource.weightWrites().single()
        assertNull(write.weight, "an empty field means bodyweight, not a rejected edit")
        assertNull(viewModel.state.value.weightEditor)
    }

    @Test
    fun anUnparseableWeightIsRejectedWithoutWriting() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("heavy"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        assertTrue(datasource.weightWrites().isEmpty())
        val editor = assertNotNull(viewModel.state.value.weightEditor, "the editor stays open")
        assertTrue(editor.isInvalid)
        assertEquals(GymMessage.WeightInvalid, viewModel.state.value.message?.value)
    }

    /** The bound is the domain's, read from the same constant rather than copied. */
    @Test
    fun anOutOfRangeWeightIsRejectedWithoutWriting() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("5000"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        assertTrue(datasource.weightWrites().isEmpty())
        assertTrue(viewModel.state.value.weightEditor?.isInvalid == true)
    }

    @Test
    fun aNegativeWeightIsRejectedWithoutWriting() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("-5"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        assertTrue(datasource.weightWrites().isEmpty())
        assertTrue(viewModel.state.value.weightEditor?.isInvalid == true)
    }

    @Test
    fun editingTheTextClearsTheInvalidMark() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(bench)))
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("heavy"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()
        viewModel.onEvent(GymEvent.WeightTextChanged("8"))

        assertFalse(viewModel.state.value.weightEditor?.isInvalid == true)
    }

    @Test
    fun cancellingTheEditorWritesNothing() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("99"))
        viewModel.onEvent(GymEvent.WeightEditCancelled)
        runCurrent()

        assertTrue(datasource.weightWrites().isEmpty())
        assertNull(viewModel.state.value.weightEditor)
    }

    @Test
    fun aFailedWeightWriteRaisesWeightSaveFailed() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GymWrite.UpdateWeight, DomainError.Database("boom"))

        viewModel.onEvent(GymEvent.WeightEditStarted(1L))
        viewModel.onEvent(GymEvent.WeightTextChanged("70"))
        viewModel.onEvent(GymEvent.WeightEditCommitted)
        runCurrent()

        assertEquals(GymMessage.WeightSaveFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun theOpenWeightEditorSurvivesARestart() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeGymDatasource(listOf(bench))
        val first = viewModel(datasource, savedStateHandle)
        runCurrent()
        first.onEvent(GymEvent.WeightEditStarted(1L))
        first.onEvent(GymEvent.WeightTextChanged("77.5"))
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle)
        runCurrent()

        assertEquals(1L, restored.state.value.weightEditor?.exerciseId)
        assertEquals("77.5", restored.state.value.weightEditor?.text)
    }

    @Test
    fun navigationEffectsAreSent() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(bench)))
        runCurrent()

        viewModel.onEvent(GymEvent.AddExerciseClicked(DayOfWeek.THURSDAY))

        assertEquals(GymEffect.NavigateToAddExercise(DayOfWeek.THURSDAY), viewModel.effect.first())

        viewModel.onEvent(GymEvent.EditExerciseClicked(7L))

        assertEquals(GymEffect.NavigateToEditExercise(7L), viewModel.effect.first())
    }

    @Test
    fun theMessageIsClearedOnceShown() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GymWrite.UpdateCompletedSets, DomainError.Database("boom"))
        viewModel.onEvent(GymEvent.SerieToggled(1L, 1))
        runCurrent()

        viewModel.onEvent(GymEvent.MessageShown)

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun anEmptyWeekIsEmptyButNotAFailure() = runTest {
        val viewModel = viewModel(FakeGymDatasource())
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.isEmpty)
        assertFalse(state.loadFailed)
        assertFalse(state.hasTerminalLoadFailure, "nothing planned yet is not a broken load")
    }
}
