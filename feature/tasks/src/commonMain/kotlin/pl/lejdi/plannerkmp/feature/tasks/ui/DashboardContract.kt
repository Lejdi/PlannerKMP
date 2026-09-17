package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.mvi.LoadableState
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.UiMessage
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

/**
 * [message] lives in the state, not in the screen's own `remember`: the state object is the single
 * source of truth, and a message held in composition local state is lost on rotation while
 * everything around it survives.
 */
data class DashboardState(
    override val isLoading: Boolean = true,
    override val loadFailed: Boolean = false,
    /**
     * The tasks whose completion is in flight, rather than one flag for the whole screen.
     *
     * One boolean disabled every card on every visible day while a single tick was being written —
     * and silently dropped a tap on any other card, which on a day with several tasks is the normal
     * way to use the screen.
     */
    val completingTaskIds: Set<Long> = emptySet(),
    val days: List<DashboardDay> = emptyList(),
    val revealedTaskId: Long? = null,
    override val message: UiMessage<DashboardMessage>? = null,
) : LoadableState<DashboardMessage> {
    override val isEmpty: Boolean get() = days.isEmpty()

    override val isSubmitting: Boolean get() = completingTaskIds.isNotEmpty()

    /** Whether *this* task has a write open. A periodic task is on several pages; all of them. */
    fun isCompleting(id: Long): Boolean = id in completingTaskIds

    /**
     * The task with this id as the last emission had it, or null once it is gone.
     *
     * A periodic task appears on several of [days]; every page holds the same object, so the first
     * match is the whole answer.
     */
    fun findTask(id: Long): Task? = days.firstNotNullOfOrNull { day -> day.tasks.find { it.id == id } }
}

/** The part of this screen the user set, and the only part worth carrying through a restart. */
@Serializable
data class DashboardInput(val revealedTaskId: Long? = null)

/**
 * A typed message, not a string. The raw text on a [pl.lejdi.plannerkmp.core.common.DomainError]
 * is driver output meant for logs; the UI layer picks the wording (and the translation).
 */
enum class DashboardMessage {
    LoadFailed,
    CompleteFailed,
}

sealed interface DashboardEvent : MviEvent {
    data class RevealActions(val taskId: Long) : DashboardEvent
    data object DismissActions : DashboardEvent

    /**
     * [onDate] is the day whose card was ticked, which is not necessarily the task's own next due
     * date — see [pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete.Params].
     *
     * Carries the id rather than the [Task] itself, for the reason
     * [pl.lejdi.plannerkmp.feature.tasks.TasksNavKey] gives for doing the same: a task handed over
     * at render time is a snapshot, and the ViewModel that receives it already holds the live list
     * the snapshot was taken from. Passing the entity meant a completion could be planned from a
     * schedule the daily cleanup or an edit on the other screen had already moved, with the state
     * the ViewModel was observing sitting right there unread.
     */
    data class CompleteTask(val taskId: Long, val onDate: LocalDate) : DashboardEvent
    data object AddTaskClicked : DashboardEvent
    data class EditTaskClicked(val taskId: Long) : DashboardEvent

    /** Re-subscribes after a load failure — the reason a "Retry" button can mean it. */
    data object RetryClicked : DashboardEvent

    /** The snackbar has been shown; drop the message so it is not repeated on rotation. */
    data object MessageShown : DashboardEvent
}

/** Only genuinely one-shot events. Everything renderable is in [DashboardState]. */
sealed interface DashboardEffect : MviEffect {
    data object NavigateToAddTask : DashboardEffect
    data class NavigateToEditTask(val taskId: Long) : DashboardEffect
}
