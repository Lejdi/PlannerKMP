package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.ValidationField

/** The inputs of the task form, for [DomainError.Validation] to point at. */
enum class TaskField : ValidationField { Name, DaysInterval, EndDate }

/**
 * A task that has not been persisted yet, and the only way to build a valid one.
 *
 * Every invariant the rest of the app relies on is enforced here rather than in the edit screen: a
 * ViewModel is not the only possible caller (a widget, an import or a deep link would be others),
 * and an invalid task is not merely a bad screen.
 */
@ConsistentCopyVisibility
data class TaskDraft private constructor(
    val name: String,
    val description: String?,
    val schedule: TaskSchedule,
) {
    companion object {
        /**
         * A draft of something already stored, whose invariants were checked when it was created.
         *
         * The only other way in. The constructor is private because "the only way to build a valid
         * one" was a claim the type did not make: it was an ordinary data class, so `create` was a
         * suggestion, and `copy` was a second door beside it.
         */
        internal fun ofStored(name: String, description: String?, schedule: TaskSchedule) =
            TaskDraft(name = name, description = description, schedule = schedule)

        /**
         * Builds a draft from raw form input, or reports **every** offending field.
         *
         * The failure names all of them, not the first one found. Stopping at the first meant the
         * user fixed the name, pressed Save, learned the repeat interval was also wrong, and pressed
         * Save again — one round trip per bad field, although the domain knew about all of them the
         * moment it was handed the input. The wording for each field belongs to the screen, which is
         * where the resources are.
         *
         * @param today used only for a [TaskType.Asap] task, which is raised against the current
         *   day rather than against a date the user picked.
         */
        @Suppress("LongParameterList") // A form's fields; grouping them would only move the list.
        fun create(
            name: String,
            description: String?,
            type: TaskType,
            startDate: LocalDate,
            endDate: LocalDate?,
            hour: LocalTime?,
            daysInterval: Int?,
            today: LocalDate,
        ): AppResult<TaskDraft> {
            val invalid = mutableSetOf<TaskField>()

            if (name.isBlank()) invalid += TaskField.Name

            val hasUsableInterval = daysInterval != null && daysInterval > 0
            if (type == TaskType.Periodic && !hasUsableInterval) invalid += TaskField.DaysInterval

            val effectiveStart = if (type == TaskType.Asap) today else startDate
            val effectiveEnd = endDate.takeIf { type == TaskType.Periodic }
            if (effectiveEnd != null && effectiveEnd < effectiveStart) invalid += TaskField.EndDate

            if (invalid.isNotEmpty()) {
                return AppResult.Failure(DomainError.Validation(invalid))
            }

            val schedule = when (type) {
                TaskType.Asap -> TaskSchedule.Asap(createdOn = today, hour = hour)
                TaskType.OneTime -> TaskSchedule.OneTime(date = startDate, hour = hour)
                // Safe: a Periodic type with no usable interval was rejected above.
                TaskType.Periodic -> TaskSchedule.Periodic(
                    startDate = startDate,
                    daysInterval = daysInterval!!,
                    endDate = effectiveEnd,
                    hour = hour,
                )
            }

            return AppResult.Success(
                TaskDraft(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    schedule = schedule,
                ),
            )
        }
    }
}

/**
 * A task that exists in storage. [id] is non-null by construction, so nothing has to read `0L` as
 * "not saved yet" — an unsaved task is a [TaskDraft] and cannot be mistaken for this.
 *
 * [Task] and [TaskDraft] differ by exactly the id, and both delegate *when* they are due to
 * [TaskSchedule]. They used to restate seven scheduling fields each, which meant `toDraft` and
 * `withId` were seven-line field-by-field copies that a new field could silently be left out of.
 *
 * The constructor is `internal`, like [TaskDraft]'s is private, so that "a valid task can only be
 * built through [TaskDraft.create]" is a claim the type actually makes. It used to stop at the
 * draft: [TaskDraft] was sealed shut while `Task` — the thing every other layer handles — took a
 * public constructor and a public `copy`, so any module could fabricate one with a blank name or a
 * schedule no validator had ever seen. Internal still lets this feature build one from storage
 * ([withId] and the datasource's row mapping) and lets its own tests construct fixtures; it is the
 * rest of the app that has no business minting tasks.
 */
@ConsistentCopyVisibility
data class Task internal constructor(
    val id: Long,
    val name: String,
    val description: String?,
    val schedule: TaskSchedule,
)

val Task.type: TaskType get() = schedule.type

/** Whether the task is due on [date], given that today is [today]. */
fun Task.occursOn(date: LocalDate, today: LocalDate): Boolean = schedule.occursOn(date, today)

/** The first date strictly after [date] on which this task is due, or null when it is finished. */
fun Task.nextOccurrenceAfter(date: LocalDate): LocalDate? = schedule.nextOccurrenceAfter(date)

/** The first date on or after [date] on which this task is due, or null when it is finished. */
fun Task.occurrenceOnOrAfter(date: LocalDate): LocalDate? = schedule.occurrenceOnOrAfter(date)

/** Whether the task's own schedule has run out by [today]. */
fun Task.hasExpiredBy(today: LocalDate): Boolean = schedule.hasExpiredBy(today)

/** The same task, next due on [date]. See [TaskSchedule.startingFrom]. */
fun Task.startingFrom(date: LocalDate): Task = copy(schedule = schedule.startingFrom(date))

fun Task.toDraft(): TaskDraft = TaskDraft.ofStored(name = name, description = description, schedule = schedule)

fun TaskDraft.withId(id: Long): Task =
    Task(id = id, name = name, description = description, schedule = schedule)
