package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
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
     * SQLDelight re-runs the query and re-emits whenever `gymExerciseEntity` *or* `gymExerciseDay`
     * changes, so callers never have to ask again after a write.
     *
     * The chain ends in `flowOn(dispatchers.io)`: `mapToList(io)` confines only the query
     * execution, leaving every operator after it — including folding the rows into domain objects
     * on every emission — running in the collector's context, which is a `viewModelScope`.
     */
    override fun observeExercises(): Flow<AppResult<List<GymExercise>>> =
        queries.selectAll(::ExerciseDayRow)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.toExercises() }
            .asAppResult(logger, "observe gym exercises")
            .flowOn(dispatchers.io)

    /**
     * A list rather than `mapToOneOrNull`, because the join returns one row per weekday: an
     * exercise on three days is three rows, and no rows at all once it is gone.
     */
    override fun observeExercise(id: Long): Flow<AppResult<GymExercise?>> =
        queries.selectById(id, ::ExerciseDayRow)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.toExercises().singleOrNull() }
            .asAppResult(logger, "observe gym exercise $id")
            .flowOn(dispatchers.io)

    /**
     * No `checkSingleRowAffected` here, unlike every other mutation: a single-row
     * `INSERT ... VALUES` either throws or inserts exactly that row, so there is no "matched
     * nothing" case to detect. Reporting one as
     * [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] is actively harmful — the edit screen
     * reads that error as "the row was deleted elsewhere" and throws away what the user had typed,
     * about a row that was never supposed to exist yet.
     *
     * One transaction for the exercise and its days, so no observer can see an exercise with no
     * day — which the join would show as no exercise at all.
     */
    override suspend fun addExercise(draft: GymExerciseDraft): AppResult<Unit> =
        mutate("gym exercise insert") {
            queries.transaction {
                queries.insert(
                    name = draft.name,
                    comment = draft.comment,
                    setsCount = draft.setsCount.toLong(),
                    repsPerSet = draft.repsPerSet.toLong(),
                    weight = draft.weight,
                )
                insertDays(queries.lastInsertRowId().executeAsOne(), draft.days)
            }
        }

    /**
     * The row is checked before its days are touched: a row deleted elsewhere throws here, which
     * rolls the transaction back rather than leaving days behind for an exercise that no longer
     * exists.
     */
    override suspend fun updateDetails(id: Long, draft: GymExerciseDraft): AppResult<Unit> =
        mutate("gym exercise details update") {
            queries.transaction {
                val rowsAffected = queries.updateDetails(
                    name = draft.name,
                    comment = draft.comment,
                    setsCount = draft.setsCount.toLong(),
                    repsPerSet = draft.repsPerSet.toLong(),
                    weight = draft.weight,
                    id = id,
                ).value
                checkSingleRowAffected(rowsAffected, "gym exercise details update")
                queries.deleteDays(id)
                insertDays(id, draft.days)
            }
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

    /** The days go in the same transaction, since there is no foreign key to cascade them. */
    override suspend fun deleteExercise(id: Long): AppResult<Unit> = mutate("gym exercise delete") {
        queries.transaction {
            val rowsAffected = queries.deleteById(id).value
            checkSingleRowAffected(rowsAffected, "gym exercise delete")
            queries.deleteDays(id)
        }
    }

    private fun insertDays(exerciseId: Long, days: Set<DayOfWeek>) {
        days.forEach { day -> queries.insertDay(exerciseId = exerciseId, dayOfWeek = day) }
    }

    // safeMutation, not safeQuery: a blocking driver call cannot be cancelled, so a timeout only
    // made the outcome unknowable — it reported failure over an insert that then committed.
    private suspend fun <T> mutate(operation: String, block: suspend () -> T): AppResult<T> =
        safeMutation(scope, dispatchers, logger, operation, block)
}

/**
 * One row of the join: an exercise's own columns, plus one of the weekdays it is planned on.
 *
 * Built through the queries' mapper overloads so `selectAll` and `selectById`, whose generated
 * result types SQLDelight keeps distinct, fold through the same code. The counts cross the boundary
 * as `Long`, because the columns are plain `INTEGER` — see the note in `GymExerciseEntity.sq`.
 */
@Suppress("LongParameterList") // One parameter per selected column, in the order SQLDelight passes them.
private data class ExerciseDayRow(
    val id: Long,
    val name: String,
    val comment: String?,
    val setsCount: Long,
    val repsPerSet: Long,
    val weight: Double?,
    val completedSets: Long,
    val completedOn: LocalDate?,
    val dayOfWeek: DayOfWeek,
)

/**
 * Folds the join back into one exercise per id.
 *
 * Both queries order by id, so `groupBy` — which keeps first-seen order — returns the exercises in
 * the order they were added, exactly as the single-table query did before the days moved out.
 */
private fun List<ExerciseDayRow>.toExercises(): List<GymExercise> =
    groupBy { it.id }.values.map { rows ->
        val row = rows.first()
        GymExercise(
            id = row.id,
            name = row.name,
            comment = row.comment,
            days = rows.mapTo(mutableSetOf()) { it.dayOfWeek },
            setsCount = row.setsCount.toInt(),
            repsPerSet = row.repsPerSet.toInt(),
            weight = row.weight,
            completedSets = row.completedSets.toInt(),
            completedOn = row.completedOn,
        )
    }
