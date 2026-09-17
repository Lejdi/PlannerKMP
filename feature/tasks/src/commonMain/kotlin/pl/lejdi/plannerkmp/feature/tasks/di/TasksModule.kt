package pl.lejdi.plannerkmp.feature.tasks.di

import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import pl.lejdi.plannerkmp.core.common.AppCoroutineScope
import pl.lejdi.plannerkmp.core.common.AppInitializer
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.SqlDelightKeyValueCache
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.tasks.TasksFeatureTab
import pl.lejdi.plannerkmp.feature.tasks.TasksNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.TasksNavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.tasks.data.KeyValueCleanupDateStore
import pl.lejdi.plannerkmp.feature.tasks.data.LocalDateColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.LocalTimeColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.SqlDelightTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.data.TaskEntity
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatabase
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupDateStore
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.ObserveTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksCleanupInitializer
import pl.lejdi.plannerkmp.feature.tasks.domain.TasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModel
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModel

/** This feature's slice of the shared key-value table. */
private const val TASKS_CACHE_NAMESPACE = "tasks"

/**
 * The Koin name for the cache binding.
 *
 * Deliberately not the string the store writes under — that is
 * [pl.lejdi.plannerkmp.feature.tasks.data.LAST_CLEANUP_DATE_KEY], and the two used to be the same
 * literal, `"lastCleanupDate"`, in two files meaning two unrelated things: a DI qualifier and a row
 * id. Two constants that must never be confused should not read identically.
 */
private const val LAST_CLEANUP_DATE_CACHE = "tasks.lastCleanupDateCache"

private const val TASKS_DRIVER = "tasksDriver"

val tasksModule = module {
    // The driver is its own binding so that closing it is expressible: Koin's `onClose` runs when
    // the graph is torn down, and the generated database class does not expose the driver it was
    // handed. Each feature owns a separate SQLite file, which keeps schema ownership where the
    // module boundary is — the cost being three connections that nothing was ever closing.
    single<SqlDriver>(named(TASKS_DRIVER)) {
        get<DatabaseDriverFactory>().createDriver(TasksDatabase.Schema, "tasks.db")
    } onClose { it?.close() }
    single {
        TasksDatabase(
            get(named(TASKS_DRIVER)),
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        )
    }
    single { get<TasksDatabase>().taskEntityQueries }
    // Qualified because Koin keys generic types by their erased class: a second
    // KeyValueCache<*, *> registered anywhere in the app would silently collide. The namespace
    // handles the same collision one layer down, in the shared table itself.
    single<KeyValueCache<String, LocalDate>>(named(LAST_CLEANUP_DATE_CACHE)) {
        SqlDelightKeyValueCache(
            namespace = TASKS_CACHE_NAMESPACE,
            queries = get(),
            // Explicitly typed: Koin keys a definition by its declared type, and AppCoroutineScope
            // is registered as itself, not as CoroutineScope.
            scope = get<AppCoroutineScope>(),
            dispatchers = get(),
            logger = get(),
            encodeKey = { it },
            serialize = { it.toString() },
            deserialize = { LocalDate.parse(it) },
        )
    }
    single<CleanupDateStore> { KeyValueCleanupDateStore(get(named(LAST_CLEANUP_DATE_CACHE))) }
    single<TasksDatasource> { SqlDelightTasksDatasource(get(), get<AppCoroutineScope>(), get(), get()) }

    factory { ObserveTasksForDashboard(get(), get(), get()) }
    factory { MarkTaskComplete(get()) }
    factory { UpdateTasksDates(get(), get()) }

    // get<SavedStateHandle>() resolves out of the CreationExtras that koinViewModel() passes in,
    // which is where per-nav-entry saved state lives.
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { (taskId: Long?) -> TaskEditViewModel(taskId, get(), get(), get(), get()) }

    factoryOf(::TasksNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
    factoryOf(::TasksNavKeySerializersContributor) { bind<NavKeySerializersContributor>() }
    singleOf(::TasksFeatureTab) { bind<FeatureTab>() }
    // The daily cleanup is app-scoped work, not the dashboard's: :shared starts it once and it
    // keeps running, re-firing whenever the date rolls over.
    factoryOf(::TasksCleanupInitializer) { bind<AppInitializer>() }
}
