package pl.lejdi.plannerkmp.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

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

private enum class Note { Saved }

private data class NoteState(val message: UiMessage<Note>? = null, val unrelated: Int = 0) : MviState

private sealed interface NoteEvent : MviEvent {
    data object Raise : NoteEvent
    data object Touch : NoteEvent
    data object RaiseTwiceInOneReduction : NoteEvent
}

private class NoteViewModel : BaseViewModel<NoteState, NoteEvent, CounterEffect>() {
    override fun createInitialState() = NoteState()

    /** Both ids minted inside one reducer, so a test can tell reduction-scoped from per-call. */
    var pairFromOneReduction: Pair<UiMessage<Note>, UiMessage<Note>>? = null
        private set

    override fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.Raise -> setState { copy(message = raise(Note.Saved)) }
            is NoteEvent.Touch -> setState { copy(unrelated = unrelated + 1) }
            is NoteEvent.RaiseTwiceInOneReduction -> setState {
                val first = raise(Note.Saved)
                val second = raise(Note.Saved)
                pairFromOneReduction = first to second
                copy(message = second)
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

    @Test
    fun effectsArriveInTheOrderTheyWereSent() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onEvent(CounterEvent.Increment)
        viewModel.onEvent(CounterEvent.Increment)
        viewModel.onEvent(CounterEvent.Increment)

        assertEquals(
            listOf(
                CounterEffect.Announced(1),
                CounterEffect.Announced(2),
                CounterEffect.Announced(3),
            ),
            viewModel.effect.take(3).toList(),
        )
    }

    @Test
    fun stateFlowIsTheSameInstanceOnEveryRead() {
        val viewModel = CounterViewModel()

        assertSame(viewModel.state, viewModel.state)
    }

    /**
     * The property [UiMessage] exists for: the same message raised twice must not compare equal to
     * the copy already on screen, or the snackbar host — which keys its effect on the value it is
     * handed — never re-shows it. Two grocery completions in a row is the normal way to use that
     * screen, and the second used to produce no acknowledgement at all.
     */
    @Test
    fun theSameMessageRaisedTwiceIsTwoDistinctMessages() {
        val viewModel = NoteViewModel()

        viewModel.onEvent(NoteEvent.Raise)
        val first = viewModel.state.value.message
        viewModel.onEvent(NoteEvent.Raise)
        val second = viewModel.state.value.message

        assertEquals(Note.Saved, first?.value)
        assertEquals(Note.Saved, second?.value)
        assertNotEquals(first, second, "a re-raised message must not equal the previous one")
    }

    /**
     * `raise` reads the id its enclosing `setState` minted; it does not mint one itself.
     *
     * That is what keeps the reducer pure: `updateAndGet` may re-run its lambda under contention, so
     * an id minted *inside* it would be a side effect in the one place this class documents twice as
     * re-runnable. Two raisings within a single reduction therefore agree, and a re-run rebuilds an
     * identical [UiMessage] rather than burning a second id.
     */
    @Test
    fun raiseIsStableWithinOneReduction() {
        val viewModel = NoteViewModel()

        viewModel.onEvent(NoteEvent.RaiseTwiceInOneReduction)

        val pair = viewModel.pairFromOneReduction
        assertEquals(pair?.first, pair?.second, "raise must read the reduction's id, not mint one")
    }

    /** A reduction that raises nothing still advances the id, so the next message is distinct. */
    @Test
    fun anUnrelatedReductionDoesNotStopTheNextMessageBeingDistinct() {
        val viewModel = NoteViewModel()

        viewModel.onEvent(NoteEvent.Raise)
        val first = viewModel.state.value.message
        viewModel.onEvent(NoteEvent.Touch)
        viewModel.onEvent(NoteEvent.Raise)

        assertNotEquals(first, viewModel.state.value.message)
    }
}
