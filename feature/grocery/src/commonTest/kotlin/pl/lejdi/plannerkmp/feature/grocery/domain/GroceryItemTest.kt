package pl.lejdi.plannerkmp.feature.grocery.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroceryItemTest {

    @Test
    fun aBlankNameIsRejectedByTheDomain() {
        val result = GroceryItemDraft.create(name = "  ", description = null)

        assertTrue(result is AppResult.Failure)
        val error = result.error
        assertTrue(error is DomainError.Validation)
        assertEquals(setOf(GroceryField.Name), error.fields)
    }

    @Test
    fun nameAndDescriptionAreTrimmedAndBlankDescriptionBecomesNull() {
        val result = GroceryItemDraft.create(name = "  Milk ", description = "   ")

        assertTrue(result is AppResult.Success)
        assertEquals("Milk", result.data.name)
        assertNull(result.data.description)
    }

    @Test
    fun withIdTurnsADraftIntoAStoredItem() {
        val result = GroceryItemDraft.create(name = "Milk", description = "Oat")
        assertTrue(result is AppResult.Success)

        val item = result.data.withId(7)

        assertEquals(GroceryItem(id = 7, name = "Milk", description = "Oat"), item)
    }
}
