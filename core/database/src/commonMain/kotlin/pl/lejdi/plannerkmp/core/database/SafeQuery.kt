package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "Database"

/**
 * The deadline a *read* is given. Reads only — see [safeMutation].
 *
 * A read that never returns is worse than one that fails, and a caller that is told a read failed
 * loses nothing by asking again: the answer is the same and no state moved. Neither of those is
 * true of a write, which is why the number does not apply to one.
 */
val DEFAULT_READ_TIMEOUT: Duration = 5.seconds

/**
 * Runs [block] as a database **read** on [CoroutineDispatchers.io] and maps the outcome to
 * [AppResult] instead of letting SQLDelight exceptions escape to callers. A timeout is a hard
 * failure, same as any other exception — it is not rethrown as cancellation.
 *
 * Writes go through [safeMutation], which is this with [timeout] set to null. The split is not
 * cosmetic: a deadline the code cannot enforce turns a slow write into one whose outcome nobody
 * knows, and the caller is told it failed either way. See [safeMutation] for what that cost.
 *
 * [block] runs on a coroutine that is deliberately **not** a child of the caller's. That is what
 * makes the timeout able to do anything at all. The obvious spelling,
 * `withTimeout { withContext(io) { block() } }`, cannot resume the caller early: structured
 * concurrency makes `withContext` wait for its own child to finish before it returns, and a
 * SQLDelight query is an ordinary blocking call with no cancellation point — so the timeout fired,
 * changed nothing, and surfaced only once the query it was supposed to bound had completed on its
 * own. The old test passed because its block was a `delay`, which *is* cancellable, so the one case
 * the timeout could not handle was the only case the code ever met.
 *
 * Not a child of the caller's, but not parentless either. [scope] is the application scope, passed
 * in rather than conjured per call: `CoroutineScope(dispatchers.io)` here built a fresh, unparented,
 * unsupervised `Job` on every single query, outside structured concurrency entirely — nothing could
 * observe that work, nothing could cancel it, and at app teardown in-flight writes simply carried
 * on. A supervised app scope gives the same detachment from the *caller* (which is all the timeout
 * needs) while keeping the work inside a hierarchy that something owns. The scope is supervised, so
 * a failing query cannot take its neighbours down.
 *
 * The blocking call itself still cannot be killed: it runs to completion on the IO dispatcher and
 * its result is discarded. What changes is that the caller stops waiting for it — and that the
 * coroutine is now cancelled rather than abandoned, so a [block] that *does* have a suspension
 * point (a transaction mid-flight, anything awaiting) stops at the next one instead of running on.
 *
 * [logger] and [operation] are required rather than optional because this is the single funnel
 * through which a driver exception is turned into a value and the throwable stops propagating.
 * Logging here — instead of leaving it to each datasource to remember — is what makes it
 * structurally impossible to swallow one silently. [operation] names the query, since the driver's
 * own message ("UNIQUE constraint failed") rarely says which call site produced it.
 */
suspend fun <T> safeQuery(
    scope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
    logger: Logger,
    operation: String,
    timeout: Duration? = DEFAULT_READ_TIMEOUT,
    block: suspend () -> T,
): AppResult<T> {
    // Detached from the *caller* — see above — but parented by the app scope. Under that scope's
    // SupervisorJob this is a root coroutine, so an `async` nobody awaits holds its exception rather
    // than reporting it to the uncaught handler or cancelling its siblings.
    val work = scope.async(dispatchers.io) { block() }
    return try {
        AppResult.Success(if (timeout == null) work.await() else withTimeout(timeout) { work.await() })
    } catch (e: TimeoutCancellationException) {
        // Cancelled, not merely abandoned. `withTimeout` only cancels its own body — `work` is not
        // its child — so without this the coroutine stayed alive for as long as the block ran, and
        // for `runCleanup` that block is a whole transaction the caller has already been told
        // failed.
        work.cancel(e)
        logger.error(TAG, "$operation timed out after $timeout", e)
        AppResult.Failure(DomainError.Database("Database operation timed out", e))
    } catch (e: CancellationException) {
        work.cancel(e)
        throw e
    } catch (e: RowNotFoundException) {
        // A warning, not an error: the row being gone is a legitimate outcome of a race with another
        // writer, and the caller is expected to handle it rather than treat it as a malfunction.
        logger.warn(TAG, "$operation matched no rows", e)
        AppResult.Failure(DomainError.NotFound(e.message ?: "Row not found", e))
    } catch (e: Exception) {
        logger.error(TAG, "$operation failed", e)
        AppResult.Failure(DomainError.Database(e.message ?: "Unknown database error", e))
    }
}

/**
 * Runs a **write** — an insert, an update, a delete, or a transaction of them — and maps the
 * outcome to [AppResult]. Everything [safeQuery] does, minus the deadline, deliberately.
 *
 * A wall-clock deadline on a write is not a safety net here; it is the one thing that makes a
 * write's outcome unknowable. [safeQuery] documents that the blocking call cannot be killed: it
 * "runs to completion on the IO dispatcher and its result is discarded". So on timeout the caller
 * was handed `DomainError.Database("Database operation timed out")` — a definite failure — while
 * the insert it describes went on to commit a moment later. The screen then showed "save failed"
 * over a task that had in fact been saved, and the obvious thing for the user to do about that,
 * pressing Save again, wrote a second row. Neither the ViewModels' re-entry guards nor the disabled
 * buttons can help, because by then the first write has already reported back.
 *
 * Without the deadline the outcome is determinate again, and that is a property of SQLite rather
 * than of this function: a single statement is atomic and a transaction commits or rolls back, so
 * a write that throws did not happen and a write that returns did. The exception becomes a
 * `DomainError`, as always, and the caller can act on it.
 *
 * What is given up is the bound itself. That bound was never really this layer's to enforce: both
 * drivers already have their own busy timeout and surface a locked database as an exception, which
 * arrives here as an ordinary failure. An indefinite hang would need a write that neither commits
 * nor errors, which SQLite does not do.
 */
suspend fun <T> safeMutation(
    scope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
    logger: Logger,
    operation: String,
    block: suspend () -> T,
): AppResult<T> = safeQuery(scope, dispatchers, logger, operation, timeout = null, block = block)

/**
 * The streaming counterpart to [safeQuery]: wraps each emission of a SQLDelight query flow in
 * [AppResult.Success] and turns a failure of the flow itself into a logged [AppResult.Failure].
 *
 * Note that a failed flow is *terminated* — `catch` cannot resume the upstream — so the emitted
 * failure is the last thing an observer sees until it resubscribes. That is what a screen's "retry"
 * has to do, and why it cannot just clear the message.
 */
fun <T> Flow<T>.asAppResult(logger: Logger, operation: String): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            logger.error(TAG, "$operation failed", throwable)
            emit(
                AppResult.Failure(
                    DomainError.Database(throwable.message ?: "Unknown database error", throwable),
                ),
            )
        }
