package pl.lejdi.plannerkmp.core.common

/**
 * Outcome of a datasource/network/cache operation, carried up through
 * usecases to the ViewModel layer instead of throwing.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}

sealed interface DomainError {
    val message: String
    val cause: Throwable?

    data class Network(override val message: String, override val cause: Throwable? = null) : DomainError
    data class Database(override val message: String, override val cause: Throwable? = null) : DomainError
    data class Unknown(override val message: String, override val cause: Throwable? = null) : DomainError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (DomainError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
