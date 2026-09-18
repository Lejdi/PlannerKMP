package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError

/** The writes [FakeGymDatasource] can be told to fail, one at a time. */
enum class GymWrite { Add, UpdateDetails, UpdateWeight, UpdateCompletedSets, Delete }

/** State-flow backed, so a write re-emits to observers exactly as SQLDelight's query flows do. */
class FakeGymDatasource(
    initialExercises: List<GymExercise> = emptyList(),
) : GymDatasource {

    private val state = MutableStateFlow(initialExercises)

    val exercises: List<GymExercise> get() = state.value
    var nextId: Long = (initialExercises.maxOfOrNull { it.id } ?: 0L) + 1

    /** Every write this fake has been asked to perform, in order, for a test to assert on. */
    val writes = mutableListOf<RecordedWrite>()

    /** What a write was asked to do, kept because several tests are about the arguments, not the state. */
    sealed interface RecordedWrite {
        data class Add(val draft: GymExerciseDraft) : RecordedWrite
        data class UpdateDetails(val id: Long, val draft: GymExerciseDraft) : RecordedWrite
        data class UpdateWeight(val id: Long, val weight: Double?) : RecordedWrite
        data class UpdateCompletedSets(
            val id: Long,
            val completedSets: Int,
            val completedOn: LocalDate?,
        ) : RecordedWrite
        data class Delete(val id: Long) : RecordedWrite
    }

    /**
     * Failures queued per write, and carrying their own [DomainError].
     *
     * A single "fail the next call" flag could only ever produce [DomainError.Database], so the
     * `NotFound` branches a ViewModel has for a row deleted elsewhere would be unreachable except
     * by actually removing the row — which changes the state under test.
     */
    private val queuedFailures = mutableMapOf<GymWrite, DomainError>()

    /** Makes the next call to [write] fail with [error]; consumed on use, so one call fails once. */
    fun failNext(write: GymWrite, error: DomainError = DomainError.Database("fake failure")) {
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

    /** When set at subscription time, the observe flows fail immediately instead of emitting. */
    var observeFailure: DomainError? = null

    private val streamError = MutableStateFlow<DomainError?>(null)

    /** Fails the live stream after everything it has already emitted, as a dying driver does. */
    fun failLiveStream(error: DomainError = DomainError.Database("fake failure")) {
        streamError.value = error
    }

    override fun observeExercises(): Flow<AppResult<List<GymExercise>>> = observe { it }

    override fun observeExercise(id: Long): Flow<AppResult<GymExercise?>> =
        observe { exercises -> exercises.find { it.id == id } }

    private fun <T> observe(select: (List<GymExercise>) -> T): Flow<AppResult<T>> {
        val failure = observeFailure
        return if (failure != null) {
            flowOf(AppResult.Failure(failure))
        } else {
            // Terminates after a failure, because the real `asAppResult` is a `catch` and cannot
            // resume its upstream — which is what makes "retry re-subscribes" mean anything.
            combine(state, streamError) { exercises, error ->
                if (error != null) AppResult.Failure(error) else AppResult.Success(select(exercises))
            }.transformWhile { result ->
                emit(result)
                result is AppResult.Success
            }
        }
    }

    override suspend fun addExercise(draft: GymExerciseDraft): AppResult<Unit> {
        awaitGate()
        writes += RecordedWrite.Add(draft)
        consumeFailure(GymWrite.Add)?.let { return it }
        state.value = state.value + GymExercise(
            id = nextId++,
            name = draft.name,
            comment = draft.comment,
            dayOfWeek = draft.dayOfWeek,
            setsCount = draft.setsCount,
            repsPerSet = draft.repsPerSet,
            weight = draft.weight,
            completedSets = 0,
            completedOn = null,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun updateDetails(id: Long, draft: GymExerciseDraft): AppResult<Unit> {
        awaitGate()
        writes += RecordedWrite.UpdateDetails(id, draft)
        consumeFailure(GymWrite.UpdateDetails)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        // The completion pair is deliberately carried over, exactly as the SQL leaves it alone.
        state.value = state.value.map {
            if (it.id == id) {
                it.copy(
                    name = draft.name,
                    comment = draft.comment,
                    dayOfWeek = draft.dayOfWeek,
                    setsCount = draft.setsCount,
                    repsPerSet = draft.repsPerSet,
                    weight = draft.weight,
                )
            } else {
                it
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateWeight(id: Long, weight: Double?): AppResult<Unit> {
        awaitGate()
        writes += RecordedWrite.UpdateWeight(id, weight)
        consumeFailure(GymWrite.UpdateWeight)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.map { if (it.id == id) it.copy(weight = weight) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun updateCompletedSets(
        id: Long,
        completedSets: Int,
        completedOn: LocalDate?,
    ): AppResult<Unit> {
        awaitGate()
        writes += RecordedWrite.UpdateCompletedSets(id, completedSets, completedOn)
        consumeFailure(GymWrite.UpdateCompletedSets)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.map {
            if (it.id == id) it.copy(completedSets = completedSets, completedOn = completedOn) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteExercise(id: Long): AppResult<Unit> {
        awaitGate()
        writes += RecordedWrite.Delete(id)
        consumeFailure(GymWrite.Delete)?.let { return it }
        if (state.value.none { it.id == id }) return notFound()
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun consumeFailure(write: GymWrite): AppResult<Nothing>? =
        queuedFailures.remove(write)?.let { AppResult.Failure(it) }

    // Mirrors checkSingleRowAffected: a mutation matching no row is NotFound, not a malfunction.
    private fun <T> notFound(): AppResult<T> =
        AppResult.Failure(DomainError.NotFound("no such exercise"))
}
