package pl.lejdi.plannerkmp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication
import pl.lejdi.plannerkmp.core.common.AppCoroutineScope
import pl.lejdi.plannerkmp.core.common.AppInitializer
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.di.commonModule
import pl.lejdi.plannerkmp.core.database.di.databaseModule
import pl.lejdi.plannerkmp.core.database.di.keyValueCacheModule
import pl.lejdi.plannerkmp.feature.grocery.di.groceryModule
import pl.lejdi.plannerkmp.feature.tasks.di.tasksModule

private const val TAG = "AppStartup"

/**
 * Every feature module, in one list.
 *
 * Kotlin/Native has no classpath scanning, so this list cannot be discovered at runtime the way the
 * tabs, nav entries and startup work inside it are — but it is the only place left that names a
 * feature.
 */
internal val featureModules = listOf(tasksModule, groceryModule)

/**
 * Every module the app runs on, exposed so a test can assert the graph resolves.
 *
 * `networkModule()` is deliberately not here. `:core:network` is kept and tested against a future
 * backend, but nothing calls it: registering it meant the app's graph could hand out a Ktor client
 * that no manifest had asked for the INTERNET permission for, so the first request would ever have
 * made would have surfaced a `SecurityException` mislabelled as a network error. Wire it back
 * together with the `:core:network` dependency in `shared/build.gradle.kts`.
 */
internal fun appModules() = listOf(commonModule, databaseModule, keyValueCacheModule) + featureModules

/**
 * Builds the app's object graph and starts its startup work.
 *
 * `koinApplication { }`, not `startKoin { }`. The latter installs the graph into Koin's *global*
 * mutable context, which made the one piece of state this app cannot see or test implicit: `App()`
 * reached for it with `getKoin()`, and the iOS entry point needed a hand-rolled `koinStarted`
 * boolean because a second `startKoin` throws. Returning the application instead makes the graph an
 * ordinary value that the platform entry point owns and hands to [App] — the same thing
 * `AppDependencyGraphTest` has always done, which is why that test could build the real graph in
 * the first place.
 *
 * The caller keeps the returned application for the life of the process: building it twice would
 * build a second set of database drivers.
 */
fun initKoin(
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = koinApplication {
    appDeclaration()
    modules(appModules())
}.also { it.koin.runStartupWork() }

/**
 * Runs every feature's contributed startup work, on an app-lifetime scope.
 *
 * Fire-and-forget on purpose: nothing on screen is waiting for it, and the data layer is reactive,
 * so whatever an initializer changes re-emits to whoever is observing. An initializer that throws
 * is caught here rather than taking the app down during `Application.onCreate`.
 *
 * One coroutine each, not one for all of them. They are independent, so serializing them only
 * meant a slow one delayed every initializer registered behind it — and now that an initializer
 * may collect for the lifetime of the app (the daily cleanup does), a single shared coroutine
 * would never have reached the second one at all. `AppCoroutineScope` is supervised, so one
 * failing does not cancel its neighbours.
 */
internal fun Koin.runStartupWork() {
    val logger = get<Logger>()
    val scope = get<AppCoroutineScope>()
    getAll<AppInitializer>().forEach { initializer ->
        scope.launch {
            try {
                initializer.initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "${initializer::class.simpleName} failed", e)
            }
        }
    }
}
