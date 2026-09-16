package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TaskEditScreen(
    task: Task?,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = koinViewModel { parametersOf(task) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is TaskEditEffect.NavigateBack -> onNavigateBack()
            is TaskEditEffect.ShowError -> errorMessage = effect.message
        }
    }

    // Only a newly-added task morphs in from the Dashboard's FAB - editing an existing task
    // is opened from its TaskCard, so it gets Nav3's plain scene transition instead.
    val screenModifier = if (task == null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(AddTaskSharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(500) },
            )
        }
    } else {
        Modifier
    }

    Scaffold(modifier = screenModifier) { padding ->
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
                .padding(top = padding.calculateTopPadding())
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(TaskEditEvent.NameChanged(it)) },
                    label = { Text("Task name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    isError = state.nameError,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.onEvent(TaskEditEvent.DescriptionChanged(it)) },
                    label = { Text("Task description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TaskType.entries.forEach { type ->
                        val isSelected = state.type == type
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = { viewModel.onEvent(TaskEditEvent.TypeChanged(type)) },
                                ),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.onEvent(TaskEditEvent.TypeChanged(type)) },
                            )
                            Text(text = type.displayLabel(), modifier = Modifier.padding(start = 8.dp))
                        }
                        if (isSelected && type == TaskType.Periodic) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
                            ) {
                                Text("Repeat every")
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = state.daysInterval,
                                    onValueChange = { viewModel.onEvent(TaskEditEvent.DaysIntervalChanged(it)) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    modifier = Modifier.width(64.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("days")
                            }
                        }
                    }
                }

                if (state.type != TaskType.Asap) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TapToOpenField(
                            value = state.startDate?.toEditFieldDisplayString().orEmpty(),
                            label = if (state.type == TaskType.OneTime) "Date" else "Start date",
                            width = 150.dp,
                            onClick = { showStartDatePicker = true },
                        )
                        TapToOpenField(
                            value = state.hour?.toEditFieldDisplayString().orEmpty(),
                            label = "Hour",
                            width = 100.dp,
                            onClick = { showTimePicker = true },
                        )
                    }
                }
                if (state.type == TaskType.Periodic) {
                    TapToOpenField(
                        value = state.endDate?.toEditFieldDisplayString().orEmpty(),
                        label = "End date",
                        width = 150.dp,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = { showEndDatePicker = true },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = { viewModel.onEvent(TaskEditEvent.DeleteClicked) }) { Text("Delete") }
                Button(onClick = { viewModel.onEvent(TaskEditEvent.SaveClicked) }) { Text("Save") }
            }
        }

        if (showStartDatePicker) {
            DateDialog(
                initialDate = state.startDate,
                onDismiss = { showStartDatePicker = false },
                onConfirm = {
                    viewModel.onEvent(TaskEditEvent.StartDateChanged(it))
                    showStartDatePicker = false
                },
            )
        }
        if (showEndDatePicker) {
            DateDialog(
                initialDate = state.endDate,
                selectableDates = state.startDate?.let { startDate ->
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            utcTimeMillis >= startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                    }
                } ?: DatePickerDefaults.AllDates,
                onDismiss = { showEndDatePicker = false },
                onConfirm = {
                    viewModel.onEvent(TaskEditEvent.EndDateChanged(it))
                    showEndDatePicker = false
                },
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TapToOpenField(
    value: String,
    label: String,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.width(width),
        )
        Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

private fun TaskType.displayLabel(): String = when (this) {
    TaskType.Asap -> "ASAP"
    TaskType.OneTime -> "Specific day"
    TaskType.Periodic -> "Periodic"
}

