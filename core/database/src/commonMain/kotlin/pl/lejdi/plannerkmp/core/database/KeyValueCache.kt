package pl.lejdi.plannerkmp.core.database

import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * A small typed store for app state that is not domain data.
 *
 * Returns [AppResult] like every other port in this app, rather than throwing. It used to be the
 * one exception, which meant each consumer had to re-wrap it in [safeQuery] to restore the
 * convention — paying a second dispatcher hop to do so — and the first consumer that forgot would
 * have let a driver exception escape into a use case.
 *
 * Deliberately only the two operations something actually calls. `remove`/`clear` were here too,
 * implemented twice and called never; they are three lines to add back the day a caller exists.
 */
interface KeyValueCache<K, V> {
    suspend fun get(key: K): AppResult<V?>
    suspend fun put(key: K, value: V): AppResult<Unit>
}
