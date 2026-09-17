package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

private val PREVIEW_ITEMS = listOf(
    GroceryItem(id = 1L, name = "Coffee", description = "Whole bean, dark roast"),
    GroceryItem(id = 2L, name = "Milk", description = null),
    GroceryItem(id = 3L, name = "Bread", description = "Sourdough if they have it"),
)

@Preview(name = "Grocery — list")
@Composable
private fun GroceryListPreview() = PlannerTheme {
    GroceryListContent(
        state = GroceryListState(isLoading = false, items = PREVIEW_ITEMS),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Grocery — empty")
@Composable
private fun GroceryEmptyPreview() = PlannerTheme {
    GroceryListContent(
        state = GroceryListState(isLoading = false),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Grocery — editing, name invalid")
@Composable
private fun GroceryEditingPreview() = PlannerTheme {
    GroceryListContent(
        state = GroceryListState(
            isLoading = false,
            items = PREVIEW_ITEMS,
            editor = GroceryEditor(
                target = EditorTarget.Existing(2L),
                name = "",
                description = "Whole, not semi-skimmed",
                nameError = true,
            ),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** A completion in flight on one row: that row's control is disabled, the rest stay live. */
@Preview(name = "Grocery — completing one item")
@Composable
private fun GroceryCompletingPreview() = PlannerTheme {
    GroceryListContent(
        state = GroceryListState(
            isLoading = false,
            items = PREVIEW_ITEMS,
            completingItemIds = setOf(2L),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** The terminal branch: a failed load with nothing behind it earns a full screen and a retry. */
@Preview(name = "Grocery — load failed")
@Composable
private fun GroceryLoadFailedPreview() = PlannerTheme {
    GroceryListContent(
        state = GroceryListState(isLoading = false, loadFailed = true),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}
