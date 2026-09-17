package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.RecordingLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SafeQueryTest {

    private val logger = RecordingLogger()

    /**
     * Stands in for the application scope safeQuery is given in production.
     *
     * Deliberately a plain supervised scope rather than runTest's `backgroundScope`: that one
     * reports a failing child to the test framework, so every test whose block throws — which is
     * most of the interesting ones here — would fail on the exception safeQuery exists to convert
     * into a value. `AppCoroutineScope` is exactly this: a SupervisorJob and nothing else.
     * The dispatcher is supplied per call by `dispatchers.io`, so the scope's own does not matter.
     */
    private val appScope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun tearDown() {
        appScope.cancel()
    }

    private fun dispatchers(io: CoroutineDispatcher) = TestCoroutineDispatchers(io)

    @Test
    fun safeQueryReturnsSuccessForCompletedBlock() = runTest {
        val result = safeQuery(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "read",
        ) { 42 }

        assertEquals(AppResult.Success(42), result)
        assertTrue(logger.errors.isEmpty(), "a successful query must not log an error")
    }

    @Test
    fun safeQueryMapsThrownExceptionToFailure() = runTest {
        val result = safeQuery<Int>(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "read",
        ) {
            throw IllegalStateException("boom")
        }

        assertTrue(result is AppResult.Failure)
    }

    /**
     * The reason [safeQuery] takes a logger at all: this is the one place a driver exception stops
     * being a throwable and becomes a value, so if it is not logged here it is gone for good.
     */
    @Test
    fun safeQueryLogsTheCauseItSwallows() = runTest {
        val thrown = IllegalStateException("UNIQUE constraint failed")

        safeQuery<Int>(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "task insert",
        ) {
            throw thrown
        }

        val logged = logger.errors.single()
        val cause = logged.cause
        assertContains(logged.message, "task insert", message = "the log has to name the failing operation")
        // Not assertEquals(thrown, cause): coroutines recover stack traces by logging a *copy* of
        // the exception, so identity does not survive the dispatch. Type and message do, and they
        // are what makes the failure diagnosable.
        assertTrue(cause is IllegalStateException, "the throwable itself must reach the log")
        assertEquals(thrown.message, cause.message)
    }

    @Test
    fun safeQueryMapsTimeoutToFailure() = runTest {
        val result = safeQuery(
            scope = appScope,
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
            logger = logger,
            operation = "slow read",
            timeout = 10.milliseconds,
        ) {
            delay(1000.milliseconds)
            42
        }

        assertTrue(result is AppResult.Failure)
        assertContains(logger.errors.single().message, "timed out")
    }

    /**
     * The case the timeout exists for, and the one it used to miss entirely.
     *
     * `NonCancellable` is what a SQLDelight query is: a call with no cancellation point. With the
     * old `withTimeout { withContext(io) { … } }`, structured concurrency made `withContext` wait
     * for this block regardless of the timeout, so `safeQuery` could not return until the gate was
     * released — this test would hang rather than fail. The previous timeout test used a `delay`,
     * which is cancellable, so it only ever exercised the case that already worked.
     */
    @Test
    fun aTimeoutReleasesTheCallerEvenWhenTheBlockCannotBeCancelled() = runTest {
        val gate = CompletableDeferred<Unit>()

        val result = safeQuery(
            scope = appScope,
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
            logger = logger,
            operation = "stuck read",
            timeout = 10.milliseconds,
        ) {
            withContext(NonCancellable) { gate.await() }
            42
        }

        assertTrue(result is AppResult.Failure)
        assertContains(logger.errors.single().message, "timed out")

        // Let the abandoned work finish so the scheduler is not left with a parked coroutine.
        gate.complete(Unit)
        runCurrent()
    }

    /**
     * A write is not given a deadline, and the contrast is the whole point of the pair.
     *
     * The same slow block is a failure through [safeQuery] and a success through [safeMutation].
     * With a deadline, a write that takes too long was reported to the caller as
     * `DomainError.Database("Database operation timed out")` — a definite failure — while the
     * statement it names went on to commit, because [safeQuery] cannot cancel a blocking driver
     * call and says so. The screen then showed "save failed" over a row that had been saved, and
     * the obvious response to that, pressing Save again, wrote a second one.
     */
    @Test
    fun aMutationIsNotBoundedByTheReadDeadline() = runTest {
        val slow: suspend () -> Int = {
            delay(DEFAULT_READ_TIMEOUT * 2)
            42
        }

        val read = safeQuery(
            scope = appScope,
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
            logger = logger,
            operation = "slow read",
            block = slow,
        )
        assertTrue(read is AppResult.Failure, "a read past the deadline still fails")

        val write = safeMutation(
            scope = appScope,
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
            logger = logger,
            operation = "slow write",
            block = slow,
        )
        assertTrue(write is AppResult.Success, "a write must wait rather than be called failed")
        assertEquals(42, write.data)
    }

    /** Everything else safeQuery does still applies: an exception is still a logged failure. */
    @Test
    fun aMutationStillMapsAThrownExceptionToAFailure() = runTest {
        val result = safeMutation(
            scope = appScope,
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
            logger = logger,
            operation = "task insert",
        ) {
            throw RowNotFoundException("task insert")
        }

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound)
    }

    @Test
    fun safeQueryRunsTheBlockOnTheIoDispatcher() = runTest {
        val io = StandardTestDispatcher(testScheduler)

        val result = safeQuery(
            appScope,
            dispatchers(io),
            logger,
            "read",
        ) {
            currentCoroutineContext()[ContinuationInterceptor]
        }

        assertTrue(result is AppResult.Success)
        assertEquals(io, result.data)
    }

    /** A mutation that matched nothing is NotFound; anything else about it is a malfunction. */
    @Test
    fun aRowNotFoundExceptionBecomesNotFoundRatherThanADatabaseFailure() = runTest {
        val result = safeQuery<Unit>(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "task update",
        ) {
            checkSingleRowAffected(0, "task update")
        }

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound, "was ${result.error}")
        // A warning, not an error: a row going missing is a race, not a broken driver.
        assertTrue(logger.errors.isEmpty())
        assertContains(logger.warnings.single().message, "task update")
    }

    /**
     * The other half of the distinction the UI depends on: more than one row affected is the driver
     * misbehaving, and must not be reported as "this record was deleted".
     */
    @Test
    fun anUnexpectedRowCountBecomesADatabaseFailure() = runTest {
        val result = safeQuery<Unit>(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "task update",
        ) {
            checkSingleRowAffected(2, "task update")
        }

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.Database, "was ${result.error}")
    }

    @Test
    fun exactlyOneRowAffectedIsNotAFailure() = runTest {
        val result = safeQuery(
            appScope,
            dispatchers(StandardTestDispatcher(testScheduler)),
            logger,
            "task update",
        ) {
            checkSingleRowAffected(1, "task update")
            "done"
        }

        assertEquals(AppResult.Success("done"), result)
    }

    @Test
    fun asAppResultWrapsEveryEmission() = runTest {
        val results = flowOf(1, 2).asAppResult(logger, "observe").toList()

        assertEquals(listOf(AppResult.Success(1), AppResult.Success(2)), results)
        assertTrue(logger.errors.isEmpty())
    }

    /**
     * A stream that works and then breaks — what a dying driver actually does.
     *
     * The emissions before the failure have to survive, and the failure has to arrive as a value
     * rather than as a thrown exception, or every screen observing storage crashes instead of
     * showing a retry.
     */
    @Test
    fun asAppResultKeepsEarlierEmissionsAndTurnsAFailureIntoAValue() = runTest {
        val source = flow {
            emit(1)
            error("driver went away")
        }

        val results = source.asAppResult(logger, "observe tasks").toList()

        assertEquals(AppResult.Success(1), results.first())
        assertTrue(results.last() is AppResult.Failure)
        assertContains(logger.errors.single().message, "observe tasks")
    }

    /**
     * Cancellation is not a failure.
     *
     * Without the rethrow, every screen teardown would log an error and push a spurious "load
     * failed" into state on the way out.
     */
    @Test
    fun asAppResultDoesNotReportCancellationAsAFailure() = runTest {
        val results = flow {
            emit(1)
            throw CancellationException("collector went away")
        }.asAppResult(logger, "observe tasks")
            .catch { }
            .toList()

        assertEquals(listOf(AppResult.Success(1)), results)
        assertTrue(logger.errors.isEmpty(), "cancellation must not be logged as a database failure")
    }
}
