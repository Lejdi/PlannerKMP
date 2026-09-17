package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * Remembers the last day the cleanup job ran.
 *
 * Its own port rather than two more methods on [TasksDatasource]: this is app state, not task
 * storage, and keeping it there forced every tasks test double to stub two unrelated methods.
 */
interface CleanupDateStore {
    suspend fun getLastCleanupDate(): AppResult<LocalDate?>
    suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit>
}
