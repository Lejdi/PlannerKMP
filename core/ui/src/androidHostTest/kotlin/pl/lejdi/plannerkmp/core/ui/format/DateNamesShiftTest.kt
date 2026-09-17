package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The off-by-one the common tests cannot see.
 *
 * `DayOfWeek.displayName()` indexes a Sunday-first platform array with `isoDayNumber % 7 + 1`
 * (`java.text.DateFormatSymbols.getWeekdays()` is 1-based, length 8, index 0 unused). A shift of one
 * leaves every name distinct and non-blank, so no structural check catches it — only real text does,
 * and real text needs a pinned locale.
 *
 * JVM-only because that is where the locale can be pinned. The iOS actual does its own arithmetic
 * (`isoDayNumber % 7` into a 0-based array of seven), which this does not cover; the two
 * implementations differ precisely where an off-by-one lives, so they are worth reading together.
 */
class DateNamesShiftTest {

    private lateinit var original: Locale

    @BeforeTest
    fun setUp() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun everyWeekdayResolvesToItsOwnName() {
        assertEquals("Monday", DayOfWeek.MONDAY.displayName())
        assertEquals("Tuesday", DayOfWeek.TUESDAY.displayName())
        assertEquals("Wednesday", DayOfWeek.WEDNESDAY.displayName())
        assertEquals("Thursday", DayOfWeek.THURSDAY.displayName())
        assertEquals("Friday", DayOfWeek.FRIDAY.displayName())
        assertEquals("Saturday", DayOfWeek.SATURDAY.displayName())
        // The wrap-around: ISO 7 has to land on index 1, not index 8.
        assertEquals("Sunday", DayOfWeek.SUNDAY.displayName())
    }

    @Test
    fun everyMonthResolvesToItsOwnName() {
        assertEquals("January", Month.JANUARY.displayName())
        assertEquals("September", Month.SEPTEMBER.displayName())
        // The last one, where a trailing empty 13th slot would show up.
        assertEquals("December", Month.DECEMBER.displayName())
    }
}
