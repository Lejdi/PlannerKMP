package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.flow.collect
import pl.lejdi.plannerkmp.core.common.AppInitializer
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.common.onFailure

private const val TAG = "TasksCleanup"

/**
 * Runs the daily catch-up at startup, and again whenever the date rolls over.
 *
 * This used to hang off `DashboardViewModel.init`, which tied an application-wide, data-mutating
 * job to one screen's lifetime: a user who stayed on the Grocery tab never ran it, and every tab
 * switch that rebuilt the dashboard ran it again. It is also not a thing the user can act on, so
 * its failure belongs in the log rather than in a snackbar on a screen that did not ask for it.
 *
 * Moving it to start-up fixed the wrong half of the problem, though: "once per app start" is not
 * the requirement — "once per day" is. A phone that never restarts the process, which is the
 * normal case, went days without a cleanup, leaving expired tasks on the dashboard. Driving it
 * from [TodayProvider.todayFlow] makes the trigger match the rule, and collecting for the lifetime
 * of the app is why each initializer gets its own coroutine.
 *
 * Nothing waits for this. The dashboard observes the tasks table, so whatever the cleanup changes
 * re-emits to whoever is looking by itself.
 */
class TasksCleanupInitializer(
    private val updateTasksDates: UpdateTasksDates,
    private val todayProvider: TodayProvider,
    private val logger: Logger,
) : AppInitializer {

    override suspend fun initialize() {
        // The emitted date is passed on rather than dropped: the use case used to re-read the clock
        // for itself, so one cleanup run consulted "today" twice and could get two answers across
        // the very rollover that triggered it.
        todayProvider.todayFlow().collect { today ->
            updateTasksDates(today).onFailure { error ->
                logger.error(TAG, "daily task cleanup failed: ${error.message}", error.cause)
            }
        }
    }
}
