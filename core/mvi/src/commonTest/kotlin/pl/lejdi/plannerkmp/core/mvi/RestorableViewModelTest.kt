package pl.lejdi.plannerkmp.core.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class FormInput(val typed: String = "")

private data class FormState(
    val typed: String = "",
    /** Stands in for data that arrives from the database rather than from the user. */
    val loaded: Int = 0,
) : MviState

private sealed interface FormEvent : MviEvent {
    data class Typed(val value: String) : FormEvent
    data class Loaded(val value: Int) : FormEvent
}

private sealed interface FormEffect : MviEffect

private class FormViewModel(
    savedStateHandle: SavedStateHandle,
) : RestorableViewModel<FormState, FormEvent, FormEffect, FormInput>(
    savedStateHandle = savedStateHandle,
    inputSerializer = FormInput.serializer(),
    logger = NoOpLogger(),
) {
    /** Counts how often the base class asked for the input, i.e. how often it considered writing. */
    var capturedCount: Int = 0
        private set

    override fun createInitialState() = FormState()

    override fun captureInput(state: FormState): FormInput {
        capturedCount++
        return FormInput(state.typed)
    }

    override fun applyInput(state: FormState, input: FormInput) = state.copy(typed = input.typed)

    override fun onEvent(event: FormEvent) {
        when (event) {
            is FormEvent.Typed -> setState { copy(typed = event.value) }
            is FormEvent.Loaded -> setState { copy(loaded = event.value) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RestorableViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun userInputSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        FormViewModel(handle).onEvent(FormEvent.Typed("half a sentence"))

        val restored = FormViewModel(handle)

        assertEquals("half a sentence", restored.state.value.typed)
    }

    /**
     * The saved-state write is driven by *input*, not by state traffic.
     *
     * `onStateChanged` fires on every reduction, and most reductions on a real screen have nothing
     * to do with the user: a list re-emitting from the database, a spinner going down, a message
     * being set and cleared. Each of those used to re-serialize the whole form and write it back.
     */
    @Test
    fun aStateChangeThatLeavesTheInputAloneIsNotWrittenBack() = runTest {
        val handle = SavedStateHandle()
        val viewModel = FormViewModel(handle)
        viewModel.onEvent(FormEvent.Typed("typed once"))
        val written = handle.get<String>(INPUT_KEY)

        // Ten emissions from the data layer, none of which touch what the user typed.
        repeat(10) { viewModel.onEvent(FormEvent.Loaded(it)) }

        assertEquals(written, handle.get<String>(INPUT_KEY), "the stored input must be unchanged")
        assertEquals("typed once", FormViewModel(handle).state.value.typed)
    }

    @Test
    fun aRealEditIsStillWrittenBack() = runTest {
        val handle = SavedStateHandle()
        val viewModel = FormViewModel(handle)
        viewModel.onEvent(FormEvent.Typed("first"))

        viewModel.onEvent(FormEvent.Typed("second"))

        assertEquals("second", FormViewModel(handle).state.value.typed)
    }

    @Test
    fun unreadableSavedInputFallsBackToTheInitialStateRatherThanCrashing() = runTest {
        val handle = SavedStateHandle()
        handle[INPUT_KEY] = "{not json"

        assertEquals("", FormViewModel(handle).state.value.typed)
    }

    private companion object {
        // Mirrors RestorableViewModel's own private key; a test asserting about the write has to
        // name the same slot.
        const val INPUT_KEY = "pl.lejdi.plannerkmp.mvi.input"
    }
}
