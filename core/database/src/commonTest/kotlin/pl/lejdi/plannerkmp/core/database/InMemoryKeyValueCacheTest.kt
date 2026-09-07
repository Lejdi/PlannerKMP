package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryKeyValueCacheTest {

    @Test
    fun getReturnsNullForMissingKey() = runTest {
        val cache = InMemoryKeyValueCache<String, String>()

        assertNull(cache.get("missing"))
    }

    @Test
    fun putThenGetReturnsStoredValue() = runTest {
        val cache = InMemoryKeyValueCache<String, Int>()

        cache.put("answer", 42)

        assertEquals(42, cache.get("answer"))
    }

    @Test
    fun putOverwritesExistingValue() = runTest {
        val cache = InMemoryKeyValueCache<String, Int>()
        cache.put("key", 1)

        cache.put("key", 2)

        assertEquals(2, cache.get("key"))
    }

    @Test
    fun removeDeletesOnlyThatKey() = runTest {
        val cache = InMemoryKeyValueCache<String, Int>()
        cache.put("a", 1)
        cache.put("b", 2)

        cache.remove("a")

        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun clearRemovesEveryEntry() = runTest {
        val cache = InMemoryKeyValueCache<String, Int>()
        cache.put("a", 1)
        cache.put("b", 2)

        cache.clear()

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
