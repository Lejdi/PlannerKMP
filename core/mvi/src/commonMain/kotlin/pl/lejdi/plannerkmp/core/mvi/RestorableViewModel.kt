package pl.lejdi.plannerkmp.core.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.lejdi.plannerkmp.core.common.Logger

private const val TAG = "MviState"
private const val INPUT_KEY = "pl.lejdi.plannerkmp.mvi.input"

/**
 * A [BaseViewModel] whose *user input* survives process death.
 *
 * The back stack and the selected tab already survive it — `:shared` goes to real trouble for that
 * — but the state inside each screen did not, and restored navigation makes the loss more visible
 * rather than less: the user comes back to exactly the screen they left, with the form they had
 * half-filled wiped.
 *
 * Only the input is saved, never the whole state. A screen's loaded data belongs to the database,
 * which is still there after a restart and re-emits by itself; putting a task list into saved
 * state would pay for it twice and, on Android, risk the binder transaction limit. So the contract
 * is a small serializable [I] — what the user typed and chose, and nothing derived.
 *
 * Encoded as a JSON string rather than through `encodeToSavedState`. `SavedState` is a `Bundle` on
 * Android, which is not available in a JVM host test, so the saved-state path could only ever have
 * been exercised on a device — and a restore path with no test is a restore path that is broken.
 * A string is the one representation every target and every test agrees on.
 *
 * @param I the restorable slice of [S] — see [captureInput] and [applyInput].
 */
abstract class RestorableViewModel<S : MviState, E : MviEvent, F : MviEffect, I : Any>(
    private val savedStateHandle: SavedStateHandle,
    private val inputSerializer: KSerializer<I>,
    private val logger: Logger,
) : BaseViewModel<S, E, F>() {

    /** The part of [state] the user typed or chose, which has to come back after a restart. */
    protected abstract fun captureInput(state: S): I

    /** Puts a restored [I] back into a freshly built state. */
    protected abstract fun applyInput(state: S, input: I): S

    final override fun restoreInto(initial: S): S {
        val saved = savedStateHandle.get<String>(INPUT_KEY) ?: return initial
        return try {
            val restored = json.decodeFromString(inputSerializer, saved)
            // Seeded, so the first reduction after a restore does not re-encode and re-write a
            // value byte-for-byte identical to the one just read back. Without this the skip below
            // could never fire on the one transition it is most certain to be redundant for.
            lastWrittenInput = restored
            applyInput(initial, restored)
        } catch (e: SerializationException) {
            // An app update can change the shape of I while saved state from the old shape is
            // still on disk. Logged rather than swallowed, and recovered rather than rethrown: a
            // blank form is a bad restore, but a crash loop on first launch after an update is
            // worse, and the user cannot clear saved state themselves.
            logger.warn(TAG, "discarding unreadable saved input for ${this::class.simpleName}", e)
            initial
        }
    }

    /**
     * The last input written, so an unchanged one is not re-encoded.
     *
     * [onStateChanged] fires on *every* reduction, not only on the ones the user caused: a task
     * list re-emitting from the database, a spinner going down, a snackbar message being set and
     * cleared. Each of those used to re-serialize the whole form and write it back, although not
     * one of them can have changed a field the user typed. Comparing first makes the write
     * proportional to actual input rather than to state traffic.
     */
    private var lastWrittenInput: I? = null

    final override fun onStateChanged(state: S) {
        val input = captureInput(state)
        if (input == lastWrittenInput) return
        lastWrittenInput = input
        savedStateHandle[INPUT_KEY] = json.encodeToString(inputSerializer, input)
    }

    private companion object {
        // Lenient about fields added since the value was written, so a new optional field in I is
        // an ordinary change rather than one that silently drops everyone's in-progress form.
        val json = Json { ignoreUnknownKeys = true }
    }
}
