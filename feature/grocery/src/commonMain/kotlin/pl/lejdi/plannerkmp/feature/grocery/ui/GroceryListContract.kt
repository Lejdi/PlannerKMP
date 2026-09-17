package pl.lejdi.plannerkmp.feature.grocery.ui

import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

/**
 * One [editor] rather than parallel `addName`/`addDescription`/`addNameError` and
 * `editName`/`editDescription`/`editNameError` fields: adding and editing are the same form with a
 * different target, and modelling them twice meant every change had to be made twice.
 */
data class GroceryListState(
    override val isLoading: Boolean = true,
    override val loadFailed: Boolean = false,
    /**
     * The items whose completion is in flight, rather than one flag for the whole screen.
     *
     * A single `isSubmitting` was the right shape for a form with one Save button and the wrong one
     * here. Completing is a tap per item and two taps in a row is how the list is meant to be used,
     * but the flag made the second tap land while the first write was still open — so the guard
     * dropped it silently and the row's control was disabled for a write that had nothing to do
     * with it.
     */
    val completingItemIds: Set<Long> = emptySet(),
    /** True while the editor's save, or an undo, is in flight. Only one of those runs at a time. */
    val isEditorBusy: Boolean = false,
    val items: List<GroceryItem> = emptyList(),
    val editor: GroceryEditor? = null,
    /**
     * The item the last completion removed, kept only until its snackbar goes.
     *
     * "Complete" is a permanent delete with no confirmation — the right call for a shopping list,
     * where a tap per item is the whole point and a dialog each time would be intolerable. What was
     * missing was the other half of that bargain: a way back from the tap that hit the wrong row.
     */
    val undoableDeletion: GroceryItem? = null,
    override val message: UiMessage<GroceryMessage>? = null,
) : LoadableState<GroceryMessage> {
    override val isEmpty: Boolean get() = items.isEmpty()

    override val isSubmitting: Boolean get() = isEditorBusy || completingItemIds.isNotEmpty()

    /** Whether *this* row has a write open — the only thing that should disable *this* row. */
    fun isCompleting(id: Long): Boolean = id in completingItemIds
}

/**
 * The open editor is the only thing on this screen the user typed, so it is the only thing worth
 * carrying through a process restart — the list itself is in the database and re-emits by itself.
 */
@Serializable
data class GroceryEditor(
    val target: EditorTarget,
    val name: String = "",
    val description: String = "",
    val nameError: Boolean = false,
)

@Serializable
sealed interface EditorTarget {
    @Serializable
    data object New : EditorTarget

    @Serializable
    data class Existing(val id: Long) : EditorTarget
}

/** The restorable slice of [GroceryListState]. */
@Serializable
data class GroceryListInput(val editor: GroceryEditor? = null)

enum class GroceryMessage {
    LoadFailed,
    SaveFailed,
    DeleteFailed,

    /** An item was completed, and can still be put back. See [GroceryListState.undoableDeletion]. */
    ItemCompleted,

    /** The row was deleted elsewhere while the editor had it open. */
    ItemNoLongerExists,
}

sealed interface GroceryListEvent : MviEvent {
    data class EditorOpened(val target: EditorTarget) : GroceryListEvent
    data class EditorNameChanged(val value: String) : GroceryListEvent
    data class EditorDescriptionChanged(val value: String) : GroceryListEvent
    data object EditorConfirmed : GroceryListEvent
    data object EditorCancelled : GroceryListEvent
    data class CompleteItem(val id: Long) : GroceryListEvent

    /** Puts back the item the last [CompleteItem] removed. */
    data object UndoCompleteClicked : GroceryListEvent
    data object RetryClicked : GroceryListEvent
    data object MessageShown : GroceryListEvent
}

/**
 * This screen raises no one-shot events: everything it shows is in [GroceryListState].
 *
 * [BaseViewModel][pl.lejdi.plannerkmp.core.mvi.BaseViewModel] is typed on an effect type, so the
 * screen needs one to name — but an *uninhabited* type says what this one says, and says it to the
 * compiler: `sendEffect` cannot be called, because there is no value to call it with. The screen
 * correspondingly does not collect the effect flow, rather than subscribing to a stream that can
 * never emit.
 */
sealed interface GroceryListEffect : MviEffect
