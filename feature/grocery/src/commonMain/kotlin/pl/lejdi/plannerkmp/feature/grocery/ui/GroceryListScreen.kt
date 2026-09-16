package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.core.ui.components.LoadingView
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

@Composable
fun GroceryListScreen(viewModel: GroceryListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GroceryListEffect.ShowError -> errorMessage = effect.message
            }
        }
    }

    Scaffold { padding ->
        if (state.isLoading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        errorMessage?.let { message ->
            ErrorView(
                message = message,
                modifier = Modifier.padding(padding),
                onRetry = { errorMessage = null },
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                items(state.items, key = { it.id }) { item ->
                    if (state.editingItemId == item.id) {
                        EditRow(state, viewModel)
                    } else {
                        GroceryRow(item, viewModel)
                    }
                }
                item { AddControl(state, viewModel) }
            }
        }
    }
}

@Composable
private fun AddControl(state: GroceryListState, viewModel: GroceryListViewModel) {
    Column(modifier = Modifier.padding(bottom = 16.dp).animateContentSize()) {
        if (state.isAddExpanded) {
            InlineEditCard(
                name = state.addName,
                onNameChange = { viewModel.onEvent(GroceryListEvent.AddNameChanged(it)) },
                nameError = state.addNameError,
                namePlaceholder = "Product name",
                description = state.addDescription,
                onDescriptionChange = { viewModel.onEvent(GroceryListEvent.AddDescriptionChanged(it)) },
                descriptionPlaceholder = "Additional information",
                onConfirm = { viewModel.onEvent(GroceryListEvent.AddConfirmClicked) },
                onCancel = { viewModel.onEvent(GroceryListEvent.AddCancelClicked) },
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingActionButton(
                    onClick = { viewModel.onEvent(GroceryListEvent.AddExpandClicked) },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun EditRow(state: GroceryListState, viewModel: GroceryListViewModel) {
    InlineEditCard(
        name = state.editName,
        onNameChange = { viewModel.onEvent(GroceryListEvent.EditNameChanged(it)) },
        nameError = state.editNameError,
        namePlaceholder = "Product name",
        description = state.editDescription,
        onDescriptionChange = { viewModel.onEvent(GroceryListEvent.EditDescriptionChanged(it)) },
        descriptionPlaceholder = "Additional information",
        onConfirm = { viewModel.onEvent(GroceryListEvent.EditConfirmClicked) },
        onCancel = { viewModel.onEvent(GroceryListEvent.EditCancelClicked) },
    )
}

@Composable
private fun InlineEditCard(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    namePlaceholder: String,
    description: String,
    onDescriptionChange: (String) -> Unit,
    descriptionPlaceholder: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.9f).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.defaultMinSize(minHeight = 84.dp).weight(1f).padding(end = 8.dp),
            ) {
                PlainTextField(
                    value = name,
                    onValueChange = onNameChange,
                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                    placeholder = namePlaceholder,
                    isError = nameError,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PlainTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    textStyle = LocalTextStyle.current,
                    placeholder = descriptionPlaceholder,
                    singleLine = false,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onConfirm) {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null)
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroceryRow(item: GroceryItem, viewModel: GroceryListViewModel) {
    Card(
        colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .animateContentSize()
            .combinedClickable(
                onLongClick = { viewModel.onEvent(GroceryListEvent.EditExpandClicked(item)) },
                onClick = {},
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.9f).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = item.name, style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold))
                item.description?.let { Text(text = it, modifier = Modifier.padding(top = 4.dp)) }
            }
            IconButton(onClick = { viewModel.onEvent(GroceryListEvent.CompleteItem(item.id)) }) {
                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    placeholder: String,
    singleLine: Boolean = true,
    isError: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = VisualTransformation.None,
        interactionSource = interactionSource,
        singleLine = singleLine,
        textStyle = if (isError) textStyle.copy(color = MaterialTheme.colorScheme.error) else textStyle,
    ) { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value = value,
            visualTransformation = VisualTransformation.None,
            innerTextField = innerTextField,
            singleLine = singleLine,
            enabled = true,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(1.dp),
            placeholder = { Text(text = placeholder, style = textStyle) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}
