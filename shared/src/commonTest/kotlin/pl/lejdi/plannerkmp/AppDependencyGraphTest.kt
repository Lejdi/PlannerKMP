package pl.lejdi.plannerkmp

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.common.AppCoroutineScope
import pl.lejdi.plannerkmp.core.common.AppInitializer
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.core.testing.CloseTrackingSqlDriver
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupDateStore
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.ObserveTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModel
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Koin resolves at runtime, so a missing or mistyped binding is a crash when the user opens a
 * screen — not a compile error. This builds the real graph (with an in-memory database standing in
 * for the platform one) and resolves everything the app resolves, so the build catches it instead.
 *
 * In `commonTest`, not `androidHostTest`, so it runs on iOS too. Kotlin/Native has no classpath
 * scanning — the very thing `appModules()` is written around — so the graph is exactly the place
 * where a platform can differ, and resolving it only on the JVM checked the half that was never in
 * doubt.
 */
class AppDependencyGraphTest {

    private val drivers = mutableListOf<CloseTrackingSqlDriver>()

    private val inMemoryDrivers = module {
        single<DatabaseDriverFactory> {
            object : DatabaseDriverFactory {
                override fun createDriver(
                    schema: SqlSchema<QueryResult.Value<Unit>>,
                    databaseName: String,
                ): SqlDriver = CloseTrackingSqlDriver(inMemorySqlDriver(schema)).also { drivers += it }
            }
        }
    }

    private var app: KoinApplication? = null

    private fun app(): KoinApplication = koinApplication {
        allowOverride(true)
        modules(appModules())
        modules(inMemoryDrivers)
    }.also { app = it }

    private fun koin(): Koin = app().koin

    /**
     * Closing the application is what drops Koin's single instances.
     *
     * They are held by the `Module` objects themselves, which are shared top-level vals, so an
     * application left open leaves its singletons — and their open database drivers — in place for
     * the next test. That test then resolves the same ports without creating anything, which is how
     * a driver-lifecycle assertion could see no drivers at all.
     */
    @AfterTest
    fun tearDown() {
        app?.close()
        app = null
        drivers.filterNot { it.closed }.forEach { it.close() }
        drivers.clear()
    }

    @Test
    fun everyScreensViewModelCanBeConstructed() {
        val koin = koin()

        // koinViewModel() supplies the SavedStateHandle out of the nav entry's CreationExtras; a
        // plain get() has no CreationExtras, so the test passes one the same way Koin would.
        koin.get<DashboardViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<GroceryListViewModel> { parametersOf(SavedStateHandle()) }
        // Both the "add" and the "edit" entry points, since the id is an injected parameter.
        koin.get<TaskEditViewModel> { parametersOf(null, SavedStateHandle()) }
        koin.get<TaskEditViewModel> { parametersOf(1L, SavedStateHandle()) }
    }

    @Test
    fun everyFeatureContributesItsTabEntriesAndSerializers() {
        val koin = koin()

        val tabs = koin.getAll<FeatureTab>().distinct()
        val entries = koin.getAll<NavEntryProviderContributor>().distinct()
        val serializers = koin.getAll<NavKeySerializersContributor>().distinct()

        assertEquals(featureModules.size, tabs.size, "each feature contributes exactly one tab")
        assertEquals(featureModules.size, entries.size)
        assertEquals(featureModules.size, serializers.size)
    }

    /**
     * The multibinding that makes app-start work run at all.
     *
     * It was in no test: unbound, or bound twice through the type-keyed override that `FeatureTab`
     * carries a warning about, and the build said nothing while the daily cleanup silently stopped
     * running for everyone.
     */
    @Test
    fun everyFeaturesStartupWorkIsContributed() {
        val initializers = koin().getAll<AppInitializer>().distinct()

        assertEquals(1, initializers.size, "only :feature:tasks contributes startup work today")
    }

    @Test
    fun tabOrderIsUnambiguous() {
        val orders = koin().getAll<FeatureTab>().distinct().map { it.order }

        assertEquals(
            orders.distinct().size,
            orders.size,
            "two features claiming one slot would make the bar order arbitrary",
        )
    }

    @Test
    fun everyUseCaseAndPortResolves() {
        val koin = koin()

        // The ViewModels above already pull most of these; naming them makes a missing binding
        // point at the definition rather than at whichever screen happened to need it.
        assertTrue(koin.get<TasksDatasource>() is TasksDatasource)
        assertTrue(koin.get<CleanupDateStore>() is CleanupDateStore)
        assertTrue(koin.get<GroceryDatasource>() is GroceryDatasource)
        koin.get<ObserveTasksForDashboard>()
        koin.get<UpdateTasksDates>()
        koin.get<MarkTaskComplete>()
        koin.get<CoroutineDispatchers>()
        koin.get<TodayProvider>()
        // Both were resolved by nothing, though runStartupWork() asks for them on every launch.
        koin.get<Logger>()
        koin.get<AppCoroutineScope>()
    }

    /**
     * Closing the graph closes the drivers.
     *
     * Each `single<SqlDriver>` carries an `onClose` precisely so the connection can be released,
     * and nothing exercised it: this test's own factory used to close the drivers it had recorded,
     * which would have passed just as happily with every `onClose` deleted.
     */
    @Test
    fun closingTheGraphClosesEveryDatabaseDriver() {
        val application = app()
        // Touch the ports so the lazily-created drivers actually exist.
        application.koin.get<TasksDatasource>()
        application.koin.get<GroceryDatasource>()
        application.koin.get<CleanupDateStore>()
        val opened = drivers.toList()
        assertEquals(3, opened.size, "one driver per database: tasks, grocery, key-value; got ${opened.size}")
        assertTrue(opened.none { it.closed }, "nothing is closed before the graph is")

        application.close()

        assertTrue(opened.all { it.closed }, "every driver's onClose must have run")
    }
}
