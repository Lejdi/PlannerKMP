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
    private var locale: Locale? = null
    private var months: Array<String> = emptyArray()
    private var weekdays: Array<String> = emptyArray()

    /**
     * Rebuilt only when the device language changes: constructing `DateFormatSymbols` loads a
     * locale bundle, and these are read once per visible day cell on every recomposition.
     */
    @Synchronized
    fun of(current: Locale): Pair<Array<String>, Array<String>> {
        if (current != locale) {
            val symbols = DateFormatSymbols.getInstance(current)
            months = symbols.months
            weekdays = symbols.weekdays
            locale = current
        }
        return months to weekdays
    }
}

/** `getMonths()` is 0-based, January first, with a trailing empty slot for a 13th month. */
actual fun Month.displayName(): String = Symbols.of(Locale.getDefault()).first[number - 1]

/**
 * `getWeekdays()` is a 1-based, Sunday-first array of length 8 (index 0 unused), following
 * `Calendar.SUNDAY == 1`. kotlinx-datetime counts ISO days, Monday 1 through Sunday 7, so Sunday
 * wraps round to the front.
 */
actual fun DayOfWeek.displayName(): String =
    Symbols.of(Locale.getDefault()).second[isoDayNumber % 7 + 1]
