package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal object LocalDateColumnAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

internal object LocalTimeColumnAdapter : ColumnAdapter<LocalTime, String> {
    override fun decode(databaseValue: String): LocalTime = LocalTime.parse(databaseValue)
    override fun encode(value: LocalTime): String = value.toString()
}
