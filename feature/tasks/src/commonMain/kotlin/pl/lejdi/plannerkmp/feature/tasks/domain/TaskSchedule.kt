package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * When a task is due.
 *
 * A sealed hierarchy rather than the four loose fields it replaces. A [Task] used to carry
 * `asap: Boolean`, `daysInterval: Int`, `startDate` and a nullable `endDate` all at once, with the
 * kind of task *derived* from them by a `when`. That made contradictions constructible and silent:
 * `Task(asap = true, daysInterval = 5, endDate = someDay)` compiled, and the derived type answered
 * "ASAP" while two of its own fields described a periodic task that nothing would ever honour. The
 * only thing standing between the app and such a row was `TaskDraft.create`, a runtime check that
 * every other construction site — a test, a mapper, a future import — simply bypassed.
 *
 * Here each kind carries exactly the fields it has, so the contradiction cannot be written down.
 * The recurrence rules below are `when (this)` over this type, so adding a kind breaks the build at
 * every rule that has to account for it.
 */
sealed interface TaskSchedule {

    /** The time of day the user set, or null when the task is not tied to one. */
    val hour: LocalTime?

    /**
     * Due today, every day, until the user completes it.
     *
     * [createdOn] is not a due date — an ASAP task is due today whatever today is — it is only the
     * day the task was raised, kept so the row has a date to store and to order by.
     */
    data class Asap(
        val createdOn: LocalDate,
        override val hour: LocalTime? = null,
    ) : TaskSchedule

    /** Due once, on [date]. */
    data class OneTime(
        val date: LocalDate,
        override val hour: LocalTime? = null,
    ) : TaskSchedule

    /**
     * Due on [startDate] and every [daysInterval] days after it, up to and including [endDate].
     *
     * @param endDate null for a task that repeats indefinitely.
     */
    data class Periodic(
        val startDate: LocalDate,
        val daysInterval: Int,
        val endDate: LocalDate? = null,
        override val hour: LocalTime? = null,
    ) : TaskSchedule {
        init {
            // A non-positive interval would make the catch-up arithmetic below walk backwards
            // forever. TaskDraft.create reports this to the user as a validation failure; reaching
            // it here means a caller built the schedule directly, which is a programming error.
            require(daysInterval > 0) { "a periodic task repeats every $daysInterval days" }
        }
    }
}

/** The kind of a schedule, for the places that have to name it — the edit form's radio buttons. */
enum class TaskType { Asap, OneTime, Periodic }

val TaskSchedule.type: TaskType
    get() = when (this) {
        is TaskSchedule.Asap -> TaskType.Asap
        is TaskSchedule.OneTime -> TaskType.OneTime
        is TaskSchedule.Periodic -> TaskType.Periodic
    }

/*
 * The recurrence rules.
 *
 * They live on the schedule, and every rule in the feature is expressed in terms of them, because
 * they used to be re-derived from the raw `asap` / `daysInterval` fields in three places — the
 * dashboard filter, the daily cleanup and "mark complete" — in three different orders, none of them
 * exhaustive. Three copies of a rule the compiler cannot check is three chances to update two.
 */

/**
 * Whether the task is due on [date].
 *
 * [today] is needed only by [TaskSchedule.Asap], whose whole definition is "due today, whenever
 * today is" — it has no due date of its own to compare against.
 */
fun TaskSchedule.occursOn(date: LocalDate, today: LocalDate): Boolean = when (this) {
    is TaskSchedule.Asap -> date == today
    is TaskSchedule.OneTime -> date == this.date
    is TaskSchedule.Periodic ->
        date >= startDate &&
            startDate.daysUntil(date) % daysInterval == 0 &&
            (endDate == null || date <= endDate)
}

/**
 * Where this schedule moves to on or after [date], or null when it has nowhere left to go.
 *
 * "Nowhere left to go" is what both callers read as *finished*: [MarkTaskComplete] deletes the task
 * and the daily cleanup drops it. So the two non-repeating kinds answer null deliberately — an ASAP
 * task is due every day but has no *later* anchor to move to, and completing it is meant to remove
 * it, not roll it to tomorrow.
 *
 * Written as an exhaustive `when` rather than `this as? Periodic ?: return null`, which is what it
 * used to be. Every other rule in this file is exhaustive precisely so that adding a schedule kind
 * breaks the build at each rule that has to account for it; the cast opted this one rule out, and
 * it is the rule whose null means "delete". A new repeating kind would have compiled cleanly and
 * silently destroyed tasks on completion.
 */
fun TaskSchedule.occurrenceOnOrAfter(date: LocalDate): LocalDate? = when (this) {
    is TaskSchedule.Asap -> null
    is TaskSchedule.OneTime -> null
    is TaskSchedule.Periodic -> {
        val daysLate = startDate.daysUntil(date)
        val occurrence = if (daysLate <= 0) {
            startDate
        } else {
            // Integer ceiling: the smallest whole number of intervals that lands on or after `date`.
            // This was `ceil(daysLate.toDouble() / daysInterval)` — floating point for date
            // arithmetic.
            val intervals = (daysLate + daysInterval - 1) / daysInterval
            startDate.plus(intervals.toLong() * daysInterval, DateTimeUnit.DAY)
        }
        occurrence.takeIf { endDate == null || it <= endDate }
    }
}

/** The first date strictly after [date] on which this task is due, or null when it is finished. */
fun TaskSchedule.nextOccurrenceAfter(date: LocalDate): LocalDate? =
    occurrenceOnOrAfter(date.plus(1, DateTimeUnit.DAY))

/** Whether the task's own schedule has run out by [today], so nothing keeps it in storage. */
fun TaskSchedule.hasExpiredBy(today: LocalDate): Boolean = when (this) {
    // ASAP tasks have no date of their own to expire; they stay until the user completes them.
    is TaskSchedule.Asap -> false
    is TaskSchedule.OneTime -> date < today
    is TaskSchedule.Periodic -> endDate != null && endDate < today
}

/**
 * The same schedule anchored at [date].
 *
 * Only a repeating task has an anchor to move, which is why this returns the receiver unchanged for
 * the others — and why the two callers that use it ([MarkTaskComplete] and the daily cleanup) only
 * ever reach it after [occurrenceOnOrAfter] has already returned non-null, i.e. only for a
 * [TaskSchedule.Periodic].
 */
fun TaskSchedule.startingFrom(date: LocalDate): TaskSchedule = when (this) {
    is TaskSchedule.Periodic -> copy(startDate = date)
    is TaskSchedule.Asap, is TaskSchedule.OneTime -> this
}
