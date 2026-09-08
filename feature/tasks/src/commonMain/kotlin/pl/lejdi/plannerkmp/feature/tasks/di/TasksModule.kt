package pl.lejdi.plannerkmp.feature.tasks.di

import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.SqlDelightKeyValueCache
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.TasksNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.data.LocalDateColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.LocalTimeColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.SqlDelightTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.data.TaskEntity
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatabase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModel
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModel

val tasksModule = module {
    single {
        TasksDatabase(
            get<DatabaseDriverFactory>().createDriver(TasksDatabase.Schema, "tasks.db"),
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        )
    }
    single { get<TasksDatabase>().taskEntityQueries }
    single<KeyValueCache<Unit, LocalDate>> {
        SqlDelightKeyValueCache(
            queries = get(),
            encodeKey = { "lastCleanupDate" },
            serialize = { it.toString() },
            deserialize = { LocalDate.parse(it) },
        )
    }
    single<TasksDatasource> { SqlDelightTasksDatasource(get(), get()) }

    factory { GetTasksForDashboard(get(), get()) }
    factory { MarkTaskComplete(get()) }
    factory { UpdateTasksDates(get(), get()) }
    factory { AddTask(get()) }
    factory { EditTask(get()) }
    factory { DeleteTask(get()) }

    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { (task: Task?) -> TaskEditViewModel(task, get(), get(), get(), get()) }

    factoryOf(::TasksNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
}
