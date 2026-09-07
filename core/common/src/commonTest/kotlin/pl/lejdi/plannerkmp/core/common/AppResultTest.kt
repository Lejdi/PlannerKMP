package pl.lejdi.plannerkmp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppResultTest {

    @Test
    fun mapTransformsSuccessData() {
        val result: AppResult<Int> = AppResult.Success(2)

        val mapped = result.map { it * 21 }

        assertEquals(AppResult.Success(42), mapped)
    }

    @Test
    fun mapPassesThroughFailureUnchanged() {
        val error = DomainError.Network("boom")
        val result: AppResult<Int> = AppResult.Failure(error)

        val mapped = result.map { it * 21 }

        assertEquals(AppResult.Failure(error), mapped)
    }

    @Test
    fun onSuccessRunsOnlyForSuccess() {
        var invoked = false
        val result: AppResult<Int> = AppResult.Success(1)

        result.onSuccess { invoked = true }

        assertTrue(invoked)
    }

    @Test
    fun onSuccessDoesNotRunForFailure() {
        var invoked = false
        val result: AppResult<Int> = AppResult.Failure(DomainError.Unknown("boom"))

        result.onSuccess { invoked = true }

        assertFalse(invoked)
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var invoked = false
        val result: AppResult<Int> = AppResult.Failure(DomainError.Database("boom"))

        result.onFailure { invoked = true }

        assertTrue(invoked)
    }
}
