package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SafeQueryTest {

    private class TestDispatchers(override val io: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher get() = io
        override val default: CoroutineDispatcher get() = io
    }

    @Test
    fun safeQueryReturnsSuccessForCompletedBlock() = runTest {
        val result = safeQuery(TestDispatchers(StandardTestDispatcher(testScheduler))) { 42 }

        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun safeQueryMapsThrownExceptionToFailure() = runTest {
        val result = safeQuery<Int>(TestDispatchers(StandardTestDispatcher(testScheduler))) {
            throw IllegalStateException("boom")
        }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeQueryMapsTimeoutToFailure() = runTest {
        val result = safeQuery(
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            timeout = 10.milliseconds,
        ) {
            delay(1000.milliseconds)
            42
        }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeQueryRunsTheBlockOnTheIoDispatcher() = runTest {
        val io = StandardTestDispatcher(testScheduler)

        val result = safeQuery(TestDispatchers(io)) {
            currentCoroutineContext()[ContinuationInterceptor]
        }

        assertTrue(result is AppResult.Success)
        assertEquals(io, result.data)
    }
}
