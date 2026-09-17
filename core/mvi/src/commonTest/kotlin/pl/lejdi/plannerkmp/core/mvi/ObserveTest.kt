package pl.lejdi.plannerkmp.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class TwoSourceState(
    val left: String = "",
    val right: String = "",
    val error: String? = null,
    val starts: Int = 0,
) : MviState

private object NoEvent : MviEvent
private object NoEffect : MviEffect

private class TwoSourceViewModel(
    private val left: () -> Flow<AppResult<String>>,
    private val right: () -> Flow<AppResult<String>>,
) : BaseViewModel<TwoSourceState, NoEvent, NoEffect>() {

    val seen = mutableListOf<String>()

    override fun createInitialState() = TwoSourceState()

    override fun onEvent(event: NoEvent) = Unit

    fun observeLeft() = observe(
        key = "left",
        // Called here, not handed over: `observe` takes the flow, and re-invoking `observeLeft` is
        // what re-evaluates this — which is the same thing the old lambda parameter did.
        source = left(),
        onData = { copy(left = it) },
        onError = { copy(error = it.message) },
        onStart = { copy(starts = starts + 1) },
        onEmission = { seen += it },
    )

    fun observeRight() = observe(
        key = "right",
        source = right(),
        onData = { copy(right = it) },
        onError = { copy(error = it.message) },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The reason observe() is keyed. With one job field the second call silently cancelled the
     * first, so a screen with two sources would simply never populate half its state — no error,
     * no compiler help.
     */
    @Test
    fun twoSourcesUnderDifferentKeysBothStaySubscribed() = runTest {
        val left = MutableStateFlow("l1")
        val right = MutableStateFlow("r1")
        val viewModel = TwoSourceViewModel(
            left = { left.map { AppResult.Success(it) } },
            right = { right.map { AppResult.Success(it) } },
        )

        viewModel.observeLeft()
        viewModel.observeRight()
        runCurrent()
        left.value = "l2"
        right.value = "r2"
        runCurrent()

        assertEquals("l2", viewModel.state.value.left)
        assertEquals("r2", viewModel.state.value.right)
    }

    /**
     * The cancellation is the thing under test, so the assertion has to be about liveness.
     *
     * Asserting on the resulting state could not fail: two collectors reducing `copy(left = "l2")`
     * produce the same value as one, and `onStart` runs twice either way — so deleting
     * `observeJobs.remove(key)?.cancel()` left this green, although a recovered screen would then
     * have two collectors racing to write the same field, which is exactly what `observe`'s own
     * documentation says the key exists to prevent.
     */
    @Test
    fun observingTheSameKeyAgainReplacesTheSubscription() = runTest {
        val left = MutableStateFlow("l1")
        val viewModel = TwoSourceViewModel(
            left = { left.map { AppResult.Success(it) } },
            right = { flowOf(AppResult.Success("r")) },
        )

        viewModel.observeLeft()
        runCurrent()
        assertEquals(1, left.subscriptionCount.value)

        viewModel.observeLeft()
        runCurrent()

        assertEquals(1, left.subscriptionCount.value, "the first subscription must have been cancelled")
        left.value = "l2"
        runCurrent()
        assertEquals("l2", viewModel.state.value.left)
        assertEquals(2, viewModel.state.value.starts, "the second call re-ran onStart")
    }

    /** The other half: a *different* key must leave the first subscription alone. */
    @Test
    fun observingADifferentKeyLeavesTheFirstSubscriptionLive() = runTest {
        val left = MutableStateFlow("l1")
        val right = MutableStateFlow("r1")
        val viewModel = TwoSourceViewModel(
            left = { left.map { AppResult.Success(it) } },
            right = { right.map { AppResult.Success(it) } },
        )

        viewModel.observeLeft()
        viewModel.observeRight()
        runCurrent()

        assertEquals(1, left.subscriptionCount.value)
        assertEquals(1, right.subscriptionCount.value)
    }

    @Test
    fun onEmissionRunsOncePerEmission() = runTest {
        val left = MutableStateFlow("a")
        val viewModel = TwoSourceViewModel(
            left = { left.map { AppResult.Success(it) } },
            right = { flowOf(AppResult.Success("r")) },
        )

        viewModel.observeLeft()
        runCurrent()
        left.value = "b"
        runCurrent()

        assertEquals(listOf("a", "b"), viewModel.seen)
    }

    @Test
    fun aFailureFoldsThroughOnErrorAndNotOnEmission() = runTest {
        val viewModel = TwoSourceViewModel(
            left = { flowOf(AppResult.Failure(DomainError.Database("boom"))) },
            right = { flowOf(AppResult.Success("r")) },
        )

        viewModel.observeLeft()
        runCurrent()

        assertEquals("boom", viewModel.state.value.error)
        assertTrue(viewModel.seen.isEmpty())
    }
}
