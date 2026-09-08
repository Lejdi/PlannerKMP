package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SafeQueryTest {

    @Test
    fun safeQueryReturnsSuccessForCompletedBlock() = runTest {
        val result = safeQuery { 42 }

        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun safeQueryMapsThrownExceptionToFailure() = runTest {
        val result = safeQuery<Int> { throw IllegalStateException("boom") }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeQueryMapsTimeoutToFailure() = runTest {
        val result = safeQuery(timeout = 10.milliseconds) {
            delay(1000.milliseconds)
            42
        }

        assertTrue(result is AppResult.Failure)
    }
}
