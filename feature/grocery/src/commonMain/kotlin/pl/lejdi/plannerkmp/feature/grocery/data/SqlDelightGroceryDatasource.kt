package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.checkSingleRowAffected
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class SqlDelightGroceryDatasource(
    private val queries: GroceryItemEntityQueries,
) : GroceryDatasource {

    override suspend fun getAllItems(): AppResult<List<GroceryItem>> = safeQuery {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        val rowsAffected = queries.insert(name = item.name, description = item.description).await()
        checkSingleRowAffected(rowsAffected, "grocery item insert")
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        val rowsAffected = queries.update(name = item.name, description = item.description, id = item.id).await()
        checkSingleRowAffected(rowsAffected, "grocery item update")
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> = safeQuery {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "grocery item delete")
    }
}

private fun GroceryItemEntity.toDomain(): GroceryItem = GroceryItem(
    id = id,
    name = name,
    description = description,
)
