package pl.lejdi.plannerkmp.core.mvi

interface MviState
interface MviEvent
interface MviEffect

/**
 * A user-facing message, plus the identity of *this* raising of it.
 *
 * The identity is the whole point. A screen's message used to be the bare enum, and the snackbar
 * host keys its effect on the value it is given — so raising the same message a second time while
 * the first was still on screen changed nothing, and the host never re-showed it. On the grocery
 * list, where completing an item is one tap and two taps in a row is the normal way to use it, the
 * second completion produced no acknowledgement at all and quietly inherited whatever was left of
 * the first one's undo window. [occurrence] makes two raisings of one message compare unequal.
 *
 * Built through [BaseViewModel.raise] rather than directly, so the counter is never forgotten.
 */
data class UiMessage<out M : Any>(val value: M, val occurrence: Long)

/**
 * The load/failure/message triad every screen in this app has.
 *
 * Each screen used to re-derive it: three copies of `hasTerminalLoadFailure`, three copies of the
 * "spinner, else full-screen error, else content" branch, and three copies of the snackbar
 * plumbing. The third copy had already drifted — it was missing the terminal branch entirely, so a
 * failed load rendered an empty *form* that still believed it was editing a real row.
 *
 * [loadFailed] is a flag of its own rather than `message == LoadFailed`, so the terminal-failure
 * rule does not have to know which member of a feature's message enum means "the load broke".
 */
interface LoadableState<M : Any> : MviState {
    val isLoading: Boolean

    /** True when the most recent load attempt failed. Cleared when a fresh attempt starts. */
    val loadFailed: Boolean

    /** Whether there is nothing on screen to fall back on — no list, no populated form. */
    val isEmpty: Boolean

    /**
     * True while a user-initiated write is in flight.
     *
     * Declared here without a default so that a screen has to answer the question rather than
     * inherit "no". Every mutation in this app was fire-and-forget from a tap: `save()` read the
     * state, launched, and returned, so two quick taps on Save ran two inserts and created two
     * rows — the same for Delete and for completing a task. A ViewModel gates re-entry on this and
     * the screen disables the control, which are the two halves of the same guard: without the
     * first a slow database still admits the second tap, and without the second the user gets no
     * feedback that the first one took.
     */
    val isSubmitting: Boolean

    /**
     * The current user-facing message, typed; the UI layer resolves it to a localized string.
     *
     * Wrapped in [UiMessage] so that the same message raised twice is two distinct values — see
     * there for what went wrong when it was not.
     */
    val message: UiMessage<M>?

    /**
     * A failure with nothing behind it, which earns a full screen and a retry. Every other failure
     * happens with content still visible, and those stay snackbars — announcing a failure and then
     * leaving the user looking at an empty pager helps nobody.
     */
    val hasTerminalLoadFailure: Boolean get() = loadFailed && isEmpty
}
