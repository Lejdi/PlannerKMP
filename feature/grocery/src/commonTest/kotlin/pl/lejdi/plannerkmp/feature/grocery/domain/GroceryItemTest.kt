package pl.lejdi.plannerkmp.feature.grocery.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GroceryItemTest {

    @Test
    fun copyWithChangedNameProducesDistinctEqualityAgainstOriginal() {
        val original = GroceryItem(id = 1, name = "Milk", description = null)

        val renamed = original.copy(name = "Oat milk")

        assertEquals("Oat milk", renamed.name)
        assertNotEquals(original, renamed)
    }

    @Test
    fun itemsWithSameFieldsAreEqual() {
        val a = GroceryItem(id = 1, name = "Bread", description = "Sourdough")
        val b = GroceryItem(id = 1, name = "Bread", description = "Sourdough")

        assertEquals(a, b)
    }
}
