package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError

/** The writes [FakeRoutinesDatasource] can be told to fail, one at a time. */
enum class RoutinesWrite { Add, UpdateDetails, UpdateCompletedOn, Delete }

/** State-flow backed, so a write re-emits to observers exactly as SQLDelight's query flows do. */
class FakeRoutinesDatasource(
    initialRoutines: List<Routine> = emptyList(),
) : RoutinesDatasource {

    private val state = MutableStateFlow(initialRoutines)

    val routines: List<Routine> get() = state.value
    var nextId: Long = (initialRoutines.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Failures queued per write, and carrying their own [DomainError].
     *
     * A single "fail the next call" flag could only ever produce [DomainError.Database], so the
     * `NotFound` branches the ViewModel has for a row deleted elsewhere would be unreachable
     * except by actually removing the row — which changes the state under test.
     */
    private val queuedFailures = mutableMapOf<RoutinesWrite, DomainError>()

    /** Makes the next call to [write] fail with [error]; consumed on use, so one call fails once. */
    fun failNext(write: RoutinesWrite, error: DomainError = DomainError.Database("fake failure")) {
        queuedFailures[write] = error
    }

    /**
     * Holds every write open until [releaseWrites].
     *
     * A re-entry guard can only be tested while a write is actually in flight; with writes that
     * complete instantly, a second tap always arrives after the first has finished and the guard
     * is never the thing under test.
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

    /** When set at subscription time, [observeRoutines] fails immediately instead of emitting. */
    var observeFailure: DomainError? = null

    private val streamError = MutableStateFlow<DomainError?>(null)

    /** Fails the live stream after everything it has already emitted, as a dying driver does. */
    fun failLiveStream(error: DomainError = DomainError.Database("fake failure")) {
        streamError.value = error
    }

    override fun observeRoutines(): Flow<AppResult<List<Routine>>> {
        val failure = observeFailure
        return if (failure != null) {
            flowOf(AppResult.Failure(failure))
        } else {
            // Terminates after a failure, because the real `asAppResult` is a `catch` and cannot
            // resume its upstream — which is what makes "retry re-subscribes" mean anything.
            combine(state, streamError) { routines, error ->
                if (error != null) AppResult.Failure(error) else AppResult.Success(routines)
            }.transformWhile { result ->
                emit(result)
                result is AppResult.Success
            }
        }
    }

    override suspend fun addRoutine(draft: RoutineDraft): AppResult<Unit> {
        awaitGate()
        consumeFailure(RoutinesWrite.Add)?.let { return it }
        state.value = state.value + Routine(
            id = nextId++,
            name = draft.name,
            description = draft.description,
            completedOn = null,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun updateDetails(id: Long, draft: RoutineDraft): AppResult<Unit> {
        awaitGate()
        consumeFailure(RoutinesWrite.UpdateDetails)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        // completedOn deliberately carried over, exactly as the SQL statement leaves it alone.
        state.value = state.value.map {
            if (it.id == id) it.copy(name = draft.name, description = draft.description) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateCompletedOn(id: Long, completedOn: LocalDate?): AppResult<Unit> {
        awaitGate()
        consumeFailure(RoutinesWrite.UpdateCompletedOn)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.map { if (it.id == id) it.copy(completedOn = completedOn) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteRoutine(id: Long): AppResult<Unit> {
        awaitGate()
        consumeFailure(RoutinesWrite.Delete)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun consumeFailure(write: RoutinesWrite): AppResult<Nothing>? =
        queuedFailures.remove(write)?.let { AppResult.Failure(it) }

    // Mirrors checkSingleRowAffected: a mutation matching no row is NotFound, not a malfunction.
    private fun <T> notFound(): AppResult<T> = AppResult.Failure(DomainError.NotFound("no such routine"))
}
