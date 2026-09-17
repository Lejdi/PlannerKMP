package pl.lejdi.plannerkmp.feature.routines.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.validationFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineDraftTest {

    @Test
    fun aValidRoutineIsBuilt() {
        val result = RoutineDraft.create(name = "Stretch", description = "Ten minutes")

        assertTrue(result is AppResult.Success)
        assertEquals("Stretch", result.data.name)
        assertEquals("Ten minutes", result.data.description)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val result = RoutineDraft.create(name = "  Stretch  ", description = "  Ten minutes  ")

        assertTrue(result is AppResult.Success)
        assertEquals("Stretch", result.data.name)
        assertEquals("Ten minutes", result.data.description)
    }

    @Test
    fun anEmptyDescriptionBecomesNull() {
        val result = RoutineDraft.create(name = "Stretch", description = "   ")

        assertTrue(result is AppResult.Success)
        assertNull(result.data.description)
    }

    @Test
    fun aNullDescriptionStaysNull() {
        val result = RoutineDraft.create(name = "Stretch", description = null)

        assertTrue(result is AppResult.Success)
        assertNull(result.data.description)
    }

    @Test
    fun aBlankNameIsRejectedAndNamesItsField() {
        val result = RoutineDraft.create(name = "   ", description = "Ten minutes")

        assertTrue(result is AppResult.Failure)
        assertEquals(setOf(RoutineField.Name), result.error.validationFields<RoutineField>())
    }

    @Test
    fun anEmptyNameIsRejected() {
        val result = RoutineDraft.create(name = "", description = null)

        assertTrue(result is AppResult.Failure)
        assertEquals(setOf(RoutineField.Name), result.error.validationFields<RoutineField>())
    }
}
