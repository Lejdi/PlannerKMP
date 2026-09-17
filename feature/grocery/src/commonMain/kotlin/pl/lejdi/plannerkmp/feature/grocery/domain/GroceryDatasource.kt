package pl.lejdi.plannerkmp.feature.grocery.domain

import kotlinx.coroutines.flow.Flow
import pl.lejdi.plannerkmp.core.common.AppResult

/**
 * The grocery storage port: declared in `domain`, implemented in `data`, so the dependency runs
 * from the implementation towards the domain rather than the other way round.
 */
interface GroceryDatasource {
    /** Emits the list, and again on every change to it. */
    fun observeItems(): Flow<AppResult<List<GroceryItem>>>

    suspend fun addItem(draft: GroceryItemDraft): AppResult<Unit>

    suspend fun editItem(item: GroceryItem): AppResult<Unit>

    suspend fun deleteItem(id: Long): AppResult<Unit>
}
