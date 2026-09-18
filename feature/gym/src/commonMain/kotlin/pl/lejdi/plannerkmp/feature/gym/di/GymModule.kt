package pl.lejdi.plannerkmp.feature.gym.di

import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.DayOfWeek
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
import pl.lejdi.plannerkmp.feature.gym.GymFeatureTab
import pl.lejdi.plannerkmp.feature.gym.GymNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.gym.GymNavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.gym.data.DayOfWeekColumnAdapter
import pl.lejdi.plannerkmp.feature.gym.data.GymDatabase
import pl.lejdi.plannerkmp.feature.gym.data.GymExerciseEntity
import pl.lejdi.plannerkmp.feature.gym.data.LocalDateColumnAdapter
import pl.lejdi.plannerkmp.feature.gym.data.SqlDelightGymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.GymDatasource
import pl.lejdi.plannerkmp.feature.gym.domain.ObserveGymWeek
import pl.lejdi.plannerkmp.feature.gym.domain.ToggleExerciseSet
import pl.lejdi.plannerkmp.feature.gym.ui.GymExerciseEditViewModel
import pl.lejdi.plannerkmp.feature.gym.ui.GymViewModel

private const val GYM_DRIVER = "gymDriver"

val gymModule = module {
    // Bound separately so it can be closed with the graph — see the note in TasksModule.
    single<SqlDriver>(named(GYM_DRIVER)) {
        get<DatabaseDriverFactory>().createDriver(GymDatabase.Schema, "gym.db")
    } onClose { it?.close() }
    single {
        GymDatabase(
            get(named(GYM_DRIVER)),
            gymExerciseEntityAdapter = GymExerciseEntity.Adapter(
                dayOfWeekAdapter = DayOfWeekColumnAdapter,
                completedOnAdapter = LocalDateColumnAdapter,
            ),
        )
    }
    single { get<GymDatabase>().gymExerciseEntityQueries }
    // AppCoroutineScope explicitly typed: Koin keys a definition by its declared type, and the
    // scope is registered as itself rather than as CoroutineScope.
    single<GymDatasource> { SqlDelightGymDatasource(get(), get<AppCoroutineScope>(), get(), get()) }

    singleOf(::ObserveGymWeek)
    singleOf(::ToggleExerciseSet)

    factoryOf(::GymNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
    factoryOf(::GymNavKeySerializersContributor) { bind<NavKeySerializersContributor>() }
    singleOf(::GymFeatureTab) { bind<FeatureTab>() }

    // get<SavedStateHandle>() resolves out of the CreationExtras koinViewModel() passes in.
    viewModel { GymViewModel(get(), get(), get(), get(), get(), get()) }
    // Destructured positionally, as TaskEditViewModel's definition is: both parameters are
    // nullable — an add has no id, an edit has no weekday to seed — and Koin cannot resolve a null
    // by type.
    viewModel { (exerciseId: Long?, dayOfWeek: DayOfWeek?) ->
        GymExerciseEditViewModel(exerciseId, dayOfWeek, get(), get(), get())
    }
}
