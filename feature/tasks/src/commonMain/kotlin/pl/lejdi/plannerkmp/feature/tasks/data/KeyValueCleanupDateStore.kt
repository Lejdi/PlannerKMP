package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupDateStore

/** The key this store occupies inside the tasks namespace of the shared key-value table. */
internal const val LAST_CLEANUP_DATE_KEY = "lastCleanupDate"

/**
 * Stores the cleanup date in the shared key-value cache.
 *
 * Pure delegation now that [KeyValueCache] returns [AppResult] itself. It used to re-wrap both
 * calls in `safeQuery` purely to restore the convention the cache broke, which also meant the read
 * hopped to the IO dispatcher twice and carried two nested timeouts.
 *
 * Keyed by [String] rather than by [Unit]. The `Unit` version needed an `encodeKey` that ignored its
 * own argument and returned a constant, which is a generic type bent into a single-value store —
 * the key belongs here, where it is one named constant, not in the DI module as a lambda that
 * discards what it is given.
 */
internal class KeyValueCleanupDateStore(
    private val cache: KeyValueCache<String, LocalDate>,
) : CleanupDateStore {

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> = cache.get(LAST_CLEANUP_DATE_KEY)

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> =
        cache.put(LAST_CLEANUP_DATE_KEY, date)
}
