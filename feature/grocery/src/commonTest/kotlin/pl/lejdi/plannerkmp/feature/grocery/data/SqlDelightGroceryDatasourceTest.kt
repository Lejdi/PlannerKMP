package pl.lejdi.plannerkmp.feature.grocery.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemDraft
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In `commonTest`, so it runs on the native driver too — see the tasks equivalent for why. */
class SqlDelightGroceryDatasourceTest {

    private lateinit var driver: SqlDriver
    private lateinit var datasource: SqlDelightGroceryDatasource
    private var driverClosed = false

    /** Stands in for the application scope the datasource is given in production. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        driverClosed = false
        driver = inMemorySqlDriver(GroceryDatabase.Schema)
        datasource = SqlDelightGroceryDatasource(
            GroceryDatabase(driver).groceryItemEntityQueries,
            appScope,
            // Unconfined as the io dispatcher: safeQuery's withContext then runs the
            // blocking driver call inline on the test thread, so runTest never sees an
            // idle scheduler and fast-forwards into safeQuery's timeout.
            TestCoroutineDispatchers(Dispatchers.Unconfined),
            NoOpLogger(),
        )
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        if (!driverClosed) driver.close()
    }

    private suspend fun storedItems(): List<GroceryItem> {
        val result = datasource.observeItems().first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    @Test
    fun addThenObserveReturnsTheItem() = runTest {
        datasource.addItem(GroceryItemDraft.ofStored(name = "Milk", description = "2%"))

        val items = storedItems()

        assertEquals(1, items.size)
        assertEquals("Milk", items.single().name)
        assertEquals("2%", items.single().description)
    }

    @Test
    fun editUpdatesNameAndDescription() = runTest {
        datasource.addItem(GroceryItemDraft.ofStored(name = "Milk", description = "2%"))
        val added = storedItems().single()

        datasource.editItem(added.copy(name = "Oat milk", description = "Unsweetened"))

        val stored = storedItems().single()
        assertEquals("Oat milk", stored.name)
        assertEquals("Unsweetened", stored.description)
    }

    @Test
    fun deleteRemovesTheItem() = runTest {
        datasource.addItem(GroceryItemDraft.ofStored(name = "Milk", description = null))
        val added = storedItems().single()

        datasource.deleteItem(added.id)

        assertEquals(emptyList(), storedItems())
    }

    @Test
    fun mutatingAMissingRowFailsAsNotFound() = runTest {
        val delete = datasource.deleteItem(999)
        val edit = datasource.editItem(GroceryItem(id = 999, name = "Ghost", description = null))

        // The *type* matters: GroceryListViewModel branches on NotFound to close the editor
        // instead of offering a retry that can never work.
        assertTrue(delete is AppResult.Failure && delete.error is DomainError.NotFound)
        assertTrue(edit is AppResult.Failure && edit.error is DomainError.NotFound)
    }

    @Test
    fun observeItemsEmitsAgainAfterAWrite() = runTest {
        val emissions = mutableListOf<List<GroceryItem>>()
        val job = launch {
            datasource.observeItems().collect { result ->
                if (result is AppResult.Success) emissions += result.data
            }
        }
        runCurrent()

        datasource.addItem(GroceryItemDraft.ofStored(name = "Bread", description = null))
        runCurrent()
        job.cancel()

        assertEquals(2, emissions.size)
        assertEquals("Bread", emissions.last().single().name)
    }

    @Test
    fun observeItemsSurfacesADatabaseFailureAsAResult() = runTest {
        driver.close()
        driverClosed = true

        val result = datasource.observeItems().first()

        assertTrue(result is AppResult.Failure)
    }
}
