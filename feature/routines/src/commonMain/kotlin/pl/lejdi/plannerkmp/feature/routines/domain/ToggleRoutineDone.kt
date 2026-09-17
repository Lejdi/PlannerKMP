package pl.lejdi.plannerkmp.feature.routines.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.UseCase

/**
 * What it means for a routine to be done.
 *
 * A use case rather than a call straight to the port, because it holds a rule: done is
 * `completedOn == today`, and the two halves of that convention — the read in
 * [ObserveRoutinesForToday] and the write here — belong in the same layer, where a test can hold
 * them against each other. Without it the convention would be spelled out in a ViewModel, which is
 * where this app keeps no business rules at all.
 *
 * [TodayProvider.today], not `todayFlow()`: this is a one-shot read at the moment of a write, which
 * is exactly what the one-shot accessor is for. Nothing here renders or schedules against the date.
 */
class ToggleRoutineDone(
    private val datasource: RoutinesDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<ToggleRoutineDone.Params, AppResult<Unit>> {

    data class Params(val id: Long, val done: Boolean)

    override suspend fun invoke(params: Params): AppResult<Unit> =
        datasource.updateCompletedOn(
            id = params.id,
            completedOn = if (params.done) todayProvider.today() else null,
        )
}
