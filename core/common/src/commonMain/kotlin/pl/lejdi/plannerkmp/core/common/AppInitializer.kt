package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Work that starts once per app start, contributed by the feature that owns it.
 *
 * Koin-multibound so `:shared` can run every initializer without knowing which features exist —
 * the same mechanism as `FeatureTab` and the nav contributors in `core:navigation`, for the same
 * reason. The alternative, hanging app-wide work off whichever screen happens to be opened first,
 * ties an application concern to one ViewModel's lifetime: the job then never runs for a user who
 * does not visit that screen, and runs again every time that screen is rebuilt.
 *
 * [initialize] may return, or it may collect for the lifetime of the app — the daily cleanup does
 * the latter, because "once per start" was never the real requirement; "once per day the app is
 * alive" was. Each initializer therefore gets its own coroutine, so a long-lived one cannot hold
 * up the others.
 *
 * An initializer must not throw. It reports its own failures through [Logger]; nothing is watching
 * its return value, because by design nothing is waiting for it.
 */
fun interface AppInitializer {
    suspend fun initialize()
}

/**
 * An application-lifetime coroutine scope.
 *
 * `SupervisorJob`, so one initializer failing does not cancel the others, and `default` rather than
 * `main`, so startup work never lands on the UI thread.
 */
class AppCoroutineScope(dispatchers: CoroutineDispatchers) : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + dispatchers.default
}
