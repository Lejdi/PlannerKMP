package pl.lejdi.plannerkmp.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightKeyValueCacheTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: SqlDelightKeyValueCache<String, Int>

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val queries = Database(driver).keyValueEntryQueries
        cache = SqlDelightKeyValueCache(
            queries = queries,
            encodeKey = { it },
            serialize = { it.toString() },
            deserialize = { it.toInt() },
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun getReturnsNullForMissingKey() = runTest {
        assertNull(cache.get("missing"))
    }

    @Test
    fun putThenGetReturnsStoredValue() = runTest {
        cache.put("answer", 42)

        assertEquals(42, cache.get("answer"))
    }

    @Test
    fun putOverwritesExistingValue() = runTest {
        cache.put("key", 1)

        cache.put("key", 2)

        assertEquals(2, cache.get("key"))
    }

    @Test
    fun removeDeletesOnlyThatKey() = runTest {
        cache.put("a", 1)
        cache.put("b", 2)

        cache.remove("a")

        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun clearRemovesEveryEntry() = runTest {
        cache.put("a", 1)
        cache.put("b", 2)

        cache.clear()

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
