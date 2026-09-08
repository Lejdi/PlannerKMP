package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.core.ui.components.LoadingView
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

@Composable
fun GroceryListScreen(viewModel: GroceryListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(GroceryListEvent.AddExpandClicked) }) {
                Text("+")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isAddExpanded) {
                item { AddRow(state, viewModel) }
            }
            items(state.items, key = { it.id }) { item ->
                if (state.editingItemId == item.id) {
                    EditRow(state, viewModel)
                } else {
                    GroceryRow(item, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AddRow(state: GroceryListState, viewModel: GroceryListViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = state.addName,
                onValueChange = { viewModel.onEvent(GroceryListEvent.AddNameChanged(it)) },
                label = { Text("Name") },
                isError = state.addNameError,
            )
            OutlinedTextField(
                value = state.addDescription,
                onValueChange = { viewModel.onEvent(GroceryListEvent.AddDescriptionChanged(it)) },
                label = { Text("Description") },
            )
            Row {
                Button(onClick = { viewModel.onEvent(GroceryListEvent.AddConfirmClicked) }) { Text("Add") }
                Button(onClick = { viewModel.onEvent(GroceryListEvent.AddCancelClicked) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun EditRow(state: GroceryListState, viewModel: GroceryListViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = state.editName,
                onValueChange = { viewModel.onEvent(GroceryListEvent.EditNameChanged(it)) },
                label = { Text("Name") },
                isError = state.editNameError,
            )
            OutlinedTextField(
                value = state.editDescription,
                onValueChange = { viewModel.onEvent(GroceryListEvent.EditDescriptionChanged(it)) },
                label = { Text("Description") },
            )
            Row {
                Button(onClick = { viewModel.onEvent(GroceryListEvent.EditConfirmClicked) }) { Text("Save") }
                Button(onClick = { viewModel.onEvent(GroceryListEvent.EditCancelClicked) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun GroceryRow(item: GroceryItem, viewModel: GroceryListViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .pointerInput(item.id) {
                detectTapGestures(
                    onLongPress = { viewModel.onEvent(GroceryListEvent.EditExpandClicked(item)) },
                    onTap = { viewModel.onEvent(GroceryListEvent.CompleteItem(item.id)) },
                )
            },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.name)
            item.description?.let { Text(it) }
        }
    }
}
