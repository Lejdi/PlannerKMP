package pl.lejdi.plannerkmp.feature.routines.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.database.asAppResult
import pl.lejdi.plannerkmp.core.database.checkSingleRowAffected
import pl.lejdi.plannerkmp.core.database.safeMutation
import pl.lejdi.plannerkmp.feature.routines.domain.Routine
import pl.lejdi.plannerkmp.feature.routines.domain.RoutineDraft
import pl.lejdi.plannerkmp.feature.routines.domain.RoutinesDatasource

internal class SqlDelightRoutinesDatasource(
    private val queries: RoutineEntityQueries,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val logger: Logger,
) : RoutinesDatasource {

    /**
     * SQLDelight re-runs the query and re-emits whenever `routineEntity` changes, so callers never
     * have to ask again after a write.
     *
     * The chain ends in `flowOn(dispatchers.io)`: `mapToList(io)` confines only the query
     * execution, leaving every operator after it — including turning the whole table into domain
     * objects on every emission — running in the collector's context, which is a `viewModelScope`.
     */
    override fun observeRoutines(): Flow<AppResult<List<Routine>>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { entities -> entities.map { it.toDomain() } }
            .asAppResult(logger, "observe routines")
            .flowOn(dispatchers.io)

    /**
     * No `checkSingleRowAffected` here, unlike every other mutation: a single-row
     * `INSERT ... VALUES` either throws or inserts exactly that row, so there is no "matched
     * nothing" case to detect. Reporting one as
     * [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] is actively harmful — the ViewModel
     * reads that error as "the row was deleted elsewhere" and throws away what the user had typed,
     * about a row that was never supposed to exist yet.
     */
    override suspend fun addRoutine(draft: RoutineDraft): AppResult<Unit> = mutate("routine insert") {
        queries.insert(name = draft.name, description = draft.description).await()
        Unit
    }

    override suspend fun updateDetails(id: Long, draft: RoutineDraft): AppResult<Unit> =
        mutate("routine details update") {
            val rowsAffected = queries.updateDetails(
                name = draft.name,
                description = draft.description,
                id = id,
            ).await()
            checkSingleRowAffected(rowsAffected, "routine details update")
        }

    override suspend fun updateCompletedOn(id: Long, completedOn: LocalDate?): AppResult<Unit> =
        mutate("routine completion update") {
            val rowsAffected = queries.updateCompletedOn(completedOn = completedOn, id = id).await()
            checkSingleRowAffected(rowsAffected, "routine completion update")
        }

    override suspend fun deleteRoutine(id: Long): AppResult<Unit> = mutate("routine delete") {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "routine delete")
    }

    // safeMutation, not safeQuery: a blocking driver call cannot be cancelled, so a timeout only
    // made the outcome unknowable — it reported failure over an insert that then committed.
    private suspend fun <T> mutate(operation: String, block: suspend () -> T): AppResult<T> =
        safeMutation(scope, dispatchers, logger, operation, block)
}

private fun RoutineEntity.toDomain(): Routine = Routine(
    id = id,
    name = name,
    description = description,
    completedOn = completedOn,
)
