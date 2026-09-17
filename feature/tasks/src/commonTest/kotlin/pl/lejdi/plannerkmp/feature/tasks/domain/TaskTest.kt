package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What is left to test about a task's kind.
 *
 * These tests used to assert how `type` was *derived* from `asap` and `daysInterval` — including a
 * case named "asap true is Asap regardless of daysInterval", which pinned down the app's behaviour
 * for a task that claimed to be both ASAP and repeating every five days. That test has no successor
 * because its subject no longer exists: [TaskSchedule] gives each kind only the fields it has, so
 * the contradiction cannot be constructed to be asked about. What remains worth asserting is the
 * mapping from each schedule to its [TaskType], and the one invariant a schedule still enforces at
 * runtime.
 */
class TaskTest {

    private val date = LocalDate(2026, 1, 1)

    @Test
    fun asapScheduleIsAsapType() {
        assertEquals(TaskType.Asap, asapTask(createdOn = date).type)
    }

    @Test
    fun oneTimeScheduleIsOneTimeType() {
        assertEquals(TaskType.OneTime, oneTimeTask(date = date).type)
    }

    @Test
    fun periodicScheduleIsPeriodicType() {
        assertEquals(TaskType.Periodic, periodicTask(startDate = date, daysInterval = 7).type)
    }

    @Test
    fun aPeriodicScheduleRefusesANonPositiveInterval() {
        assertFailsWith<IllegalArgumentException> {
            TaskSchedule.Periodic(startDate = date, daysInterval = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TaskSchedule.Periodic(startDate = date, daysInterval = -3)
        }
    }

    @Test
    fun onlyARepeatingTaskHasAnOccurrenceToAdvanceTo() {
        assertNull(asapTask(createdOn = date).nextOccurrenceAfter(date))
        assertNull(oneTimeTask(date = date).nextOccurrenceAfter(date))
        assertEquals(
            LocalDate(2026, 1, 8),
            periodicTask(startDate = date, daysInterval = 7).nextOccurrenceAfter(date),
        )
    }

    /** Moving an anchor is meaningful only for a repeating task; the others are returned intact. */
    @Test
    fun startingFromOnlyMovesARepeatingTask() {
        val later = LocalDate(2026, 3, 1)

        assertEquals(date, asapTask(createdOn = date).startingFrom(later).anchorDate)
        assertEquals(date, oneTimeTask(date = date).startingFrom(later).anchorDate)
        assertEquals(
            later,
            periodicTask(startDate = date, daysInterval = 7).startingFrom(later).anchorDate,
        )
    }
}
