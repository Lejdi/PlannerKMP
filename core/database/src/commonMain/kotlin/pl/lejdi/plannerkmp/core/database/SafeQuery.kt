package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DomainError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [block] as a database read/write on [CoroutineDispatchers.io] and maps
 * the outcome to [AppResult] instead of letting SQLDelight exceptions escape to
 * callers. A timeout is a hard failure, same as any other exception — it is not
 * rethrown as cancellation.
 *
 * Every caller reaches this from `viewModelScope.launch`, i.e. from Main, so the
 * [withContext] hop is what keeps blocking SQLite work off the UI thread. It is
 * also what makes [withTimeout] able to do anything at all: a non-suspending
 * SQLDelight call offers no cancellation point of its own, so without the
 * dispatch the timeout could never resume the caller. The blocking call itself
 * still cannot be killed — it runs to completion on the IO dispatcher — but the
 * caller stops waiting and gets a failure.
 */
suspend fun <T> safeQuery(
    dispatchers: CoroutineDispatchers,
    timeout: Duration = 5.seconds,
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(withTimeout(timeout) { withContext(dispatchers.io) { block() } })
} catch (e: TimeoutCancellationException) {
    AppResult.Failure(DomainError.Database("Database operation timed out", e))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(DomainError.Database(e.message ?: "Unknown database error", e))
}
