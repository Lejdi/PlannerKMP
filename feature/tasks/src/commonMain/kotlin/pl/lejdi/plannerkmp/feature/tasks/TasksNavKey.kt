package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Serializable, and keyed by id rather than by a whole [pl.lejdi.plannerkmp.feature.tasks.domain.Task].
 *
 * Carrying the entity meant the back stack could not be written to saved state (so rotation lost
 * it), and that the edit screen worked from a snapshot taken at navigation time — which the daily
 * cleanup could have moved on since.
 */
sealed interface TasksNavKey : NavKey {

    @Serializable
    data object Dashboard : TasksNavKey

    @Serializable
    data class TaskEdit(val taskId: Long? = null) : TasksNavKey
}
