package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.datetime.DayOfWeek

/**
 * An exercise plus the only question this screen asks of it: how much of it is done *today*.
 *
 * [doneSets] is computed rather than stored, so it is correct the moment the date changes without
 * anything having written to the table — see [ObserveGymWeek], which is where that computation
 * lives and where the daily reset therefore happens.
 */
data class DayExercise(val exercise: GymExercise, val doneSets: Int) {
    /** Forwarded because the list's key and every event about a row need it. */
    val id: Long get() = exercise.id

    val setsCount: Int get() = exercise.setsCount

    val isDone: Boolean get() = doneSets >= setsCount
}

/**
 * One weekday's plan.
 *
 * There are always seven of these, whether or not they hold anything: the pager has a page per
 * weekday, and a day with nothing planned is a page saying so rather than a page that is missing.
 */
data class GymDay(val dayOfWeek: DayOfWeek, val exercises: List<DayExercise>)
