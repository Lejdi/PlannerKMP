package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class FakeGroceryDatasource(initialItems: List<GroceryItem> = emptyList()) : GroceryDatasource {

    val items = mutableListOf<GroceryItem>().apply { addAll(initialItems) }
    var nextId: Long = (initialItems.maxOfOrNull { it.id } ?: 0L) + 1
    var failNextCall: Boolean = false

    override suspend fun getAllItems(): AppResult<List<GroceryItem>> {
        if (consumeFailure()) return failure()
        return AppResult.Success(items.toList())
    }

    override suspend fun addItem(item: GroceryItem): AppResult<Unit> {
        if (consumeFailure()) return failure()
        items.add(item.copy(id = nextId++))
        return AppResult.Success(Unit)
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return failure()
        items[index] = item
        return AppResult.Success(Unit)
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val removed = items.removeAll { it.id == id }
        return if (removed) AppResult.Success(Unit) else failure()
    }

    private fun consumeFailure(): Boolean {
        if (!failNextCall) return false
        failNextCall = false
        return true
    }

    private fun <T> failure(): AppResult<T> = AppResult.Failure(DomainError.Database("fake failure"))
}
