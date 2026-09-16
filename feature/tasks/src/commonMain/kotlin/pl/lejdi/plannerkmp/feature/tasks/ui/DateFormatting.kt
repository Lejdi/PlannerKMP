package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

private val cardDateFormat = LocalDate.Format {
    dayOfMonth()
    char(' ')
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    year()
}

private val editFieldDateFormat = LocalDate.Format {
    dayOfMonth()
    char('-')
    monthNumber()
    char('-')
    year()
}

private val editFieldTimeFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
}

private val weekdayNames = mapOf(
    DayOfWeek.MONDAY to "Monday",
    DayOfWeek.TUESDAY to "Tuesday",
    DayOfWeek.WEDNESDAY to "Wednesday",
    DayOfWeek.THURSDAY to "Thursday",
    DayOfWeek.FRIDAY to "Friday",
    DayOfWeek.SATURDAY to "Saturday",
    DayOfWeek.SUNDAY to "Sunday",
)

fun LocalDate.toCardDisplayString(): String =
    "${cardDateFormat.format(this)}\n${weekdayNames.getValue(dayOfWeek)}"

fun LocalDate.toEditFieldDisplayString(): String = editFieldDateFormat.format(this)

fun LocalTime.toEditFieldDisplayString(): String = editFieldTimeFormat.format(this)
