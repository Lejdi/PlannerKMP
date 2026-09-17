package pl.lejdi.plannerkmp.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.fold

abstract class BaseViewModel<S : MviState, E : MviEvent, F : MviEffect> : ViewModel() {

    protected abstract fun createInitialState(): S

    // Lazy so createInitialState() runs after the subclass's own constructor
    // has finished, not while calling up through BaseViewModel's own init.
    private val _state: MutableStateFlow<S> by lazy { MutableStateFlow(restoreInto(createInitialState())) }

    // A single instance rather than `get() = _state.asStateFlow()`: a getter would allocate a new
    // wrapper on every read, so anything keyed on the flow identity (remember, LaunchedEffect)
    // would re-run on every recomposition.
    val state: StateFlow<S> by lazy { _state.asStateFlow() }

    // UNLIMITED + trySend: sending an effect never suspends, so effects are delivered in the order
    // they were raised. Launching a coroutine per send (the obvious alternative) leaves ordering up
    // to the dispatcher.
    private val _effect = Channel<F>(Channel.UNLIMITED)
    val effect: Flow<F> = _effect.receiveAsFlow()

    // Keyed rather than a single field: see observe(). Main-thread confined, like every other
    // entry point on a ViewModel here — events arrive from composition and init runs on the caller.
    private val observeJobs = mutableMapOf<Any, Job>()

    abstract fun onEvent(event: E)

    /**
     * Reduces the state, having first minted the occurrence id [raise] will hand out.
     *
     * The id is taken *before* `updateAndGet`, not inside it. Every message in this app is raised
     * as `copy(message = raise(X))` — lexically inside the reducer — and minting it there meant
     * incrementing a counter inside a lambda this class documents twice as re-runnable (see
     * [onStateChanged] and [observe]'s `onData`). That is the one thing a reducer is not allowed to
     * do, in the class that states the rule. Minting outside and only *reading* inside makes the
     * reducer pure again: a re-run rebuilds an identical [UiMessage] instead of burning a second id.
     */
    protected fun setState(reduce: S.() -> S) {
        currentOccurrence = ++messageOccurrences
        onStateChanged(_state.updateAndGet(reduce))
    }

    protected fun sendEffect(effect: F) {
        _effect.trySend(effect)
    }

    // Per ViewModel rather than global, and touched only from setState, which — like every other
    // entry point here — is main-thread confined.
    private var messageOccurrences = 0L

    // The id minted for the reduction currently running. Read, never written, by raise().
    private var currentOccurrence = 0L

    /**
     * Wraps [message] with the occurrence id of the reduction that is raising it, so the same
     * message raised twice re-shows rather than comparing equal to the copy already on screen.
     *
     * **Call this inside a [setState] reducer**, which is where every call site has it: the id
     * belongs to that reduction and is minted by [setState] before the reducer runs. Two raisings
     * are two `setState` calls, so they always carry different ids — which is the entire property
     * [UiMessage] exists for.
     *
     * Every `copy(message = …)` goes through this. Writing the bare value compiles only where the
     * state still expects one, which is nowhere — see [UiMessage] for the bug that made this
     * necessary.
     */
    protected fun <M : Any> raise(message: M): UiMessage<M> = UiMessage(message, currentOccurrence)

    /**
     * Seeds the initial state from somewhere outside the ViewModel — saved state, typically.
     *
     * A no-op here; [RestorableViewModel] is what implements it. It exists on the base class so
     * that `_state` can be built through it without every screen knowing about saved state.
     */
    protected open fun restoreInto(initial: S): S = initial

    /**
     * Called once after each state change, with the state that resulted.
     *
     * Separate from the reducer passed to [setState], which must stay pure: `updateAndGet` may
     * re-run its lambda under contention, so a side effect written inside it can happen twice.
     */
    protected open fun onStateChanged(state: S) = Unit

    /**
     * Subscribes to a `Flow<AppResult<T>>` and folds each emission into the state.
     *
     * Every screen in this app is this shape — observe a reactive source, show data or a typed
     * message — and before this each one hand-rolled the same five steps: hold a job, cancel it,
     * flag loading, collect, fold. Calling it again with the same [key] replaces that
     * subscription, which is what makes a "retry" event a one-liner: a failed upstream flow is
     * terminated, so only a fresh subscription can recover, and the previous one must be cancelled
     * first or a recovered screen ends up with two collectors racing to set the same state.
     *
     * [key] is what lets one screen observe two sources. A single job field meant the second
     * `observe` call silently cancelled the first, with no error and no compiler help — the screen
     * would simply never populate half its state.
     *
     * [source] is the flow itself rather than a lambda producing one. Re-calling `observe` already
     * re-evaluates the argument, and the flows here are cold, so the lambda was an indirection that
     * read as if it mattered.
     *
     * [onData] folds an emission into the state and must stay pure, for the reason given on
     * [onStateChanged]. Side effects an emission triggers — navigating away because the record is
     * gone — belong in [onEmission], which runs exactly once per emission.
     */
    protected fun <T> observe(
        key: Any = DEFAULT_OBSERVE_KEY,
        source: Flow<AppResult<T>>,
        onData: S.(T) -> S,
        onError: S.(DomainError) -> S,
        onStart: S.() -> S = { this },
        onEmission: (T) -> Unit = {},
    ) {
        collectInto(key, onStart) {
            source.collect { result ->
                result.fold(
                    onSuccess = { data ->
                        setState { onData(data) }
                        onEmission(data)
                    },
                    onFailure = { error -> setState { onError(error) } },
                )
            }
        }
    }

    /**
     * The same, for a source that cannot fail.
     *
     * `TodayProvider.todayFlow()` is the case: wrapping it in `map { AppResult.Success(it) }` to
     * satisfy the signature above bought a fake result and an `onError` branch with a comment saying
     * the clock cannot fail — a dead branch is a worse answer than a second entry point.
     */
    protected fun <T> observeValues(
        key: Any = DEFAULT_OBSERVE_KEY,
        source: Flow<T>,
        onData: S.(T) -> S,
    ) {
        collectInto(key) {
            source.collect { value -> setState { onData(value) } }
        }
    }

    private fun collectInto(key: Any, onStart: S.() -> S = { this }, block: suspend () -> Unit) {
        observeJobs.remove(key)?.cancel()
        observeJobs[key] = viewModelScope.launch {
            setState(onStart)
            block()
        }
    }

    private companion object {
        val DEFAULT_OBSERVE_KEY = Any()
    }
}
