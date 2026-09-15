package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    task: Task?,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = koinViewModel { parametersOf(task) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is TaskEditEffect.NavigateBack -> onNavigateBack()
            is TaskEditEffect.ShowError -> errorMessage = effect.message
        }
    }

    Scaffold { padding ->
        errorMessage?.let { message ->
            // The form's own values live in the ViewModel's state, so dismissing
            // brings the user back to exactly what they were editing.
            ErrorView(
                message = message,
                modifier = Modifier.padding(padding),
                onRetry = { errorMessage = null },
            )
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onEvent(TaskEditEvent.NameChanged(it)) },
                label = { Text("Name") },
                isError = state.nameError,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onEvent(TaskEditEvent.DescriptionChanged(it)) },
                label = { Text("Description") },
            )

            Row {
                TaskType.entries.forEach { type ->
                    Row {
                        RadioButton(
                            selected = state.type == type,
                            onClick = { viewModel.onEvent(TaskEditEvent.TypeChanged(type)) },
                        )
                        Text(type.name)
                    }
                }
            }

            if (state.type != TaskType.Asap) {
                OutlinedTextField(
                    value = state.startDate?.toString().orEmpty(),
                    onValueChange = {},
                    label = { Text("Start date (dd-MM-yyyy)") },
                    readOnly = true,
                )
            }
            if (state.type == TaskType.Periodic) {
                OutlinedTextField(
                    value = state.endDate?.toString().orEmpty(),
                    onValueChange = {},
                    label = { Text("End date (dd-MM-yyyy)") },
                    readOnly = true,
                )
                OutlinedTextField(
                    value = state.daysInterval,
                    onValueChange = { viewModel.onEvent(TaskEditEvent.DaysIntervalChanged(it)) },
                    label = { Text("Repeat every (days)") },
                )
            }
            if (state.type != TaskType.Asap) {
                Box {
                    OutlinedTextField(
                        value = state.hour?.toString().orEmpty(),
                        onValueChange = {},
                        label = { Text("Time (optional)") },
                        readOnly = true,
                    )
                    // OutlinedTextField consumes clicks itself even when readOnly, so an
                    // overlay is needed to turn the field into a tap target for the dialog.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true },
                    )
                }
            }

            Row {
                Button(onClick = { viewModel.onEvent(TaskEditEvent.SaveClicked) }) { Text("Save") }
                Button(onClick = { viewModel.onEvent(TaskEditEvent.DeleteClicked) }) { Text("Delete") }
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = state.hour?.hour ?: 12,
                initialMinute = state.hour?.minute ?: 0,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    Button(onClick = {
                        viewModel.onEvent(
                            TaskEditEvent.HourChanged(LocalTime(timePickerState.hour, timePickerState.minute)),
                        )
                        showTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    Button(onClick = { showTimePicker = false }) { Text("Cancel") }
                },
                text = { TimePicker(state = timePickerState) },
            )
        }
    }
}
