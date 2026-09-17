package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.flow.Flow

/**
 * A single domain operation that produces one result and is done.
 *
 * Lives in `core:common` rather than `core:mvi` so a feature's domain layer does not have to depend
 * on the presentation/ViewModel module to describe its own use cases.
 */
fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}

/**
 * A domain operation that produces a stream.
 *
 * Separate from [UseCase] because folding the two together forces `suspend fun invoke(): Flow<T>` —
 * a signature that advertises that obtaining the stream can suspend or be cancelled when building a
 * flow does neither. Collecting it suspends; handing it over does not.
 */
fun interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<R>
}

/** Drops the meaningless `Unit` from the call site of a use case that takes no parameters. */
suspend operator fun <R> UseCase<Unit, R>.invoke(): R = invoke(Unit)

/** Drops the meaningless `Unit` from the call site of a flow use case that takes no parameters. */
operator fun <R> FlowUseCase<Unit, R>.invoke(): Flow<R> = invoke(Unit)
