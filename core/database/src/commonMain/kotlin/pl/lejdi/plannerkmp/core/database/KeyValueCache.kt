package pl.lejdi.plannerkmp.core.database

interface KeyValueCache<K, V> {
    suspend fun get(key: K): V?
    suspend fun put(key: K, value: V)
    suspend fun remove(key: K)
    suspend fun clear()
}

class InMemoryKeyValueCache<K, V> : KeyValueCache<K, V> {
    private val values = mutableMapOf<K, V>()

    override suspend fun get(key: K): V? = values[key]

    override suspend fun put(key: K, value: V) {
        values[key] = value
    }

    override suspend fun remove(key: K) {
        values.remove(key)
    }

    override suspend fun clear() {
        values.clear()
    }
}
