package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.datetime.DayOfWeek
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.validationFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymExerciseDraftTest {

    @Test
    fun aValidDraftIsBuilt() {
        val result = create()

        assertTrue(result is AppResult.Success)
        assertEquals("Bench press", result.data.name)
        assertNull(result.data.comment)
        assertEquals(DayOfWeek.MONDAY, result.data.dayOfWeek)
        assertEquals(4, result.data.setsCount)
        assertEquals(8, result.data.repsPerSet)
        assertEquals(60.0, result.data.weight)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val result = create(name = "  Squat  ", comment = "  slow eccentric  ")

        assertTrue(result is AppResult.Success)
        assertEquals("Squat", result.data.name)
        assertEquals("slow eccentric", result.data.comment)
    }

    @Test
    fun aBlankCommentBecomesNull() {
        val result = create(comment = "   ")

        assertTrue(result is AppResult.Success)
        assertNull(result.data.comment)
    }

    @Test
    fun aBlankNameIsRejected() = assertInvalid(create(name = "  "), GymField.Name)

    @Test
    fun anUnparseableSeriesCountIsRejected() = assertInvalid(create(setsCount = null), GymField.Sets)

    @Test
    fun aSeriesCountBelowOneIsRejected() = assertInvalid(create(setsCount = 0), GymField.Sets)

    @Test
    fun aSeriesCountAboveTheMaximumIsRejected() =
        assertInvalid(create(setsCount = GymExerciseDraft.MAX_SETS + 1), GymField.Sets)

    @Test
    fun theSeriesCountBoundsAreAccepted() {
        assertTrue(create(setsCount = 1) is AppResult.Success)
        assertTrue(create(setsCount = GymExerciseDraft.MAX_SETS) is AppResult.Success)
    }

    @Test
    fun anUnparseableRepCountIsRejected() = assertInvalid(create(repsPerSet = null), GymField.Reps)

    @Test
    fun aRepCountBelowOneIsRejected() = assertInvalid(create(repsPerSet = 0), GymField.Reps)

    @Test
    fun aRepCountAboveTheMaximumIsRejected() =
        assertInvalid(create(repsPerSet = GymExerciseDraft.MAX_REPS + 1), GymField.Reps)

    /** No weight is how a bodyweight exercise is expressed, not a mistake. */
    @Test
    fun aNullWeightIsAccepted() {
        val result = create(weight = null)

        assertTrue(result is AppResult.Success)
        assertNull(result.data.weight)
    }

    @Test
    fun aZeroWeightIsAccepted() {
        assertTrue(create(weight = 0.0) is AppResult.Success)
    }

    @Test
    fun aNegativeWeightIsRejected() = assertInvalid(create(weight = -0.5), GymField.Weight)

    @Test
    fun aWeightAboveTheMaximumIsRejected() =
        assertInvalid(create(weight = GymExerciseDraft.MAX_WEIGHT + 0.1), GymField.Weight)

    /** NaN and the infinities are rejected here rather than becoming a row nothing can render. */
    @Test
    fun aNonFiniteWeightIsRejected() {
        assertInvalid(create(weight = Double.NaN), GymField.Weight)
        assertInvalid(create(weight = Double.POSITIVE_INFINITY), GymField.Weight)
    }

    /** The point of a Validation carrying a set: one submit reports every bad input. */
    @Test
    fun everyOffendingFieldIsNamedAtOnce() {
        val result = create(name = "", setsCount = 0, repsPerSet = null, weight = -1.0)

        assertTrue(result is AppResult.Failure)
        assertEquals(
            setOf(GymField.Name, GymField.Sets, GymField.Reps, GymField.Weight),
            result.error.validationFields<GymField>(),
        )
    }

    private fun create(
        name: String = "Bench press",
        comment: String? = null,
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        setsCount: Int? = 4,
        repsPerSet: Int? = 8,
        weight: Double? = 60.0,
    ) = GymExerciseDraft.create(
        name = name,
        comment = comment,
        dayOfWeek = dayOfWeek,
        setsCount = setsCount,
        repsPerSet = repsPerSet,
        weight = weight,
    )

    private fun assertInvalid(result: AppResult<GymExerciseDraft>, field: GymField) {
        assertTrue(result is AppResult.Failure)
        assertEquals(setOf(field), result.error.validationFields<GymField>())
    }
}
