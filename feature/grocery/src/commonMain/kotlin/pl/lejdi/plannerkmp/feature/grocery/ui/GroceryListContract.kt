package pl.lejdi.plannerkmp.feature.grocery.ui

import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

data class GroceryListState(
    val isLoading: Boolean = true,
    val items: List<GroceryItem> = emptyList(),
    val isAddExpanded: Boolean = false,
    val addName: String = "",
    val addDescription: String = "",
    val addNameError: Boolean = false,
    val editingItemId: Long? = null,
    val editName: String = "",
    val editDescription: String = "",
    val editNameError: Boolean = false,
) : MviState

sealed interface GroceryListEvent : MviEvent {
    data object AddExpandClicked : GroceryListEvent
    data class AddNameChanged(val value: String) : GroceryListEvent
    data class AddDescriptionChanged(val value: String) : GroceryListEvent
    data object AddConfirmClicked : GroceryListEvent
    data object AddCancelClicked : GroceryListEvent
    data class EditExpandClicked(val item: GroceryItem) : GroceryListEvent
    data class EditNameChanged(val value: String) : GroceryListEvent
    data class EditDescriptionChanged(val value: String) : GroceryListEvent
    data object EditConfirmClicked : GroceryListEvent
    data object EditCancelClicked : GroceryListEvent
    data class CompleteItem(val id: Long) : GroceryListEvent
}

sealed interface GroceryListEffect : MviEffect {
    data class ShowError(val message: String) : GroceryListEffect
}
