package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The recurrence rules, directly.
 *
 * They had no test file: everything was reached through `planCleanup` and `ObserveTasksForDashboard`,
 * which only ever call them with the arguments those two use — so `occurrenceOnOrAfter`'s
 * already-due branch, its end-date clamp and `hasExpiredBy`'s boundary were unreachable from any
 * test, although all five functions are public domain API and the design argument for the sealed
 * type is that every rule accounts for every kind.
 */
class TaskScheduleTest {

    private val today = LocalDate(2026, 9, 8)
    private fun days(n: Int) = today.plus(n, DateTimeUnit.DAY)

    private val asap = TaskSchedule.Asap(createdOn = days(-30))
    private val oneTime = TaskSchedule.OneTime(date = today)
    private val periodic = TaskSchedule.Periodic(startDate = today, daysInterval = 3)

    // ---- occursOn ------------------------------------------------------------------------------

    @Test
    fun anAsapTaskIsDueTodayWhateverTodayIs() {
        assertTrue(asap.occursOn(today, today))
        assertFalse(asap.occursOn(days(1), today))
        assertFalse(asap.occursOn(days(-1), today))
    }

    @Test
    fun aOneTimeTaskIsDueOnlyOnItsOwnDate() {
        assertTrue(oneTime.occursOn(today, today))
        assertFalse(oneTime.occursOn(days(1), today))
    }

    @Test
    fun aPeriodicTaskIsDueOnItsStartAndEveryIntervalAfter() {
        assertTrue(periodic.occursOn(today, today))
        assertTrue(periodic.occursOn(days(3), today))
        assertTrue(periodic.occursOn(days(6), today))
        assertFalse(periodic.occursOn(days(1), today))
        assertFalse(periodic.occursOn(days(-3), today), "never before its start date")
    }

    /** Inclusive: the end date is a due date, not the first day after the last one. */
    @Test
    fun aPeriodicTaskIsDueOnItsEndDateButNotAfter() {
        val bounded = periodic.copy(endDate = days(6))

        assertTrue(bounded.occursOn(days(6), today))
        assertFalse(bounded.occursOn(days(9), today))
    }

    // ---- occurrenceOnOrAfter -------------------------------------------------------------------

    /**
     * Both non-repeating kinds answer null, which their two callers read as *finished*.
     *
     * This is the rule that used to be `this as? Periodic ?: return null` — the one place the
     * sealed hierarchy's "adding a kind breaks the build" promise did not hold, and the one whose
     * null makes MarkTaskComplete delete rather than reschedule.
     */
    @Test
    fun onlyARepeatingScheduleHasSomewhereToMoveTo() {
        assertNull(asap.occurrenceOnOrAfter(today))
        assertNull(oneTime.occurrenceOnOrAfter(today))
        assertEquals(today, periodic.occurrenceOnOrAfter(today))
    }

    /** Asked about a date it has not reached yet, a schedule answers with its own start. */
    @Test
    fun aScheduleThatHasNotStartedYetAnswersWithItsStartDate() {
        val future = periodic.copy(startDate = days(10))

        assertEquals(days(10), future.occurrenceOnOrAfter(today))
        assertEquals(days(10), future.occurrenceOnOrAfter(days(10)))
    }

    /** Catch-up is one jump and preserves the phase, rather than stepping interval by interval. */
    @Test
    fun aLateScheduleCatchesUpToTheNextOccurrenceOnItsOwnCadence() {
        val late = periodic.copy(startDate = days(-10))

        // -10, -7, -4, -1, +2 — the first on or after today is +2.
        assertEquals(days(2), late.occurrenceOnOrAfter(today))
    }

    @Test
    fun anOccurrenceExactlyOnTheAskedDateIsThatDate() {
        val aligned = periodic.copy(startDate = days(-3))

        assertEquals(today, aligned.occurrenceOnOrAfter(today))
    }

    /** Still inside its end date, but with no occurrence left before it: finished. */
    @Test
    fun aScheduleWhoseNextOccurrenceWouldPassItsEndDateIsFinished() {
        val bounded = periodic.copy(endDate = days(1))

        assertEquals(today, bounded.occurrenceOnOrAfter(today))
        assertNull(bounded.occurrenceOnOrAfter(days(1)), "the next one would be day 3, past day 1")
    }

    // ---- nextOccurrenceAfter -------------------------------------------------------------------

    @Test
    fun nextOccurrenceAfterIsStrictlyLater() {
        assertEquals(days(3), periodic.nextOccurrenceAfter(today))
        assertEquals(days(3), periodic.nextOccurrenceAfter(days(1)))
        assertNull(oneTime.nextOccurrenceAfter(today))
        assertNull(asap.nextOccurrenceAfter(today))
    }

    // ---- hasExpiredBy --------------------------------------------------------------------------

    @Test
    fun anAsapTaskNeverExpires() {
        assertFalse(asap.hasExpiredBy(today))
        assertFalse(asap.hasExpiredBy(days(365)))
    }

    /** The boundary: a task due today has not expired today. */
    @Test
    fun aOneTimeTaskExpiresTheDayAfterItsDate() {
        assertFalse(oneTime.hasExpiredBy(today))
        assertTrue(oneTime.hasExpiredBy(days(1)))
    }

    /** The same boundary, and the one a `<` / `<=` slip would delete a task on its own last day. */
    @Test
    fun aPeriodicTaskExpiresTheDayAfterItsEndDate() {
        val bounded = periodic.copy(endDate = days(6))

        assertFalse(bounded.hasExpiredBy(days(6)))
        assertTrue(bounded.hasExpiredBy(days(7)))
    }

    @Test
    fun aPeriodicTaskWithNoEndDateNeverExpires() {
        assertFalse(periodic.hasExpiredBy(days(3650)))
    }

    // ---- startingFrom --------------------------------------------------------------------------

    @Test
    fun startingFromMovesOnlyARepeatingSchedule() {
        assertEquals(days(5), (periodic.startingFrom(days(5)) as TaskSchedule.Periodic).startDate)
        assertEquals(asap, asap.startingFrom(days(5)))
        assertEquals(oneTime, oneTime.startingFrom(days(5)))
    }

    @Test
    fun startingFromKeepsEverythingElseAboutTheSchedule() {
        val bounded = periodic.copy(endDate = days(30))

        val moved = bounded.startingFrom(days(3)) as TaskSchedule.Periodic

        assertEquals(days(30), moved.endDate)
        assertEquals(3, moved.daysInterval)
        assertEquals(bounded.hour, moved.hour)
    }

    // ---- construction --------------------------------------------------------------------------

    /**
     * A non-positive interval would make the catch-up arithmetic walk backwards forever, so the
     * type refuses it rather than trusting every caller to have gone through TaskDraft.create.
     */
    @Test
    fun aPeriodicScheduleRefusesANonPositiveInterval() {
        assertFailsWith<IllegalArgumentException> {
            TaskSchedule.Periodic(startDate = today, daysInterval = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TaskSchedule.Periodic(startDate = today, daysInterval = -1)
        }
    }

    @Test
    fun theKindOfAScheduleIsDerivedFromItsType() {
        assertEquals(TaskType.Asap, asap.type)
        assertEquals(TaskType.OneTime, oneTime.type)
        assertEquals(TaskType.Periodic, periodic.type)
    }
}
