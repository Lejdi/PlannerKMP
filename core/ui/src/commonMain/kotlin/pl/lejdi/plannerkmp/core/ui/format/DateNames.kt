package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.format.char

/**
 * The month's name in the device's language, e.g. "September" / "wrzesień".
 *
 * Platform locale data rather than string resources. `:feature:tasks` used to carry nineteen
 * hand-written resources — twelve months, seven weekdays — behind two `when` maps that ended in
 * `error("Unmapped month")`. That has three costs: a second feature needing a date name copies all
 * nineteen, the app can only ever render the languages somebody hand-translates, and a device set
 * to any other language falls back to English while the rest of the system is not. Both platforms
 * already ship every locale's names.
 *
 * The *format* form is used deliberately, on both platforms. Several languages inflect a month
 * differently on its own than inside a full date — Polish "wrzesień" alone versus "września" in
 * "5 września" — and these names are rendered inside a date, which is the format case.
 */
expect fun Month.displayName(): String

/** The weekday's name in the device's language, e.g. "Monday" / "poniedziałek". */
expect fun DayOfWeek.displayName(): String

/**
 * The weekday's abbreviated name, e.g. "Mon" / "pon" — for a chip or a column header, where the
 * full name does not fit.
 *
 * Deliberately not `displayName().take(3)`. That happens to work in English and Polish and is
 * wrong elsewhere: the abbreviation is not always a prefix, and in the languages that write the
 * weekday in a non-Latin script it is not always three characters either. Both platforms already
 * ship these names, for the same reason they ship the full ones.
 */
expect fun DayOfWeek.shortDisplayName(): String

/**
 * Numeric formats, which need no translation and so stay here rather than in platform code.
 *
 * `dd-mm-yyyy` and `hh:mm` are what the edit form's tap-to-open fields show.
 */
private val fieldDateFormat = LocalDate.Format {
    dayOfMonth()
    char('-')
    monthNumber()
    char('-')
    year()
}

private val fieldTimeFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
}

/** The date as `dd-mm-yyyy`, for a form field. */
fun LocalDate.toFieldString(): String = fieldDateFormat.format(this)

/** The time as `hh:mm`, for a form field. */
fun LocalTime.toFieldString(): String = fieldTimeFormat.format(this)
