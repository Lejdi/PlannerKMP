package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.FlowUseCase
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.map

/**
 * The week's plan as it stands today: seven days, recomputed on every change to storage *and*
 * every time the date changes.
 *
 * This is where the daily reset lives, and it is why this feature has no cleanup job. A tick is
 * not stored as a boolean that something has to clear overnight — it is a count plus the date it
 * was made on, and that date stops matching by itself.
 *
 * The [combine] with [TodayProvider.todayFlow] is not decoration. Reading the date off each
 * emission instead looks like it survives midnight and does not: a SQLDelight query flow re-emits
 * only when its table is written, so a phone left on this screen overnight crosses midnight with
 * nothing to trigger a recompute and goes on showing yesterday's ticks until the user happens to
 * write something. That is the exact bug the dashboard once had, and `:feature:routines` carries
 * the same note for the same reason.
 *
 * `flowOn(dispatchers.default)` rather than `io`: [combine] runs its transform in the *collector's*
 * context, which for every collector of this is a `viewModelScope`, and grouping the table into
 * seven days is CPU work over data already in memory rather than a query.
 */
class ObserveGymWeek(
    private val datasource: GymDatasource,
    private val todayProvider: TodayProvider,
    private val dispatchers: CoroutineDispatchers,
) : FlowUseCase<Unit, AppResult<List<GymDay>>> {

    override fun invoke(params: Unit): Flow<AppResult<List<GymDay>>> =
        combine(datasource.observeExercises(), todayProvider.todayFlow()) { result, today ->
            result.map { exercises -> buildWeek(exercises, today) }
        }.flowOn(dispatchers.default)

    /**
     * Seven days, always, in [DayOfWeek.entries] order — which is Monday first, as ISO has it.
     *
     * A weekday with nothing planned is an empty [GymDay] rather than an absent one: the pager has
     * a page per weekday, and a missing page would be a hole in the week.
     */
    private fun buildWeek(exercises: List<GymExercise>, today: LocalDate): List<GymDay> {
        val byWeekday = exercises.groupBy { it.dayOfWeek }
        return DayOfWeek.entries.map { weekday ->
            GymDay(
                dayOfWeek = weekday,
                exercises = byWeekday[weekday].orEmpty().map { it.asDayExercise(today) },
            )
        }
    }
}

/**
 * The derivation the whole feature turns on: a tick counts only on the date it was made.
 *
 * The `coerceIn` is not defensive noise. The form may shrink `setsCount` under a row that was
 * already ticked — and it deliberately does not touch the completion pair when it does, because
 * the two writes are separate — so "five of three series done" is a state storage can legitimately
 * hold and this is where it is resolved.
 */
private fun GymExercise.asDayExercise(today: LocalDate) = DayExercise(
    exercise = this,
    doneSets = if (completedOn == today) completedSets.coerceIn(0, setsCount) else 0,
)
