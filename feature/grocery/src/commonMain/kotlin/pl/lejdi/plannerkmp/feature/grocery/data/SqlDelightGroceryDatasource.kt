package pl.lejdi.plannerkmp.feature.grocery.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.database.asAppResult
import pl.lejdi.plannerkmp.core.database.checkSingleRowAffected
import pl.lejdi.plannerkmp.core.database.safeMutation
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemDraft

internal class SqlDelightGroceryDatasource(
    private val queries: GroceryItemEntityQueries,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val logger: Logger,
) : GroceryDatasource {

    /**
     * SQLDelight re-runs the query and re-emits whenever `groceryItemEntity` changes, so callers
     * never have to ask again after a write.
     *
     * `flowOn` is what keeps the *mapping* off the main thread. `mapToList(io)` confines only the
     * query execution; every operator after it runs in the collector's context, which for every
     * caller here is `viewModelScope` — so turning the whole table into domain objects, on every
     * emission, was happening on the UI thread. The tasks datasource has always done this; this one
     * had the rule written down for it and not applied.
     */
    override fun observeItems(): Flow<AppResult<List<GroceryItem>>> =
        queries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { entities -> entities.map { it.toDomain() } }
            .asAppResult(logger, "observe grocery items")
            .flowOn(dispatchers.io)

    /**
     * No `checkSingleRowAffected` here, unlike every other mutation: an `INSERT ... VALUES` with one
     * row either throws or inserts exactly that row, so there is no "matched nothing" case to
     * detect. Reporting one as [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] was actively
     * harmful: [pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModel] reads that error as
     * "the row was deleted elsewhere", closes the editor and throws away what the user had typed —
     * about a row that was never supposed to exist yet.
     */
    override suspend fun addItem(draft: GroceryItemDraft): AppResult<Unit> = mutate("grocery item insert") {
        queries.insert(name = draft.name, description = draft.description).await()
        Unit
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> = mutate("grocery item update") {
        val rowsAffected = queries.update(name = item.name, description = item.description, id = item.id).await()
        checkSingleRowAffected(rowsAffected, "grocery item update")
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> = mutate("grocery item delete") {
        val rowsAffected = queries.deleteById(id).await()
        checkSingleRowAffected(rowsAffected, "grocery item delete")
    }

    // safeMutation, not safeQuery: see the note on the tasks datasource's own helper.
    private suspend fun <T> mutate(operation: String, block: suspend () -> T): AppResult<T> =
        safeMutation(scope, dispatchers, logger, operation, block)
}

private fun GroceryItemEntity.toDomain(): GroceryItem = GroceryItem(
    id = id,
    name = name,
    description = description,
)
