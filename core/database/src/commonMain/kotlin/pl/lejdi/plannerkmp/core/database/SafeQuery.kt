package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [block] as a database read/write and maps the outcome to [AppResult]
 * instead of letting SQLDelight exceptions escape to callers. A timeout is a
 * hard failure, same as any other exception — it is not rethrown as
 * cancellation.
 */
suspend fun <T> safeQuery(
    timeout: Duration = 5.seconds,
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(withTimeout(timeout) { block() })
} catch (e: TimeoutCancellationException) {
    AppResult.Failure(DomainError.Database("Database operation timed out", e))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(DomainError.Database(e.message ?: "Unknown database error", e))
}
