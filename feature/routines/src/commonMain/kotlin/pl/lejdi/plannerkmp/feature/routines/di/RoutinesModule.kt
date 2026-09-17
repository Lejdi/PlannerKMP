package pl.lejdi.plannerkmp.feature.routines.di

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import pl.lejdi.plannerkmp.core.common.AppCoroutineScope
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.routines.RoutinesFeatureTab
import pl.lejdi.plannerkmp.feature.routines.RoutinesNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.routines.RoutinesNavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.routines.data.LocalDateColumnAdapter
import pl.lejdi.plannerkmp.feature.routines.data.RoutineEntity
import pl.lejdi.plannerkmp.feature.routines.data.RoutinesDatabase
import pl.lejdi.plannerkmp.feature.routines.data.SqlDelightRoutinesDatasource
import pl.lejdi.plannerkmp.feature.routines.domain.ObserveRoutinesForToday
import pl.lejdi.plannerkmp.feature.routines.domain.RoutinesDatasource
import pl.lejdi.plannerkmp.feature.routines.domain.ToggleRoutineDone
import pl.lejdi.plannerkmp.feature.routines.ui.RoutinesViewModel

private const val ROUTINES_DRIVER = "routinesDriver"

val routinesModule = module {
    // Bound separately so it can be closed with the graph — see the note in TasksModule.
    single<SqlDriver>(named(ROUTINES_DRIVER)) {
        get<DatabaseDriverFactory>().createDriver(RoutinesDatabase.Schema, "routines.db")
    } onClose { it?.close() }
    single {
        RoutinesDatabase(
            get(named(ROUTINES_DRIVER)),
            routineEntityAdapter = RoutineEntity.Adapter(completedOnAdapter = LocalDateColumnAdapter),
        )
    }
    single { get<RoutinesDatabase>().routineEntityQueries }
    // AppCoroutineScope explicitly typed: Koin keys a definition by its declared type, and the
    // scope is registered as itself rather than as CoroutineScope.
    single<RoutinesDatasource> { SqlDelightRoutinesDatasource(get(), get<AppCoroutineScope>(), get(), get()) }

    singleOf(::ObserveRoutinesForToday)
    singleOf(::ToggleRoutineDone)

    factoryOf(::RoutinesNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
    factoryOf(::RoutinesNavKeySerializersContributor) { bind<NavKeySerializersContributor>() }
    singleOf(::RoutinesFeatureTab) { bind<FeatureTab>() }

    // get<SavedStateHandle>() resolves out of the CreationExtras koinViewModel() passes in.
    viewModel { RoutinesViewModel(get(), get(), get(), get(), get()) }
}
