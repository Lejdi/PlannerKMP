package pl.lejdi.plannerkmp.feature.grocery.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatabase
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.data.SqlDelightGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModel

val groceryModule = module {
    single { GroceryDatabase(get<DatabaseDriverFactory>().createDriver(GroceryDatabase.Schema, "grocery.db")) }
    single { get<GroceryDatabase>().groceryItemEntityQueries }
    single<GroceryDatasource> { SqlDelightGroceryDatasource(get()) }

    factory { GetGroceryItems(get()) }
    factory { AddGrocery(get()) }
    factory { EditGrocery(get()) }
    factory { DeleteGrocery(get()) }

    viewModel { GroceryListViewModel(get(), get(), get(), get()) }
}
