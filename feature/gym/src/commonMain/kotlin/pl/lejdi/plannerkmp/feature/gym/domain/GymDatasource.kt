package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * The gym storage port: declared in `domain`, implemented in `data`, so the dependency runs from
 * the implementation towards the domain rather than the other way round.
 *
 * **The three updates are separate on purpose.** Three writers touch an exercise and none of them
 * has read what the others wrote — the form owns the text and the numbers, the inline editor on
 * the list row owns the weight, and the checkboxes own the completion pair. Each works from a copy
 * read at a different moment, so a whole-row update from any of them would push its stale version
 * of the others' columns: a rename un-ticking the day, a tick reverting a weight the user had just
 * typed. SQLite reports both as success. `:feature:tasks` splits `editTask` from `rescheduleTask`
 * and `:feature:routines` splits `updateDetails` from `updateCompletedOn` for exactly this reason.
 */
interface GymDatasource {
    /** Emits every exercise, and again on every change to them. */
    fun observeExercises(): Flow<AppResult<List<GymExercise>>>

    /**
     * Emits one exercise, and again on every change to it; null once it is gone.
     *
     * The edit screen reads through this rather than through a one-shot read, so that it holds a
     * live row rather than a snapshot taken when the user long-pressed. A one-shot read turns a
     * save into a read-modify-write over data that may already have moved, and gives the screen no
     * way to notice the row was deleted underneath it.
     */
    fun observeExercise(id: Long): Flow<AppResult<GymExercise?>>

    suspend fun addExercise(draft: GymExerciseDraft): AppResult<Unit>

    /** Writes every column the form owns; never touches the completion pair. */
    suspend fun updateDetails(id: Long, draft: GymExerciseDraft): AppResult<Unit>

    /** Writes the weight only, for the inline editor on the list row. */
    suspend fun updateWeight(id: Long, weight: Double?): AppResult<Unit>

    /**
     * Writes the completion pair only, for the checkboxes.
     *
     * [completedOn] is the date [completedSets] were ticked on, and `null` when the count is zero —
     * a row with nothing done carries no date to compare against. See [ToggleExerciseSet], which
     * owns that convention.
     */
    suspend fun updateCompletedSets(
        id: Long,
        completedSets: Int,
        completedOn: LocalDate?,
    ): AppResult<Unit>

    suspend fun deleteExercise(id: Long): AppResult<Unit>
}
