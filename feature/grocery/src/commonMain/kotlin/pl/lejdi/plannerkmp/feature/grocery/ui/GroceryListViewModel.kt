package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class GroceryListViewModel(
    private val getGroceryItems: GetGroceryItems,
    private val addGrocery: AddGrocery,
    private val editGrocery: EditGrocery,
    private val deleteGrocery: DeleteGrocery,
) : BaseViewModel<GroceryListState, GroceryListEvent, GroceryListEffect>() {

    override fun createInitialState() = GroceryListState()

    init {
        loadItems()
    }

    override fun onEvent(event: GroceryListEvent) {
        when (event) {
            is GroceryListEvent.AddExpandClicked -> setState { copy(isAddExpanded = true) }
            is GroceryListEvent.AddNameChanged -> setState { copy(addName = event.value, addNameError = false) }
            is GroceryListEvent.AddDescriptionChanged -> setState { copy(addDescription = event.value) }
            is GroceryListEvent.AddConfirmClicked -> confirmAdd()
            is GroceryListEvent.AddCancelClicked -> setState {
                copy(isAddExpanded = false, addName = "", addDescription = "", addNameError = false)
            }
            is GroceryListEvent.EditExpandClicked -> setState {
                copy(
                    editingItemId = event.item.id,
                    editName = event.item.name,
                    editDescription = event.item.description.orEmpty(),
                    editNameError = false,
                )
            }
            is GroceryListEvent.EditNameChanged -> setState { copy(editName = event.value, editNameError = false) }
            is GroceryListEvent.EditDescriptionChanged -> setState { copy(editDescription = event.value) }
            is GroceryListEvent.EditConfirmClicked -> confirmEdit()
            is GroceryListEvent.EditCancelClicked -> setState {
                copy(editingItemId = null, editName = "", editDescription = "", editNameError = false)
            }
            is GroceryListEvent.CompleteItem -> completeItem(event.id)
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            when (val result = getGroceryItems(Unit)) {
                is AppResult.Success -> setState { copy(isLoading = false, items = result.data) }
                is AppResult.Failure -> {
                    setState { copy(isLoading = false) }
                    sendEffect(GroceryListEffect.ShowError(result.error.message))
                }
            }
        }
    }

    private fun confirmAdd() {
        val current = state.value
        if (current.addName.isBlank()) {
            setState { copy(addNameError = true) }
            return
        }
        viewModelScope.launch {
            val item = GroceryItem(id = 0, name = current.addName, description = current.addDescription.ifBlank { null })
            when (val result = addGrocery(item)) {
                is AppResult.Success -> {
                    setState { copy(isAddExpanded = false, addName = "", addDescription = "", addNameError = false) }
                    loadItems()
                }
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }

    private fun confirmEdit() {
        val current = state.value
        val editingId = current.editingItemId ?: return
        if (current.editName.isBlank()) {
            setState { copy(editNameError = true) }
            return
        }
        viewModelScope.launch {
            val item = GroceryItem(id = editingId, name = current.editName, description = current.editDescription.ifBlank { null })
            when (val result = editGrocery(item)) {
                is AppResult.Success -> {
                    setState { copy(editingItemId = null, editName = "", editDescription = "", editNameError = false) }
                    loadItems()
                }
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }

    private fun completeItem(id: Long) {
        viewModelScope.launch {
            when (val result = deleteGrocery(id)) {
                is AppResult.Success -> loadItems()
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }
}
