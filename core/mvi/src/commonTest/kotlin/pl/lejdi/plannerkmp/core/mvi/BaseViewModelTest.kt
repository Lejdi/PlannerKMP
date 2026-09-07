package pl.lejdi.plannerkmp.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterState(val count: Int = 0) : MviState

private sealed interface CounterEvent : MviEvent {
    data object Increment : CounterEvent
}

private sealed interface CounterEffect : MviEffect {
    data class Announced(val count: Int) : CounterEffect
}

private class CounterViewModel : BaseViewModel<CounterState, CounterEvent, CounterEffect>() {
    override fun createInitialState() = CounterState()

    override fun onEvent(event: CounterEvent) {
        when (event) {
            is CounterEvent.Increment -> {
                setState { copy(count = count + 1) }
                sendEffect(CounterEffect.Announced(state.value.count))
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateComesFromCreateInitialState() {
        val viewModel = CounterViewModel()

        assertEquals(CounterState(count = 0), viewModel.state.value)
    }

    @Test
    fun onEventUpdatesStateThroughSetState() {
        val viewModel = CounterViewModel()

        viewModel.onEvent(CounterEvent.Increment)

        assertEquals(CounterState(count = 1), viewModel.state.value)
    }

    @Test
    fun sendEffectEmitsOnEffectFlow() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onEvent(CounterEvent.Increment)

        assertEquals(CounterEffect.Announced(1), viewModel.effect.first())
    }
}
