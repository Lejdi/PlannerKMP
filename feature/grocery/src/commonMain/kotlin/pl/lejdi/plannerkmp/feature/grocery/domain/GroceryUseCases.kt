package pl.lejdi.plannerkmp.feature.grocery.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatasource

class GetGroceryItems(
    private val datasource: GroceryDatasource,
) : UseCase<Unit, AppResult<List<GroceryItem>>> {
    override suspend fun invoke(params: Unit): AppResult<List<GroceryItem>> = datasource.getAllItems()
}

class AddGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<GroceryItem, AppResult<Unit>> {
    override suspend fun invoke(params: GroceryItem): AppResult<Unit> = datasource.addItem(params)
}

class EditGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<GroceryItem, AppResult<Unit>> {
    override suspend fun invoke(params: GroceryItem): AppResult<Unit> = datasource.editItem(params)
}

class DeleteGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<Long, AppResult<Unit>> {
    override suspend fun invoke(params: Long): AppResult<Unit> = datasource.deleteItem(params)
}
