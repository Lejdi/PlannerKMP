package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * ISO-8601 text, so a date sorts and compares correctly in SQL as well as in Kotlin.
 *
 * Deliberately a copy of the other features' adapter rather than a shared one in :core:database.
 * The rule is `feature -> core, never feature -> feature`, and hoisting a two-line adapter into a
 * core module would put a kotlinx-datetime dependency on every consumer of :core:database to save
 * four lines.
 */
internal object LocalDateColumnAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

/**
 * The ISO day number — Monday 1 through Sunday 7 — rather than the enum's name.
 *
 * A number is what lets `ORDER BY dayOfWeek` come out in calendar order. Storing "FRIDAY" would
 * sort the week alphabetically, and re-sorting in Kotlin afterwards would undo the point of having
 * the query do it at all.
 */
internal object DayOfWeekColumnAdapter : ColumnAdapter<DayOfWeek, Long> {
    /**
     * kotlinx-datetime's own factory, which throws outside `1..7`.
     *
     * That is the behaviour wanted here: a corrupt row becomes a caught database failure the UI
     * can report, rather than a silent Monday that looks like data.
     */
    override fun decode(databaseValue: Long): DayOfWeek = DayOfWeek(databaseValue.toInt())

    override fun encode(value: DayOfWeek): Long = value.isoDayNumber.toLong()
}
