package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/**
 * `monthSymbols`/`weekdaySymbols` — the *format* form, matching the Android side.
 *
 * These names are rendered inside a full date ("17 September 2026"), and the languages that
 * distinguish the two forms want the format one in that position: Polish "17 września", not
 * "17 wrzesień". Taking the standalone form here and the format form on Android would also mean the
 * two platforms rendering the same date differently.
 */
private object Symbols {
    private var localeId: String? = null
    private var months: List<String> = emptyList()
    private var weekdays: List<String> = emptyList()

    /** Cached per locale: allocating an `NSDateFormatter` per day cell per frame is not free. */
    fun current(): Pair<List<String>, List<String>> {
        val locale = NSLocale.currentLocale
        val id = locale.localeIdentifier
        if (id != localeId) {
            val formatter = NSDateFormatter().apply { this.locale = locale }
            months = formatter.monthSymbols.filterIsInstance<String>()
            weekdays = formatter.weekdaySymbols.filterIsInstance<String>()
            localeId = id
        }
        return months to weekdays
    }
}

actual fun Month.displayName(): String = Symbols.current().first[number - 1]

/**
 * Foundation returns a 0-based, Sunday-first array of seven. kotlinx-datetime counts ISO days,
 * Monday 1 through Sunday 7, so Sunday (7) wraps to index 0.
 */
actual fun DayOfWeek.displayName(): String = Symbols.current().second[isoDayNumber % 7]
