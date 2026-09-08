package pl.lejdi.plannerkmp.feature.grocery.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightGroceryDatasourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var datasource: SqlDelightGroceryDatasource

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GroceryDatabase.Schema.create(driver)
        datasource = SqlDelightGroceryDatasource(
            GroceryDatabase(driver).groceryItemEntityQueries,
            // Unconfined as the io dispatcher: safeQuery's withContext then runs the
            // blocking JDBC call inline on the test thread, so runTest never sees an
            // idle scheduler and fast-forwards into safeQuery's 5s timeout.
            TestCoroutineDispatchers(Dispatchers.Unconfined),
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addThenGetAllReturnsTheItem() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = "2%"))

        val result = datasource.getAllItems()

        assertTrue(result is AppResult.Success)
        assertEquals(1, result.data.size)
        assertEquals("Milk", result.data.single().name)
        assertEquals("2%", result.data.single().description)
    }

    @Test
    fun editUpdatesNameAndDescription() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = "2%"))
        val added = (datasource.getAllItems() as AppResult.Success).data.single()

        datasource.editItem(added.copy(name = "Oat milk", description = "Unsweetened"))

        val result = (datasource.getAllItems() as AppResult.Success).data.single()
        assertEquals("Oat milk", result.name)
        assertEquals("Unsweetened", result.description)
    }

    @Test
    fun deleteRemovesTheItem() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = null))
        val added = (datasource.getAllItems() as AppResult.Success).data.single()

        datasource.deleteItem(added.id)

        assertEquals(emptyList(), (datasource.getAllItems() as AppResult.Success).data)
    }

    @Test
    fun deleteOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.deleteItem(999)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun editOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.editItem(GroceryItem(id = 999, name = "Ghost", description = null))

        assertTrue(result is AppResult.Failure)
    }
}

private class TestCoroutineDispatchers(private val dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}
