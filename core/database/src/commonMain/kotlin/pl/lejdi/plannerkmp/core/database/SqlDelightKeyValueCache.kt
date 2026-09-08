package pl.lejdi.plannerkmp.core.database

class SqlDelightKeyValueCache<K, V>(
    private val queries: KeyValueEntryQueries,
    private val encodeKey: (K) -> String,
    private val serialize: (V) -> String,
    private val deserialize: (String) -> V,
) : KeyValueCache<K, V> {

    override suspend fun get(key: K): V? =
        queries.get(encodeKey(key)).executeAsOneOrNull()?.let(deserialize)

    override suspend fun put(key: K, value: V) {
        queries.put(encodeKey(key), serialize(value))
    }

    override suspend fun remove(key: K) {
        queries.remove(encodeKey(key))
    }

    override suspend fun clear() {
        queries.clear()
    }
}
