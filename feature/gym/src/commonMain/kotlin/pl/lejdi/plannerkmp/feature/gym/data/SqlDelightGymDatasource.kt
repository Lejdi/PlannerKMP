package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
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
import pl.lejdi.plannerkmp.feature.gym.domain.GymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymExerciseDraft

internal class SqlDelightGymDatasource(
    private val queries: GymExerciseEntityQueries,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val logger: Logger,
) : GymDatasource {

    /**
     * SQLDelight re-runs the query and re-emits whenever `gymExerciseEntity` changes, so callers
     * never have to ask again after a write.
     *
     * The chain ends in `flowOn(dispatchers.io)`: `mapToList(io)` confines only the query
     * execution, leaving every operator after it — including turning the whole table into domain
     * objects on every emission — running in the collector's context, which is a `viewModelScope`.
     */
    override fun observeExercises(): Flow<AppResult<List<GymExercise>>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { entities -> entities.map { it.toDomain() } }
            .asAppResult(logger, "observe gym exercises")
            .flowOn(dispatchers.io)

    override fun observeExercise(id: Long): Flow<AppResult<GymExercise?>> =
        queries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { entity -> entity?.toDomain() }
            .asAppResult(logger, "observe gym exercise $id")
            .flowOn(dispatchers.io)

    /**
     * No `checkSingleRowAffected` here, unlike every other mutation: a single-row
     * `INSERT ... VALUES` either throws or inserts exactly that row, so there is no "matched
     * nothing" case to detect. Reporting one as
     * [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] is actively harmful — the edit screen
     * reads that error as "the row was deleted elsewhere" and throws away what the user had typed,
     * about a row that was never supposed to exist yet.
     */
    override suspend fun addExercise(draft: GymExerciseDraft): AppResult<Unit> =
        mutate("gym exercise insert") {
            queries.insert(
                name = draft.name,
                comment = draft.comment,
                dayOfWeek = draft.dayOfWeek,
                setsCount = draft.setsCount.toLong(),
                repsPerSet = draft.repsPerSet.toLong(),
                weight = draft.weight,
            ).await()
            Unit
        }

    override suspend fun updateDetails(id: Long, draft: GymExerciseDraft): AppResult<Unit> =
        mutate("gym exercise details update") {
            val rowsAffected = queries.updateDetails(
                name = draft.name,
                comment = draft.comment,
                dayOfWeek = draft.dayOfWeek,
                setsCount = draft.setsCount.toLong(),
                repsPerSet = draft.repsPerSet.toLong(),
                weight = draft.weight,
                id = id,
            ).await()
            checkSingleRowAffected(rowsAffected, "gym exercise details update")
        }

    override suspend fun updateWeight(id: Long, weight: Double?): AppResult<Unit> =
        mutate("gym exercise weight update") {
            val rowsAffected = queries.updateWeight(weight = weight, id = id).await()
            checkSingleRowAffected(rowsAffected, "gym exercise weight update")
        }

    override suspend fun updateCompletedSets(
        id: Long,
        completedSets: Int,
        completedOn: LocalDate?,
    ): AppResult<Unit> = mutate("gym exercise completion update") {
        val rowsAffected = queries.updateCompletedSets(
            completedSets = completedSets.toLong(),
            completedOn = completedOn,
            id = id,
        ).await()
        checkSingleRowAffected(rowsAffected, "gym exercise completion update")
    }

    override suspend fun deleteExercise(id: Long): AppResult<Unit> = mutate("gym exercise delete") {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "gym exercise delete")
    }

    // safeMutation, not safeQuery: a blocking driver call cannot be cancelled, so a timeout only
    // made the outcome unknowable — it reported failure over an insert that then committed.
    private suspend fun <T> mutate(operation: String, block: suspend () -> T): AppResult<T> =
        safeMutation(scope, dispatchers, logger, operation, block)
}

/**
 * The counts cross the boundary as `Long`, because the columns are plain `INTEGER` — see the note
 * in `GymExerciseEntity.sq` for why they are not declared `AS Int`.
 */
private fun GymExerciseEntity.toDomain(): GymExercise = GymExercise(
    id = id,
    name = name,
    comment = comment,
    dayOfWeek = dayOfWeek,
    setsCount = setsCount.toInt(),
    repsPerSet = repsPerSet.toInt(),
    weight = weight,
    completedSets = completedSets.toInt(),
    completedOn = completedOn,
)
