package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

interface GroceryDatasource {
    suspend fun getAllItems(): AppResult<List<GroceryItem>>
    suspend fun addItem(item: GroceryItem): AppResult<Unit>
    suspend fun editItem(item: GroceryItem): AppResult<Unit>
    suspend fun deleteItem(id: Long): AppResult<Unit>
}
