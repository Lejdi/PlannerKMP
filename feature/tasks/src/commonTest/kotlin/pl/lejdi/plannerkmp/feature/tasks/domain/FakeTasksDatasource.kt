package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError

/** The writes [FakeTasksDatasource] can be told to fail, one at a time. */
enum class TasksWrite { Add, Edit, Reschedule, Delete, Cleanup }

/**
 * Backed by a [MutableStateFlow] so it behaves like the real datasource: a write re-emits to
 * whoever is observing, rather than waiting to be asked again.
 */
class FakeTasksDatasource(
    initialTasks: List<Task> = emptyList(),
) : TasksDatasource {

    private val state = MutableStateFlow(initialTasks)

    val tasks: List<Task> get() = state.value
    var nextId: Long = (initialTasks.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Failures queued per write, rather than one "fail the next call whatever it is" flag.
     *
     * That flag landed on whichever call happened to come next, which is how a test named for the
     * cleanup's *write* failing ended up exercising its read instead — the name and the behaviour
     * disagreed and nothing could catch it. It also only ever produced [DomainError.Database], so
     * every `NotFound`-versus-malfunction branch in the ViewModels was unreachable.
     */
    private val queuedFailures = mutableMapOf<TasksWrite, DomainError>()

    /** Makes the next call to [write] fail with [error]; consumed on use, so one call fails once. */
    fun failNext(write: TasksWrite, error: DomainError = DomainError.Database("fake failure")) {
        queuedFailures[write] = error
    }

    /**
     * Holds every write open until [releaseWrites].
     *
     * A re-entry guard can only be tested while a write is actually in flight; with writes that
     * complete instantly, the second tap always lands after the first has finished.
     */
    private var gate: CompletableDeferred<Unit>? = null

    var blockWrites: Boolean
        get() = gate != null
        set(value) {
            gate = if (value) CompletableDeferred() else null
        }

    fun releaseWrites() {
        gate?.complete(Unit)
        gate = null
    }

    private suspend fun awaitGate() {
        gate?.await()
    }

    /** When set at subscription time, the observe streams fail immediately instead of emitting. */
    var observeFailure: DomainError? = null

    /** When set at subscription time, [observeTask] fails immediately instead of emitting. */
    var observeTaskFailure: DomainError? = null

    private val streamError = MutableStateFlow<DomainError?>(null)

    /**
     * Fails the *live* streams, after everything they have already emitted.
     *
     * [observeFailure] can only model a stream that was broken before anyone looked. A real driver
     * dying mid-session emits rows and then fails, which is the only way to reach a screen's
     * `onError` with its form already seeded — a branch that was otherwise untestable.
     */
    fun failLiveStreams(error: DomainError = DomainError.Database("fake failure")) {
        streamError.value = error
    }

    var appliedCleanups: Int = 0
        private set
    var lastCleanupDeletes: List<Long> = emptyList()
        private set
    var lastCleanupUpdates: List<Task> = emptyList()
        private set

    override fun observeTasks(): Flow<AppResult<List<Task>>> {
        val failure = observeFailure
        return if (failure != null) {
            flowOf(AppResult.Failure(failure))
        } else {
            results { it }
        }
    }

    override fun observeTask(id: Long): Flow<AppResult<Task?>> {
        val failure = observeTaskFailure
        return if (failure != null) {
            flowOf(AppResult.Failure(failure))
        } else {
            results { tasks -> tasks.find { it.id == id } }
        }
    }

    /**
     * Emits successes until [failLiveStreams], then one failure, then completes.
     *
     * Completing is the part that matters: the real `asAppResult` is a `catch`, which cannot resume
     * its upstream, so a failed stream is terminated and only a fresh subscription recovers. A fake
     * that kept emitting after a failure would make every "retry re-subscribes" test vacuous.
     */
    private fun <T> results(select: (List<Task>) -> T): Flow<AppResult<T>> =
        combine(state, streamError) { tasks, error ->
            if (error != null) AppResult.Failure(error) else AppResult.Success(select(tasks))
        }.transformWhile { result ->
            emit(result)
            result is AppResult.Success
        }

    override suspend fun addTask(draft: TaskDraft): AppResult<Unit> {
        awaitGate()
        consumeFailure(TasksWrite.Add)?.let { return it }
        state.value = state.value + draft.withId(nextId++)
        return AppResult.Success(Unit)
    }

    override suspend fun editTask(task: Task): AppResult<Unit> {
        awaitGate()
        consumeFailure(TasksWrite.Edit)?.let { return it }
        if (state.value.none { it.id == task.id }) return notFound()
        state.value = state.value.map { if (it.id == task.id) task else it }
        return AppResult.Success(Unit)
    }

    /** Schedule columns only, exactly like the statement it stands in for. */
    override suspend fun rescheduleTask(task: Task): AppResult<Unit> {
        awaitGate()
        consumeFailure(TasksWrite.Reschedule)?.let { return it }
        if (state.value.none { it.id == task.id }) return notFound()
        state.value = state.value.map {
            if (it.id == task.id) it.copy(schedule = task.schedule) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> {
        awaitGate()
        consumeFailure(TasksWrite.Delete)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun runCleanup(plan: (List<Task>) -> CleanupPlan): AppResult<Unit> {
        awaitGate()
        consumeFailure(TasksWrite.Cleanup)?.let { return it }
        // Plan from the same snapshot the writes are applied to, like the real transaction.
        val snapshot = state.value
        val cleanup = plan(snapshot)
        appliedCleanups++
        lastCleanupDeletes = cleanup.deletedIds
        lastCleanupUpdates = cleanup.updatedTasks
        val schedulesById = cleanup.updatedTasks.associate { it.id to it.schedule }
        state.value = snapshot
            .filterNot { it.id in cleanup.deletedIds }
            .map { task -> schedulesById[task.id]?.let { task.copy(schedule = it) } ?: task }
        return AppResult.Success(Unit)
    }

    private fun consumeFailure(write: TasksWrite): AppResult<Nothing>? =
        queuedFailures.remove(write)?.let { AppResult.Failure(it) }

    // Mirrors checkSingleRowAffected: a mutation that matches no row is NotFound, not a malfunction.
    private fun <T> notFound(): AppResult<T> = AppResult.Failure(DomainError.NotFound("no such task"))
}

class FakeCleanupDateStore(
    initialDate: LocalDate? = null,
) : CleanupDateStore {

    var lastCleanupDate: LocalDate? = initialDate
    var failNextRead: Boolean = false
    var failNextWrite: Boolean = false

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> {
        if (failNextRead) {
            failNextRead = false
            return AppResult.Failure(DomainError.Database("fake failure"))
        }
        return AppResult.Success(lastCleanupDate)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> {
        if (failNextWrite) {
            failNextWrite = false
            return AppResult.Failure(DomainError.Database("fake failure"))
        }
        lastCleanupDate = date
        return AppResult.Success(Unit)
    }
}
