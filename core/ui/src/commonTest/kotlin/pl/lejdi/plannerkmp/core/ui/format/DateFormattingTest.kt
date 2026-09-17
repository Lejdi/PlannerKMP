package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lives in `core:ui`, which owns the code. It used to sit in `:feature:tasks`, so this module's
 * only coverage was contributed by another module and would have disappeared with it.
 */
class DateFormattingTest {

    @Test
    fun editFieldFormatsAreNumericAndLocaleNeutral() {
        assertEquals("08-09-2026", LocalDate(2026, 9, 8).toFieldString())
        assertEquals("09:30", LocalTime(9, 30).toFieldString())
    }

    /**
     * Month and weekday names come from the platform's locale data, so the exact text depends on the
     * device language and is not the thing to assert here. What is worth pinning down in common code
     * is that every entry resolves to a distinct, non-blank name — the failure mode of the old
     * hand-written `when` maps was a missing or duplicated branch.
     *
     * An off-by-one into the platform's symbol array does *not* fail this way: every name stays
     * unique, just attached to the wrong day. That needs real text to detect, which needs a pinned
     * locale — see the JVM-only test beside this one. The two assertions that used to claim to cover
     * it here compared `DayOfWeek.MONDAY.displayName()` with
     * `LocalDate(2026, 9, 7).dayOfWeek.displayName()`, and 2026-09-07 *is* a Monday, so both sides
     * called the same function on the same enum constant.
     */
    @Test
    fun everyWeekdayHasItsOwnNonBlankName() {
        val names = DayOfWeek.entries.map { it.displayName() }

        assertEquals(7, names.size)
        assertEquals(7, names.distinct().size, "two weekdays resolved to the same name: $names")
        assertTrue(names.none { it.isBlank() }, "a weekday resolved to a blank name: $names")
    }

    @Test
    fun everyMonthHasItsOwnNonBlankName() {
        val names = Month.entries.map { it.displayName() }

        assertEquals(12, names.size)
        assertEquals(12, names.distinct().size, "two months resolved to the same name: $names")
        assertTrue(names.none { it.isBlank() }, "a month resolved to a blank name: $names")
    }

    @Test
    fun pickerMillisRoundTripThroughUtc() {
        val date = LocalDate(2026, 9, 8)

        assertEquals(date, date.toPickerMillis().toPickerDate())
    }

    /**
     * The UTC anchoring is the load-bearing part.
     *
     * Material's date picker interprets its milliseconds in UTC, so a calendar day converted through
     * any other zone comes back a day out for roughly half the world. 2026-09-08T00:00Z is
     * 1788825600000.
     */
    @Test
    fun aPickedDayIsAnchoredAtUtcMidnight() {
        assertEquals(1_788_825_600_000L, LocalDate(2026, 9, 8).toPickerMillis())
        assertEquals(LocalDate(2026, 9, 8), 1_788_825_600_000L.toPickerDate())
    }

    /** Any instant within the UTC day maps back to that day, not to its neighbour. */
    @Test
    fun anyMillisecondWithinTheUtcDayMapsToThatDay() {
        val startOfDay = LocalDate(2026, 9, 8).toPickerMillis()
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1

        assertEquals(LocalDate(2026, 9, 8), startOfDay.toPickerDate())
        assertEquals(LocalDate(2026, 9, 8), endOfDay.toPickerDate())
        assertEquals(LocalDate(2026, 9, 9), (endOfDay + 1).toPickerDate())
    }
}
