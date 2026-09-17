package pl.lejdi.plannerkmp.feature.routines.data

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.LocalDate

/**
 * ISO-8601 text, so a date sorts and compares correctly in SQL as well as in Kotlin.
 *
 * Deliberately a copy of :feature:tasks' adapter rather than a shared one in :core:database. The
 * rule is `feature -> core, never feature -> feature`, and hoisting a two-line adapter into a core
 * module would put a kotlinx-datetime dependency on every consumer of :core:database to save four
 * lines.
 */
internal object LocalDateColumnAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}
