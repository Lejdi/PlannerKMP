package pl.lejdi.plannerkmp.feature.grocery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.lejdi.plannerkmp.feature.grocery.data.FakeGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryListViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeGroceryDatasource) = GroceryListViewModel(
        getGroceryItems = GetGroceryItems(datasource),
        addGrocery = AddGrocery(datasource),
        editGrocery = EditGrocery(datasource),
        deleteGrocery = DeleteGrocery(datasource),
    )

    @Test
    fun loadsItemsOnCreation() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val viewModel = viewModel(datasource)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.items.size)
    }

    @Test
    fun addConfirmWithBlankNameShowsErrorAndDoesNotCallAddGrocery() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.AddExpandClicked)

        viewModel.onEvent(GroceryListEvent.AddConfirmClicked)

        assertTrue(viewModel.state.value.addNameError)
        assertTrue(datasource.items.isEmpty())
    }

    @Test
    fun addConfirmWithNameAddsItemAndCollapsesRow() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.AddExpandClicked)
        viewModel.onEvent(GroceryListEvent.AddNameChanged("Bread"))

        viewModel.onEvent(GroceryListEvent.AddConfirmClicked)

        assertEquals(1, datasource.items.size)
        assertEquals("Bread", datasource.items.single().name)
        assertFalse(viewModel.state.value.isAddExpanded)
        assertEquals("", viewModel.state.value.addName)
    }

    @Test
    fun editExpandPrefillsCurrentValues() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = "2%")),
        )
        val viewModel = viewModel(datasource)

        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))

        assertEquals(1, viewModel.state.value.editingItemId)
        assertEquals("Milk", viewModel.state.value.editName)
        assertEquals("2%", viewModel.state.value.editDescription)
    }

    @Test
    fun editConfirmWithBlankNameShowsErrorAndDoesNotCallEditGrocery() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))
        viewModel.onEvent(GroceryListEvent.EditNameChanged(""))

        viewModel.onEvent(GroceryListEvent.EditConfirmClicked)

        assertTrue(viewModel.state.value.editNameError)
        assertEquals("Milk", datasource.items.single().name)
    }

    @Test
    fun editConfirmWithNameUpdatesItemAndCollapsesRow() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))
        viewModel.onEvent(GroceryListEvent.EditNameChanged("Oat milk"))

        viewModel.onEvent(GroceryListEvent.EditConfirmClicked)

        assertEquals("Oat milk", datasource.items.single().name)
        assertNull(viewModel.state.value.editingItemId)
    }

    @Test
    fun completeItemDeletesIt() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))

        assertTrue(datasource.items.isEmpty())
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun loadItemsFailureShowsErrorEffectAndStopsLoading() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        datasource.failNextCall = true

        val viewModel = viewModel(datasource)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(GroceryListEffect.ShowError("fake failure"), viewModel.effect.first())
    }

    @Test
    fun addConfirmFailureKeepsRowExpandedAndDoesNotAddItem() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.AddExpandClicked)
        viewModel.onEvent(GroceryListEvent.AddNameChanged("Bread"))
        datasource.failNextCall = true

        viewModel.onEvent(GroceryListEvent.AddConfirmClicked)

        assertTrue(datasource.items.isEmpty())
        assertTrue(viewModel.state.value.isAddExpanded)
        assertEquals("Bread", viewModel.state.value.addName)
        assertEquals(GroceryListEffect.ShowError("fake failure"), viewModel.effect.first())
    }

    @Test
    fun editConfirmFailureKeepsEditingStateAndDoesNotUpdateItem() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))
        viewModel.onEvent(GroceryListEvent.EditNameChanged("Oat milk"))
        datasource.failNextCall = true

        viewModel.onEvent(GroceryListEvent.EditConfirmClicked)

        assertEquals("Milk", datasource.items.single().name)
        assertEquals(1, viewModel.state.value.editingItemId)
        assertEquals("Oat milk", viewModel.state.value.editName)
        assertEquals(GroceryListEffect.ShowError("fake failure"), viewModel.effect.first())
    }

    @Test
    fun completeItemFailureKeepsItem() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        datasource.failNextCall = true

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))

        assertEquals(1, datasource.items.size)
        assertEquals(GroceryListEffect.ShowError("fake failure"), viewModel.effect.first())
    }
}
