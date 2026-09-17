package pl.lejdi.plannerkmp.core.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun onFailureDoesNotRunForSuccess() {
        var invoked = false
        val result: AppResult<Int> = AppResult.Success(1)

        result.onFailure { invoked = true }

        assertFalse(invoked)
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var invoked = false
        val result: AppResult<Int> = AppResult.Failure(DomainError.Database("boom"))

        result.onFailure { invoked = true }

        assertTrue(invoked)
    }

    @Test
    fun flatMapChainsSuccessIntoTheNextOperation() {
        val result: AppResult<Int> = AppResult.Success(2)

        val chained = result.flatMap { AppResult.Success(it * 21) }

        assertEquals(AppResult.Success(42), chained)
    }

    @Test
    fun flatMapShortCircuitsOnFailureWithoutRunningTransform() {
        val error = DomainError.Database("boom")
        val result: AppResult<Int> = AppResult.Failure(error)
        var invoked = false

        val chained = result.flatMap {
            invoked = true
            AppResult.Success(it)
        }

        assertFalse(invoked)
        assertEquals(AppResult.Failure(error), chained)
    }

    @Test
    fun flatMapPropagatesAFailureRaisedByTheTransform() {
        val error = DomainError.Validation(TestField.Name)
        val result: AppResult<Int> = AppResult.Success(1)

        val chained: AppResult<Int> = result.flatMap { AppResult.Failure(error) }

        assertEquals(AppResult.Failure(error), chained)
    }

    @Test
    fun foldSelectsTheBranchMatchingTheOutcome() {
        val success: AppResult<Int> = AppResult.Success(2)
        val failure: AppResult<Int> = AppResult.Failure(DomainError.Network("offline"))

        assertEquals("ok:2", success.fold(onSuccess = { "ok:$it" }, onFailure = { "err:${it.message}" }))
        assertEquals("err:offline", failure.fold(onSuccess = { "ok:$it" }, onFailure = { "err:${it.message}" }))
    }

    @Test
    fun validationErrorCarriesTheFieldsItBelongsTo() {
        val error = DomainError.Validation(setOf(TestField.Name, TestField.DaysInterval))

        assertEquals(setOf(TestField.Name, TestField.DaysInterval), error.fields)
        // The message is for the log; the UI resolves the fields to its own wording.
        assertContains(error.message, "DaysInterval")
    }

    @Test
    fun aValidationErrorMustNameAtLeastOneField() {
        assertFailsWith<IllegalArgumentException> { DomainError.Validation(emptySet()) }
    }

    @Test
    fun validationFieldsReturnsEveryFieldOfTheAskedForType() {
        val error: DomainError = DomainError.Validation(setOf(TestField.Name, TestField.DaysInterval))

        assertEquals(setOf(TestField.Name, TestField.DaysInterval), error.validationFields<TestField>())
    }

    /**
     * A field belonging to some other feature is dropped rather than crashing the screen that asks.
     *
     * The caller has no control to attach it to, so its correct response is the screen-level
     * message it already shows when the set comes back empty.
     */
    @Test
    fun validationFieldsDropsFieldsBelongingToAnotherFeature() {
        val error: DomainError = DomainError.Validation(setOf(OtherField.Quantity))

        assertEquals(emptySet(), error.validationFields<TestField>())
    }

    @Test
    fun validationFieldsIsEmptyForAnErrorThatIsNotAboutInput() {
        val error: DomainError = DomainError.Network("offline")

        assertEquals(emptySet(), error.validationFields<TestField>())
    }

    /**
     * A feature's own field type. The point of [ValidationField] being an interface is that
     * `core:common` never sees this enum, so a test here has to declare one just like a feature
     * does.
     */
    private enum class TestField : ValidationField { Name, DaysInterval }

    /** A second feature's fields, to prove one screen never sees another's. */
    private enum class OtherField : ValidationField { Quantity }
}
