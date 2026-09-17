package pl.lejdi.plannerkmp.feature.grocery.di

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
import pl.lejdi.plannerkmp.feature.grocery.GroceryFeatureTab
import pl.lejdi.plannerkmp.feature.grocery.GroceryNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.grocery.GroceryNavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatabase
import pl.lejdi.plannerkmp.feature.grocery.data.SqlDelightGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModel

private const val GROCERY_DRIVER = "groceryDriver"

val groceryModule = module {
    // Bound separately so it can be closed with the graph — see the note in TasksModule.
    single<SqlDriver>(named(GROCERY_DRIVER)) {
        get<DatabaseDriverFactory>().createDriver(GroceryDatabase.Schema, "grocery.db")
    } onClose { it?.close() }
    single { GroceryDatabase(get(named(GROCERY_DRIVER))) }
    single { get<GroceryDatabase>().groceryItemEntityQueries }
    // AppCoroutineScope explicitly typed: Koin keys a definition by its declared type, and the scope
    // is registered as itself rather than as CoroutineScope.
    single<GroceryDatasource> { SqlDelightGroceryDatasource(get(), get<AppCoroutineScope>(), get(), get()) }

    factoryOf(::GroceryNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
    factoryOf(::GroceryNavKeySerializersContributor) { bind<NavKeySerializersContributor>() }
    singleOf(::GroceryFeatureTab) { bind<FeatureTab>() }

    // get<SavedStateHandle>() resolves out of the CreationExtras koinViewModel() passes in.
    viewModel { GroceryListViewModel(get(), get(), get()) }
}
