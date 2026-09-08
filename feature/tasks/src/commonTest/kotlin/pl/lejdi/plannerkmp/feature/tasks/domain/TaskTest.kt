package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTest {

    private val baseTask = Task(
        id = 1,
        name = "Water plants",
        description = null,
        startDate = LocalDate(2026, 1, 1),
        endDate = null,
        hour = null,
        daysInterval = 0,
        asap = false,
    )

    @Test
    fun asapTrueIsAsapTypeRegardlessOfDaysInterval() {
        val task = baseTask.copy(asap = true, daysInterval = 5)

        assertEquals(TaskType.Asap, task.type)
    }

    @Test
    fun notAsapWithZeroDaysIntervalIsOneTime() {
        val task = baseTask.copy(asap = false, daysInterval = 0)

        assertEquals(TaskType.OneTime, task.type)
    }

    @Test
    fun notAsapWithPositiveDaysIntervalIsPeriodic() {
        val task = baseTask.copy(asap = false, daysInterval = 7)

        assertEquals(TaskType.Periodic, task.type)
    }
}
