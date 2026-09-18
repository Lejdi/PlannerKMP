package pl.lejdi.plannerkmp.feature.gym.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.UseCase

/**
 * The write half of the completion convention, and the one rule the checkboxes have.
 *
 * Completion is a count rather than a set of series ids, so a tap means "this many are done":
 * tapping series *k* that is not done counts up to *k*, and tapping one that is counts down to
 * *k − 1*, which is the only direction a count can be un-ticked in. Ticking the third box while
 * none is ticked therefore marks three done — the deliberate consequence of the count model, and
 * [ToggleExerciseSetTest] pins it so that it reads as intended rather than as a bug.
 *
 * [TodayProvider] is asked for the date at write time, because a tick belongs to the day it was
 * made on and not to the day the screen was opened. The date is cleared when the count returns to
 * zero: a row with nothing done carries no date, so there is nothing for [ObserveGymWeek] to
 * compare against. `:feature:routines`' `ToggleRoutineDone` holds the same convention in the
 * boolean case.
 */
class ToggleExerciseSet(
    private val datasource: GymDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<ToggleExerciseSet.Params, AppResult<Unit>> {

    /**
     * [serieNumber] is 1-based, as the label the user reads is ("Serie 1").
     *
     * Takes the [DayExercise] rather than an id: the caller resolves it from the list it is
     * observing, so the count this plans from is the one the data layer last emitted rather than
     * a snapshot taken when the card was drawn.
     */
    data class Params(val exercise: DayExercise, val serieNumber: Int)

    override suspend fun invoke(params: Params): AppResult<Unit> {
        val tapped = params.serieNumber
        val alreadyDone = tapped <= params.exercise.doneSets
        val completedSets = (if (alreadyDone) tapped - 1 else tapped)
            .coerceIn(0, params.exercise.setsCount)
        return datasource.updateCompletedSets(
            id = params.exercise.id,
            completedSets = completedSets,
            completedOn = todayProvider.today().takeIf { completedSets > 0 },
        )
    }
}
