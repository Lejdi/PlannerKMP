package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Task builders for tests, one per kind of schedule.
 *
 * Before [TaskSchedule] existed, every test built a `Task` out of the same four loose fields and
 * each one chose its own combination — `daysInterval = 0, asap = true` here, `asap = false` with a
 * null end date there — which meant a test could describe a task the app could never store, and
 * some did. Naming the three kinds makes a test say which one it means.
 */
fun asapTask(
    id: Long = 1L,
    name: String = "Task",
    description: String? = null,
    createdOn: LocalDate = LocalDate(2026, 9, 1),
    hour: LocalTime? = null,
) = Task(id, name, description, TaskSchedule.Asap(createdOn = createdOn, hour = hour))

fun oneTimeTask(
    id: Long = 1L,
    name: String = "Task",
    description: String? = null,
    date: LocalDate = LocalDate(2026, 9, 1),
    hour: LocalTime? = null,
) = Task(id, name, description, TaskSchedule.OneTime(date = date, hour = hour))

fun periodicTask(
    id: Long = 1L,
    name: String = "Task",
    description: String? = null,
    startDate: LocalDate = LocalDate(2026, 9, 1),
    daysInterval: Int = 7,
    endDate: LocalDate? = null,
    hour: LocalTime? = null,
) = Task(
    id,
    name,
    description,
    TaskSchedule.Periodic(
        startDate = startDate,
        daysInterval = daysInterval,
        endDate = endDate,
        hour = hour,
    ),
)

/** The date a task's schedule is anchored at, for assertions about it having moved. */
val Task.anchorDate: LocalDate
    get() = when (val schedule = schedule) {
        is TaskSchedule.Asap -> schedule.createdOn
        is TaskSchedule.OneTime -> schedule.date
        is TaskSchedule.Periodic -> schedule.startDate
    }
