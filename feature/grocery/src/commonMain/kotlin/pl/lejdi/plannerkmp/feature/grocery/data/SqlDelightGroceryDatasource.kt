package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class SqlDelightGroceryDatasource(
    private val queries: GroceryItemEntityQueries,
) : GroceryDatasource {

    override suspend fun getAllItems(): AppResult<List<GroceryItem>> = safeQuery {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        queries.insert(name = item.name, description = item.description)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row inserted, got $rowsAffected" }
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        queries.update(name = item.name, description = item.description, id = item.id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row updated, got $rowsAffected" }
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> = safeQuery {
        queries.deleteById(id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row deleted, got $rowsAffected" }
    }
}

private fun GroceryItemEntity.toDomain(): GroceryItem = GroceryItem(
    id = id,
    name = name,
    description = description,
)
