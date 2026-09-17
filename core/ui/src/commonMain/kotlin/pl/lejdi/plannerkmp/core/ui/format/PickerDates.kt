package pl.lejdi.plannerkmp.core.ui.format

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * The two conversions Material's date picker needs, in one place and under test.
 *
 * `DatePickerState` speaks epoch milliseconds and interprets them in **UTC** — a calendar day is
 * picked, not an instant, so anchoring it anywhere else shifts the result by a day for roughly half
 * the world. That choice is load-bearing and it is the opposite of the local-date model the rest of
 * the app uses, which is exactly why it should not be four inline expressions in a screen file. It
 * used to be: two `atStartOfDayIn(TimeZone.UTC)` calls and one `Instant.fromEpochMilliseconds(...)`
 * written out among the composables, in a codebase that otherwise keeps every date rule behind
 * `TaskSchedule` and every date format here, with a test.
 */
fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** The inverse of [toPickerMillis]. */
fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
