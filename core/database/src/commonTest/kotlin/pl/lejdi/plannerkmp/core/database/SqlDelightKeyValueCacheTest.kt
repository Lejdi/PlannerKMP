package pl.lejdi.plannerkmp.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In `commonTest`, so the namespacing and serializer behaviour is checked on both drivers. */
class SqlDelightKeyValueCacheTest {

    private lateinit var driver: SqlDriver
    private lateinit var queries: KeyValueEntryQueries
    private lateinit var cache: SqlDelightKeyValueCache<String, Int>

    /** The application scope `safeQuery` runs its work on. Supervised, exactly as the real one is. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun cache(namespace: String) = SqlDelightKeyValueCache<String, Int>(
        namespace = namespace,
        queries = queries,
        // Stands in for the application scope the cache is given in production. Cancelled in
        // tearDown, so nothing a test starts can outlive it.
        scope = scope,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
        logger = NoOpLogger(),
        encodeKey = { it },
        serialize = { it.toString() },
        deserialize = { it.toInt() },
    )

    @BeforeTest
    fun setUp() {
        driver = inMemorySqlDriver(KeyValueDatabase.Schema)
        queries = KeyValueDatabase(driver).keyValueEntryQueries
        cache = cache("tasks")
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        driver.close()
    }

    @Test
    fun getReturnsNullForMissingKey() = runTest {
        assertNull(cache.get("missing").value())
    }

    @Test
    fun putThenGetRoundTripsThroughTheSerializers() = runTest {
        cache.put("key", 2)

        assertEquals(2, cache.get("key").value())
    }

    @Test
    fun putOverwritesAnExistingKeyRatherThanFailing() = runTest {
        cache.put("key", 1)
        cache.put("key", 2)

        assertEquals(2, cache.get("key").value())
    }

    @Test
    fun keysAreIndependentOfOneAnother() = runTest {
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(1, cache.get("a").value())
        assertEquals(2, cache.get("b").value())
    }

    /**
     * The reason [SqlDelightKeyValueCache] takes a namespace at all. One flat table is shared by
     * every feature, so without it two features picking the same word would overwrite each other
     * through `INSERT OR REPLACE`, silently.
     */
    @Test
    fun theSameKeyInTwoNamespacesIsTwoSeparateValues() = runTest {
        val tasks = cache("tasks")
        val grocery = cache("grocery")

        tasks.put("lastSync", 1)
        grocery.put("lastSync", 2)

        assertEquals(1, tasks.get("lastSync").value())
        assertEquals(2, grocery.get("lastSync").value())
    }

    /**
     * A corrupt stored value reaches the caller as a failure rather than as a thrown exception —
     * the point of the cache returning [AppResult] like every other port.
     */
    @Test
    fun anUndeserializableValueBecomesAFailureRatherThanThrowing() = runTest {
        cache.put("key", 1)
        queries.put("tasks:key", "not a number")

        assertTrue(cache.get("key") is AppResult.Failure)
    }

    // `as? Success ?: error(...)` would be wrong here: a successful null is the expected result of
    // reading a missing key, and elvis cannot tell it from a failure.
    private fun <T> AppResult<T>.value(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> error("expected success, was $this")
    }
}
