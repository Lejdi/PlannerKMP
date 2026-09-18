package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * `java.text.DateFormatSymbols` — a JDK class, deliberately.
 *
 * The obvious alternatives both fail one of the two requirements. `android.icu.text` can be asked
 * for the standalone form, but it is a *framework* class: in a JVM host test it is a stub that
 * throws "not mocked", so the whole of this file would be untestable off a device — the same trap
 * `RestorableViewModel` documents for `Bundle`. `java.time`'s `TextStyle.FULL_STANDALONE` needs
 * API 26 and this app ships to 24, which would mean enabling core library desugaring for one string.
 *
 * This class is in both the JDK and Android with the same behaviour and the same data, so the host
 * test exercises the real implementation.
 *
 * It returns the *format* form rather than the standalone one, which is the right form here: these
 * names are rendered inside a full date ("17 September 2026"), and the languages that distinguish
 * the two want the format form in exactly that position — Polish "17 września", not "17 wrzesień".
 */
private object Symbols {

    /** The three arrays one locale bundle yields, so a lookup cannot return a mismatched pair. */
    class Names(
        val months: Array<String>,
        val weekdays: Array<String>,
        val shortWeekdays: Array<String>,
    )

    private var locale: Locale? = null
    private var names: Names = Names(emptyArray(), emptyArray(), emptyArray())

    /**
     * Rebuilt only when the device language changes: constructing `DateFormatSymbols` loads a
     * locale bundle, and these are read once per visible day cell on every recomposition.
     */
    @Synchronized
    fun of(current: Locale): Names {
        if (current != locale) {
            val symbols = DateFormatSymbols.getInstance(current)
            names = Names(
                months = symbols.months,
                weekdays = symbols.weekdays,
                shortWeekdays = symbols.shortWeekdays,
            )
            locale = current
        }
        return names
    }
}

/** `getMonths()` is 0-based, January first, with a trailing empty slot for a 13th month. */
actual fun Month.displayName(): String = Symbols.of(Locale.getDefault()).months[number - 1]

/**
 * `getWeekdays()` is a 1-based, Sunday-first array of length 8 (index 0 unused), following
 * `Calendar.SUNDAY == 1`. kotlinx-datetime counts ISO days, Monday 1 through Sunday 7, so Sunday
 * wraps round to the front.
 */
actual fun DayOfWeek.displayName(): String =
    Symbols.of(Locale.getDefault()).weekdays[weekdayIndex]

/** `getShortWeekdays()` is laid out exactly like `getWeekdays()`, so the index is the same. */
actual fun DayOfWeek.shortDisplayName(): String =
    Symbols.of(Locale.getDefault()).shortWeekdays[weekdayIndex]

/**
 * The one place the Sunday-first wrap-around is written down on this platform.
 *
 * It used to be spelled out at the single call site that needed it; a second reader of the same
 * arrays is exactly how an off-by-one gets copied with a typo, which is what
 * `DateNamesShiftTest` exists to catch.
 */
private val DayOfWeek.weekdayIndex: Int get() = isoDayNumber % 7 + 1
