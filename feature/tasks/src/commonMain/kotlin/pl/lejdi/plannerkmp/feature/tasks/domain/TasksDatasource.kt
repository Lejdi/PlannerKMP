package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.flow.Flow
import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * The tasks storage port.
 *
 * Declared in `domain` and implemented in `data`: the domain layer owns the contract it needs, and
 * the SQLDelight implementation depends on the domain rather than the other way round.
 */
interface TasksDatasource {
    /** Emits the full task list, and again on every change to it. */
    fun observeTasks(): Flow<AppResult<List<Task>>>

    /**
     * Emits one task, and again on every change to it; null once it is gone.
     *
     * The edit screen reads through this rather than through a one-shot read, so that it holds a
     * live row rather than a snapshot taken when the user tapped. A one-shot read turns a save into
     * a read-modify-write over data that may already have moved — the daily cleanup rolls periodic
     * tasks forward while the app is running — and gives the screen no way to notice the row was
     * deleted underneath it.
     */
    fun observeTask(id: Long): Flow<AppResult<Task?>>

    suspend fun addTask(draft: TaskDraft): AppResult<Unit>

    /**
     * Writes the whole row, including the text the user typed.
     *
     * Only the edit form calls this, because only the edit form owns every field at once. The two
     * writers that move a schedule on the user's behalf use [rescheduleTask] instead.
     */
    suspend fun editTask(task: Task): AppResult<Unit>

    /**
     * Writes [task]'s schedule columns and nothing else.
     *
     * Separate from [editTask] because the callers differ in what they are entitled to change.
     * "Mark complete" and the daily cleanup both act on a [Task] read earlier and move only its
     * schedule; sending that stale object through [editTask] wrote its stale `name` and
     * `description` back over whatever the user had edited in the meantime, and reported success.
     */
    suspend fun rescheduleTask(task: Task): AppResult<Unit>

    suspend fun deleteTask(id: Long): AppResult<Unit>

    /**
     * Reads the tasks, asks [plan] what to change, and applies it — all in one transaction.
     *
     * [plan] is the pure domain policy ([planCleanup]); this method exists to give it a consistent
     * read. Reading through a separate `getAllTasks()` first meant the plan was computed from rows
     * that could already have moved by the time it was applied: a task edited between the read and
     * the write was rolled forward from its old anchor, silently undoing the edit. Planning inside
     * the transaction makes the decision and the write see the same rows, and keeps the whole day's
     * changes atomic so a failure part-way cannot leave half of them applied.
     */
    suspend fun runCleanup(plan: (List<Task>) -> CleanupPlan): AppResult<Unit>
}
