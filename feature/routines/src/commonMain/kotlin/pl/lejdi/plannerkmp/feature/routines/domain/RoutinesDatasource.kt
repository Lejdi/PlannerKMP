package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * The routines storage port: declared in `domain`, implemented in `data`, so the dependency runs
 * from the implementation towards the domain rather than the other way round.
 *
 * The two updates are separate on purpose. Both writers here act on a [Routine] read earlier — the
 * editor on the one it opened, the checkbox on the one the list drew — so a whole-row update from
 * either would push its stale copy of the other's columns: a rename un-ticking today, or a tick
 * reverting a rename, both reported by SQLite as success. `:feature:tasks` splits `editTask` from
 * `rescheduleTask` for exactly this reason.
 */
interface RoutinesDatasource {
    /** Emits the list, and again on every change to it. */
    fun observeRoutines(): Flow<AppResult<List<Routine>>>

    suspend fun addRoutine(draft: RoutineDraft): AppResult<Unit>

    /** Writes the name and description only; never touches [Routine.completedOn]. */
    suspend fun updateDetails(id: Long, draft: RoutineDraft): AppResult<Unit>

    /** Writes [Routine.completedOn] only; never touches the name or description. */
    suspend fun updateCompletedOn(id: Long, completedOn: LocalDate?): AppResult<Unit>

    suspend fun deleteRoutine(id: Long): AppResult<Unit>
}
