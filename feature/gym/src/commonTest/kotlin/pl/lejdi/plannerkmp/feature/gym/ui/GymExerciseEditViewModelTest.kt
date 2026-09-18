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
import kotlinx.datetime.DayOfWeek
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.feature.gym.domain.FakeGymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymField
import pl.lejdi.plannerkmp.feature.gym.domain.GymWrite
import pl.lejdi.plannerkmp.feature.gym.domain.exercise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GymExerciseEditViewModelTest {

    private val bench = exercise(
        id = 1L,
        name = "Bench press",
        comment = "slow eccentric",
        dayOfWeek = DayOfWeek.MONDAY,
        setsCount = 4,
        repsPerSet = 8,
        weight = 60.0,
    )

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
        exerciseId: Long? = null,
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = GymExerciseEditViewModel(
        exerciseId,
        dayOfWeek,
        savedStateHandle,
        NoOpLogger(),
        datasource,
    )

    private fun FakeGymDatasource.adds() =
        writes.filterIsInstance<FakeGymDatasource.RecordedWrite.Add>()

    private fun FakeGymDatasource.detailUpdates() =
        writes.filterIsInstance<FakeGymDatasource.RecordedWrite.UpdateDetails>()

    private fun fillValidForm(viewModel: GymExerciseEditViewModel) {
        viewModel.onEvent(GymExerciseEditEvent.NameChanged("Squat"))
        viewModel.onEvent(GymExerciseEditEvent.SetsCountChanged("5"))
        viewModel.onEvent(GymExerciseEditEvent.RepsPerSetChanged("5"))
        viewModel.onEvent(GymExerciseEditEvent.WeightChanged("80"))
    }

    @Test
    fun aNewExerciseStartsEmptyOnTheDayItWasAddedFrom() = runTest {
        val viewModel = viewModel(FakeGymDatasource(), dayOfWeek = DayOfWeek.THURSDAY)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertFalse(state.isEditingExisting)
        assertEquals(DayOfWeek.THURSDAY, state.form.dayOfWeek)
        assertEquals("", state.form.name)
    }

    @Test
    fun theFormIsSeededFromTheStoredRow() = runTest {
        val viewModel = viewModel(FakeGymDatasource(listOf(bench)), exerciseId = 1L)
        runCurrent()

        val form = viewModel.state.value.form
        assertEquals("Bench press", form.name)
        assertEquals("slow eccentric", form.comment)
        assertEquals(DayOfWeek.MONDAY, form.dayOfWeek)
        assertEquals("4", form.setsCount)
        assertEquals("8", form.repsPerSet)
        assertEquals("60", form.weight, "the weight is shown as 60, not 60.0")
        assertTrue(viewModel.state.value.isFormSeeded)
        assertFalse(viewModel.state.value.isLoading)
    }

    /** Later emissions must not overwrite what the user is in the middle of typing. */
    @Test
    fun onlyTheFirstEmissionSeedsTheForm() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.NameChanged("Incline bench"))
        datasource.updateWeight(1L, 70.0)
        runCurrent()

        assertEquals("Incline bench", viewModel.state.value.form.name)
    }

    /** Staying subscribed is what a one-shot read could never do. */
    @Test
    fun theScreenLeavesWhenTheRowIsDeletedUnderneathIt() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        datasource.deleteExercise(1L)
        runCurrent()

        assertEquals(GymExerciseEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun aNewExerciseIsAdded() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource, dayOfWeek = DayOfWeek.WEDNESDAY)
        runCurrent()
        fillValidForm(viewModel)

        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        val draft = datasource.adds().single().draft
        assertEquals("Squat", draft.name)
        assertEquals(DayOfWeek.WEDNESDAY, draft.dayOfWeek)
        assertEquals(5, draft.setsCount)
        assertEquals(5, draft.repsPerSet)
        assertEquals(80.0, draft.weight)
        assertEquals(GymExerciseEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun anEditGoesThroughUpdateDetails() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.NameChanged("Incline bench"))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertTrue(datasource.adds().isEmpty(), "an existing row is updated, not inserted")
        val update = datasource.detailUpdates().single()
        assertEquals(1L, update.id)
        assertEquals("Incline bench", update.draft.name)
    }

    /** Moving an exercise to another weekday is how it changes days; there is no other way. */
    @Test
    fun theExerciseCanBeMovedToAnotherWeekday() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.DayOfWeekChanged(DayOfWeek.FRIDAY))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(DayOfWeek.FRIDAY, datasource.detailUpdates().single().draft.dayOfWeek)
    }

    @Test
    fun everyInvalidFieldIsMarkedFromOneSave() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.NameChanged("   "))
        viewModel.onEvent(GymExerciseEditEvent.SetsCountChanged("0"))
        viewModel.onEvent(GymExerciseEditEvent.RepsPerSetChanged("abc"))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(
            setOf(GymField.Name, GymField.Sets, GymField.Reps),
            viewModel.state.value.form.invalidFields,
        )
        assertTrue(datasource.writes.isEmpty(), "an invalid form writes nothing")
    }

    @Test
    fun correctingAFieldClearsItsMark() = runTest {
        val viewModel = viewModel(FakeGymDatasource())
        runCurrent()
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()
        assertTrue(GymField.Name in viewModel.state.value.form)

        viewModel.onEvent(GymExerciseEditEvent.NameChanged("Squat"))

        assertFalse(GymField.Name in viewModel.state.value.form)
    }

    @Test
    fun anEmptyWeightFieldSavesAsBodyweight() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)

        viewModel.onEvent(GymExerciseEditEvent.WeightChanged(""))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertNull(datasource.adds().single().draft.weight)
    }

    @Test
    fun aCommaDecimalWeightSaves() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)

        viewModel.onEvent(GymExerciseEditEvent.WeightChanged("82,5"))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(82.5, datasource.adds().single().draft.weight)
    }

    @Test
    fun anUnparseableWeightMarksTheWeightField() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)

        viewModel.onEvent(GymExerciseEditEvent.WeightChanged("heavy"))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(setOf(GymField.Weight), viewModel.state.value.form.invalidFields)
        assertTrue(datasource.writes.isEmpty())
    }

    @Test
    fun aSecondSaveWhileOneIsInFlightIsDropped() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)
        datasource.blockWrites = true

        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()
        datasource.releaseWrites()
        runCurrent()

        assertEquals(1, datasource.adds().size, "two taps must not insert twice")
    }

    @Test
    fun deleteAsksFirst() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.DeleteClicked)

        assertEquals(GymExerciseEditDialog.ConfirmDelete, viewModel.state.value.form.activeDialog)
        assertTrue(datasource.writes.isEmpty(), "asking is not deleting")
    }

    @Test
    fun aConfirmedDeleteRemovesTheRowAndLeaves() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.DeleteClicked)
        viewModel.onEvent(GymExerciseEditEvent.DeleteConfirmed)
        runCurrent()

        assertTrue(datasource.exercises.isEmpty())
        assertEquals(GymExerciseEditEffect.NavigateBack, viewModel.effect.first())
    }

    /** The row already being gone is the outcome delete wanted, not a failure to report. */
    @Test
    fun deletingAnAlreadyAbsentRowStillLeaves() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()
        datasource.failNext(GymWrite.Delete, DomainError.NotFound("gone"))

        viewModel.onEvent(GymExerciseEditEvent.DeleteClicked)
        viewModel.onEvent(GymExerciseEditEvent.DeleteConfirmed)
        runCurrent()

        assertEquals(GymExerciseEditEffect.NavigateBack, viewModel.effect.first())
    }

    @Test
    fun aRealDeleteFailureKeepsTheUserOnTheScreen() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()
        datasource.failNext(GymWrite.Delete, DomainError.Database("boom"))

        viewModel.onEvent(GymExerciseEditEvent.DeleteClicked)
        viewModel.onEvent(GymExerciseEditEvent.DeleteConfirmed)
        runCurrent()

        assertEquals(GymExerciseEditMessage.DeleteFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun aSaveOntoADeletedRowSaysSo() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()
        datasource.failNext(GymWrite.UpdateDetails, DomainError.NotFound("gone"))

        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(
            GymExerciseEditMessage.ExerciseNoLongerExists,
            viewModel.state.value.message?.value,
        )
    }

    @Test
    fun aFailedSaveRaisesSaveFailed() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)
        datasource.failNext(GymWrite.Add, DomainError.Database("boom"))

        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        assertEquals(GymExerciseEditMessage.SaveFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun theTypedFormSurvivesARestart() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeGymDatasource()
        val first = viewModel(datasource, savedStateHandle = savedStateHandle)
        runCurrent()
        first.onEvent(GymExerciseEditEvent.NameChanged("Squat"))
        first.onEvent(GymExerciseEditEvent.SetsCountChanged("5"))
        first.onEvent(GymExerciseEditEvent.DayOfWeekChanged(DayOfWeek.SATURDAY))
        runCurrent()

        val restored = viewModel(datasource, savedStateHandle = savedStateHandle)
        runCurrent()

        assertEquals("Squat", restored.state.value.form.name)
        assertEquals("5", restored.state.value.form.setsCount)
        assertEquals(DayOfWeek.SATURDAY, restored.state.value.form.dayOfWeek)
    }

    @Test
    fun aFailedLoadIsTerminalAndRetryResubscribes() = runTest {
        val datasource = FakeGymDatasource(listOf(bench))
        datasource.observeFailure = DomainError.Database("driver gone")
        val viewModel = viewModel(datasource, exerciseId = 1L)
        runCurrent()

        assertTrue(viewModel.state.value.hasTerminalLoadFailure)
        assertFalse(viewModel.state.value.canSubmit, "a form that never loaded cannot be saved")

        datasource.observeFailure = null
        viewModel.onEvent(GymExerciseEditEvent.RetryClicked)
        runCurrent()

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals("Bench press", viewModel.state.value.form.name)
    }

    @Test
    fun theMessageIsClearedOnceShown() = runTest {
        val datasource = FakeGymDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        fillValidForm(viewModel)
        datasource.failNext(GymWrite.Add, DomainError.Database("boom"))
        viewModel.onEvent(GymExerciseEditEvent.SaveClicked)
        runCurrent()

        viewModel.onEvent(GymExerciseEditEvent.MessageShown)

        assertNull(viewModel.state.value.message)
    }
}
