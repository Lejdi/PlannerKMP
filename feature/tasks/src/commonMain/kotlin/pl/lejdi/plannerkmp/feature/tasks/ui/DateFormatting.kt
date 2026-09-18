package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import pl.lejdi.plannerkmp.core.ui.format.displayName
import pl.lejdi.plannerkmp.feature.tasks.resources.Res
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_card_date

/**
 * The date line of a day's heading: "17 September 2026".
 *
 * Only the *order* of the parts is a resource — `tasks_card_date` — because that genuinely differs
 * by language. The month and weekday names come from the platform's own locale data (see
 * [displayName]); this file used to carry nineteen hand-written English resources and two `when`
 * maps to reach them, which meant the app rendered English on a device set to anything nobody had
 * hand-translated.
 */
@Composable
fun LocalDate.toDateLine(): String =
    stringResource(Res.string.tasks_card_date, day, month.displayName(), year)

/**
 * The weekday line beneath it.
 *
 * Separate from [toDateLine] rather than joined with a `"\n"`. The join put the line order and the
 * separator in Kotlin, where a locale that wants the weekday first cannot reach them, and it made
 * the two lines one `Text` — so they could not be styled apart and a screen reader had no way to
 * treat the heading as a heading.
 */
@Composable
fun LocalDate.toWeekdayLine(): String = dayOfWeek.displayName()
