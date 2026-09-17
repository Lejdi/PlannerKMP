package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CoroutineScope
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger

/**
 * The key-value store, backed by this module's own one-table schema.
 *
 * [namespace] is prefixed onto every row id, so the table is partitioned by its owner rather than
 * by everyone independently picking unique key strings. One flat `id TEXT PRIMARY KEY` shared by
 * every feature meant two features choosing the same word would silently overwrite each other's
 * value through `INSERT OR REPLACE`, with nothing to catch it at compile time or at runtime.
 *
 * Both methods run through the `safeQuery`/[safeMutation] pair, which supplies the dispatcher hop
 * that makes `suspend` honest here (SQLDelight's generated queries block) and the logging of
 * anything the driver — or a [deserialize] on a corrupt value — throws. Only the read carries a
 * deadline; see [safeMutation] for why the write must not.
 */
@Suppress("LongParameterList") // A generic store's codecs; grouping them would only move the list.
class SqlDelightKeyValueCache<K, V>(
    private val namespace: String,
    private val queries: KeyValueEntryQueries,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val logger: Logger,
    private val encodeKey: (K) -> String,
    private val serialize: (V) -> String,
    private val deserialize: (String) -> V,
) : KeyValueCache<K, V> {

    override suspend fun get(key: K): AppResult<V?> =
        safeQuery(scope, dispatchers, logger, "read $namespace cache entry") {
            queries.get(rowId(key)).executeAsOneOrNull()?.let(deserialize)
        }

    // safeMutation for the write and safeQuery for the read: a deadline on a write cannot cancel
    // it, so it only turns a slow one into a failure the caller is told about and a row that
    // appears anyway.
    override suspend fun put(key: K, value: V): AppResult<Unit> =
        safeMutation(scope, dispatchers, logger, "write $namespace cache entry") {
            queries.put(rowId(key), serialize(value))
        }

    private fun rowId(key: K): String = "$namespace:${encodeKey(key)}"
}
