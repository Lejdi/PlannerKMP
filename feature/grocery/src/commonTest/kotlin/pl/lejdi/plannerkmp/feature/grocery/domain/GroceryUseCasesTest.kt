package pl.lejdi.plannerkmp.feature.grocery.domain

import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.grocery.data.FakeGroceryDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroceryUseCasesTest {

    @Test
    fun getGroceryItemsReturnsWhatTheDatasourceHas() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = GetGroceryItems(datasource).invoke(Unit)

        assertEquals(AppResult.Success(listOf(GroceryItem(id = 1, name = "Milk", description = null))), result)
    }

    @Test
    fun addGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource()

        val result = AddGrocery(datasource).invoke(GroceryItem(id = 0, name = "Bread", description = null))

        assertTrue(result is AppResult.Success)
        assertEquals(1, datasource.items.size)
    }

    @Test
    fun editGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = EditGrocery(datasource).invoke(GroceryItem(id = 1, name = "Oat milk", description = null))

        assertTrue(result is AppResult.Success)
        assertEquals("Oat milk", datasource.items.single().name)
    }

    @Test
    fun deleteGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = DeleteGrocery(datasource).invoke(1)

        assertTrue(result is AppResult.Success)
        assertTrue(datasource.items.isEmpty())
    }
}
