package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.FlowUseCase
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.map

/**
 * The routine list as it stands today, recomputed on every change to storage *and* every time the
 * date changes.
 *
 * This is where the daily reset lives, and it is why there is no cleanup job in this feature: a
 * tick is not stored as a boolean that something has to clear overnight, it is derived from a date
 * that stops matching by itself.
 *
 * The [combine] with [TodayProvider.todayFlow] is not decoration. Reading the date off each
 * emission instead looks like it survives midnight and does not: a SQLDelight query flow re-emits
 * only when its table is written, so a phone left on this screen overnight crosses midnight with
 * nothing to trigger a recompute and goes on showing yesterday's ticks until the user happens to
 * write something. That is the exact bug the dashboard already had.
 *
 * `flowOn(default)` rather than `io`: [combine] runs its transform in the *collector's* context,
 * which for every collector of this is a `viewModelScope`, and this is CPU work over data already
 * in memory rather than a query.
 */
class ObserveRoutinesForToday(
    private val datasource: RoutinesDatasource,
    private val todayProvider: TodayProvider,
    private val dispatchers: CoroutineDispatchers,
) : FlowUseCase<Unit, AppResult<List<TodayRoutine>>> {

    override fun invoke(params: Unit): Flow<AppResult<List<TodayRoutine>>> =
        combine(datasource.observeRoutines(), todayProvider.todayFlow()) { result, today ->
            result.map { routines ->
                routines.map { TodayRoutine(routine = it, isDoneToday = it.completedOn == today) }
            }
        }.flowOn(dispatchers.default)
}
