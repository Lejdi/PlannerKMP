package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormattingTest {

    @Test
    fun formatsAsTwoLineDayMonthYearAndWeekdayName() {
        val date = LocalDate(2025, 7, 13)

        assertEquals("13 July 2025\nSunday", date.toCardDisplayString())
    }
}
