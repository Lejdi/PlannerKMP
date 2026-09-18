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

    /** The three lists one formatter yields, so a lookup cannot return a mismatched pair. */
    class Names(
        val months: List<String>,
        val weekdays: List<String>,
        val shortWeekdays: List<String>,
    )

    private var localeId: String? = null
    private var names: Names = Names(emptyList(), emptyList(), emptyList())

    /** Cached per locale: allocating an `NSDateFormatter` per day cell per frame is not free. */
    fun current(): Names {
        val locale = NSLocale.currentLocale
        val id = locale.localeIdentifier
        if (id != localeId) {
            val formatter = NSDateFormatter().apply { this.locale = locale }
            names = Names(
                months = formatter.monthSymbols.filterIsInstance<String>(),
                weekdays = formatter.weekdaySymbols.filterIsInstance<String>(),
                shortWeekdays = formatter.shortWeekdaySymbols.filterIsInstance<String>(),
            )
            localeId = id
        }
        return names
    }
}

actual fun Month.displayName(): String = Symbols.current().months[number - 1]

actual fun DayOfWeek.displayName(): String = Symbols.current().weekdays[weekdayIndex]

/** `shortWeekdaySymbols` is laid out exactly like `weekdaySymbols`, so the index is the same. */
actual fun DayOfWeek.shortDisplayName(): String = Symbols.current().shortWeekdays[weekdayIndex]

/**
 * Foundation returns a 0-based, Sunday-first array of seven. kotlinx-datetime counts ISO days,
 * Monday 1 through Sunday 7, so Sunday (7) wraps to index 0.
 *
 * Note this is *not* the Android index: `java.text.DateFormatSymbols` returns a 1-based array of
 * eight with an unused slot at the front. The two platforms differ precisely where an off-by-one
 * lives, which is why each writes its own arithmetic down once rather than at every call site.
 */
private val DayOfWeek.weekdayIndex: Int get() = isoDayNumber % 7
