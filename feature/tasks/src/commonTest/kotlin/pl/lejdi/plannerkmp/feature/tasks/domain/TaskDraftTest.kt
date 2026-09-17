package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.validationFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These rules used to live in TaskEditViewModel, where only that one screen could enforce them.
 */
class TaskDraftTest {

    private val today = LocalDate(2026, 9, 8)

    private fun create(
        name: String = "Water plants",
        description: String? = null,
        type: TaskType = TaskType.OneTime,
        startDate: LocalDate = today,
        endDate: LocalDate? = null,
        daysInterval: Int? = null,
    ) = TaskDraft.create(
        name = name,
        description = description,
        type = type,
        startDate = startDate,
        endDate = endDate,
        hour = null,
        daysInterval = daysInterval,
        today = today,
    )

    private fun fieldOf(result: AppResult<TaskDraft>): TaskField {
        assertTrue(result is AppResult.Failure)
        val error = result.error
        assertTrue(error is DomainError.Validation)
        val fields = error.validationFields<TaskField>()
        assertEquals(1, fields.size, "expected exactly one offending field, got $fields")
        return fields.single()
    }

    @Test
    fun blankNameIsRejectedAndNamesTheField() {
        assertEquals(TaskField.Name, fieldOf(create(name = "   ")))
    }

    @Test
    fun periodicTaskRequiresAPositiveInterval() {
        assertEquals(
            TaskField.DaysInterval,
            fieldOf(create(type = TaskType.Periodic, daysInterval = 0)),
        )
        assertEquals(
            TaskField.DaysInterval,
            fieldOf(create(type = TaskType.Periodic, daysInterval = -3)),
        )
        assertEquals(
            TaskField.DaysInterval,
            fieldOf(create(type = TaskType.Periodic, daysInterval = null)),
        )
    }

    @Test
    fun endDateBeforeStartDateIsRejected() {
        val result = create(
            type = TaskType.Periodic,
            daysInterval = 2,
            startDate = today,
            endDate = today.minus(1, DateTimeUnit.DAY),
        )

        assertEquals(TaskField.EndDate, fieldOf(result))
    }

    @Test
    fun asapTaskStartsTodayWhateverDateWasPassed() {
        val result = create(type = TaskType.Asap, startDate = LocalDate(2020, 1, 1))

        assertTrue(result is AppResult.Success)
        assertEquals(TaskSchedule.Asap(createdOn = today), result.data.schedule)
    }

    @Test
    fun endDateIsDroppedForNonPeriodicTasks() {
        val result = create(type = TaskType.OneTime, endDate = today)

        assertTrue(result is AppResult.Success)
        // A one-off has nowhere to put an end date any more: the type has no such field.
        assertTrue(result.data.schedule is TaskSchedule.OneTime)
    }

    @Test
    fun nameAndDescriptionAreTrimmedAndBlankDescriptionBecomesNull() {
        val result = create(name = "  Water plants  ", description = "   ")

        assertTrue(result is AppResult.Success)
        assertEquals("Water plants", result.data.name)
        assertNull(result.data.description)
    }

    @Test
    fun validPeriodicTaskKeepsItsInterval() {
        val result = create(type = TaskType.Periodic, daysInterval = 4, endDate = today)

        assertTrue(result is AppResult.Success)
        val schedule = result.data.schedule
        assertTrue(schedule is TaskSchedule.Periodic)
        assertEquals(4, schedule.daysInterval)
        assertEquals(today, schedule.endDate)
    }

    /**
     * The point of accumulating rather than returning the first failure: a form with three bad
     * inputs used to report them one Save at a time.
     */
    @Test
    fun everyBrokenFieldIsReportedFromOneSubmission() {
        val result = create(
            name = "  ",
            type = TaskType.Periodic,
            startDate = today,
            endDate = today.minus(5, DateTimeUnit.DAY),
            daysInterval = 0,
        )

        assertTrue(result is AppResult.Failure)
        assertEquals(
            setOf(TaskField.Name, TaskField.DaysInterval, TaskField.EndDate),
            result.error.validationFields<TaskField>(),
        )
    }

    @Test
    fun aSingleBadFieldNamesOnlyThatField() {
        val result = create(name = "   ")

        assertTrue(result is AppResult.Failure)
        val error = result.error
        assertTrue(error is DomainError.Validation)
        assertEquals(setOf(TaskField.Name), error.fields)
    }

    @Test
    fun withIdTurnsADraftIntoAStoredTask() {
        val result = create()
        assertTrue(result is AppResult.Success)

        val task = result.data.withId(42)

        assertEquals(42, task.id)
        assertEquals(result.data, task.toDraft())
    }
}
