package pl.lejdi.plannerkmp.feature.gym.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.gym.domain.DayExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymDay

/**
 * The exercise whose weight is being edited in place, and the text typed into it so far.
 *
 * One editor for the whole screen rather than a flag per row: only one field can hold focus, and a
 * set of open editors would be a state the UI cannot represent.
 */
@Serializable
data class WeightEditor(
    val exerciseId: Long,
    /**
     * The page the editor was opened on.
     *
     * An exercise planned on several days has a card on each of their pages, and the pager composes
     * a neighbouring page while it is being swiped to — so an editor keyed by the exercise alone
     * opened on every one of those cards at once, two fields contending for one focus.
     */
    @Serializable(with = DayOfWeekSerializer::class)
    val dayOfWeek: DayOfWeek,
    val text: String,
    /** Set by a rejected commit, cleared as soon as the user changes the text. */
    val isInvalid: Boolean = false,
    /**
     * Whether the field has actually held focus yet, which is what makes "focus was lost" mean
     * "the user tapped away".
     *
     * `Modifier.onFocusChanged` fires once when the field is attached, reporting *unfocused* —
     * before the user can have touched anything (`FocusChangedNode` starts at `null` and the focus
     * owner dispatches the current `Inactive` state on attach). Without this flag that event was
     * indistinguishable from leaving the field, so the editor committed and closed on the frame it
     * opened, and on screen the field looked like it refused to take focus.
     *
     * [Transient] and never restored: a field rebuilt after process death is attached again and
     * gets the same event again, so coming back as `true` would reproduce the bug on every restore.
     * It is also not something the user typed, which is the only thing this input carries.
     */
    @Transient
    val hasBeenFocused: Boolean = false,
)

/**
 * [message] lives in the state, not in the screen's own `remember`: the state object is the single
 * source of truth, and a message held in composition local state is lost on rotation while
 * everything around it survives.
 */
data class GymState(
    override val isLoading: Boolean = true,
    override val loadFailed: Boolean = false,
    /** Seven days once loaded, Monday first — see `ObserveGymWeek`. */
    val days: List<GymDay> = emptyList(),
    /**
     * Today's weekday, kept live rather than read once at construction.
     *
     * The pager opens on it and one page is marked with it, so a screen left open across midnight
     * has to move the marker without being touched. `:feature:tasks`' edit form carries the same
     * note about the one-shot read it used to do.
     */
    val today: DayOfWeek,
    val weightEditor: WeightEditor? = null,
    /**
     * The exercises with a write in flight, rather than one flag for the whole screen.
     *
     * One boolean disabled every checkbox on the page while a single tick was being written, and
     * silently dropped a tap on any other row — which on a day with five exercises is the normal
     * way to use the screen.
     */
    val pendingExerciseIds: Set<Long> = emptySet(),
    override val message: UiMessage<GymMessage>? = null,
) : LoadableState<GymMessage> {

    /**
     * An empty *week*, which is the only emptiness worth a full-screen message: a single day with
     * nothing planned is a normal state and gets a line on its own page.
     */
    override val isEmpty: Boolean get() = days.all { it.exercises.isEmpty() }

    override val isSubmitting: Boolean get() = pendingExerciseIds.isNotEmpty()

    /** Whether *this* exercise has a write open. */
    fun isPending(id: Long): Boolean = id in pendingExerciseIds

    /**
     * The exercise with this id as the last emission had it, on any page, or null once it is gone.
     *
     * An exercise planned on several days appears on each of their pages, and any match will do for
     * what it is used for here — its weight, and whether it still exists — because those are the
     * same on every page. Its `doneSets` is not: see [findTodayExercise].
     */
    fun findExercise(id: Long): DayExercise? =
        days.firstNotNullOfOrNull { day -> day.exercises.find { it.id == id } }

    /**
     * The exercise with this id as *today's* page shows it, or null when it is not planned today.
     *
     * The one to plan a tick from. Every other page reports zero series done, so a tick planned from
     * the first match in the week would count up from nothing whenever that match came before today.
     */
    fun findTodayExercise(id: Long): DayExercise? =
        days.find { it.dayOfWeek == today }?.exercises?.find { it.id == id }
}

/**
 * The part of this screen the user typed, and the only part worth carrying through a restart.
 *
 * [days] and [GymState.today] are deliberately absent: the first re-emits from the database, and
 * re-reading the clock is always correct and cannot go stale.
 */
@Serializable
data class GymInput(val weightEditor: WeightEditor? = null)

/**
 * A typed message, not a string. The raw text on a [pl.lejdi.plannerkmp.core.common.DomainError]
 * is driver output meant for logs; the UI layer picks the wording (and the translation).
 */
enum class GymMessage {
    LoadFailed,
    ToggleFailed,
    WeightSaveFailed,

    /** The typed weight is not a number, or is outside the bounds the domain accepts. */
    WeightInvalid,

    /** The row went while the card was on screen, so no retry of that write can succeed. */
    ExerciseNoLongerExists,
}

sealed interface GymEvent : MviEvent {
    /**
     * [serieNumber] is 1-based, matching the label the user reads.
     *
     * Carries the id rather than the [DayExercise], for the reason the nav key gives for doing the
     * same: an exercise handed over at render time is a snapshot, and the ViewModel receiving it
     * already holds the live list that snapshot was taken from.
     */
    data class SerieToggled(val exerciseId: Long, val serieNumber: Int) : GymEvent

    /** [dayOfWeek] is the page the card was tapped on — see [WeightEditor.dayOfWeek]. */
    data class WeightEditStarted(val exerciseId: Long, val dayOfWeek: DayOfWeek) : GymEvent
    data class WeightTextChanged(val text: String) : GymEvent

    /**
     * The field gained or lost focus.
     *
     * The screen forwards the fact; what it *means* is decided in the ViewModel, because "lost
     * focus" only means "the user left the field" once the field has held focus — see
     * [WeightEditor.hasBeenFocused]. Deciding that in the composable put a rule somewhere no test
     * could reach it, and the rule was wrong.
     */
    data class WeightEditorFocusChanged(val isFocused: Boolean) : GymEvent
    data object WeightEditCommitted : GymEvent
    data object WeightEditCancelled : GymEvent

    /** Sent by both the long press and the edit icon: two affordances, one intent. */
    data class EditExerciseClicked(val exerciseId: Long) : GymEvent

    /** [dayOfWeek] is the page the user is looking at, so a new exercise lands on that day. */
    data class AddExerciseClicked(val dayOfWeek: DayOfWeek) : GymEvent

    /** Re-subscribes after a load failure — the reason a "Retry" button can mean it. */
    data object RetryClicked : GymEvent

    /** The snackbar has been shown; drop the message so it is not repeated on rotation. */
    data object MessageShown : GymEvent
}

/** Only genuinely one-shot events. Everything renderable is in [GymState]. */
sealed interface GymEffect : MviEffect {
    data class NavigateToAddExercise(val dayOfWeek: DayOfWeek) : GymEffect
    data class NavigateToEditExercise(val exerciseId: Long) : GymEffect
}
