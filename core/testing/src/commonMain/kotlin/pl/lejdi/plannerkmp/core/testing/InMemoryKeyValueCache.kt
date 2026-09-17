package pl.lejdi.plannerkmp.core.testing

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.KeyValueCache

/** Stands in for the SQLDelight-backed cache, for a test that is not testing storage. */
class InMemoryKeyValueCache<K, V> : KeyValueCache<K, V> {
    private val values = mutableMapOf<K, V>()

    override suspend fun get(key: K): AppResult<V?> = AppResult.Success(values[key])

    override suspend fun put(key: K, value: V): AppResult<Unit> {
        values[key] = value
        return AppResult.Success(Unit)
    }
}
