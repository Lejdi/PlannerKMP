package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.navigation.NavAnimation
import pl.lejdi.plannerkmp.core.ui.CollectEffects
import pl.lejdi.plannerkmp.core.ui.components.FieldError
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.format.toFieldString
import pl.lejdi.plannerkmp.core.ui.format.toPickerDate
import pl.lejdi.plannerkmp.core.ui.format.toPickerMillis
import pl.lejdi.plannerkmp.core.ui.resources.core_action_cancel
import pl.lejdi.plannerkmp.core.ui.resources.core_action_delete
import pl.lejdi.plannerkmp.core.ui.resources.core_action_ok
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskField
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import pl.lejdi.plannerkmp.feature.tasks.resources.Res
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_a11y_action_choose_date
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_a11y_action_choose_time
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_a11y_not_set
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_a11y_pick_date
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_a11y_pick_time
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_action_save
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_delete_confirm_body
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_delete_confirm_title
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_delete_failed
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_end_before_start
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_interval_invalid
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_load_failed
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_name_blank
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_save_failed
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_task_gone
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_date
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_description
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_end_date
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_hour
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_name
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_field_start_date
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_repeat_days
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_repeat_every
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_type_asap
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_type_one_time
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_type_periodic
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TaskEditScreen(
    taskId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = koinViewModel { parametersOf(taskId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is TaskEditEffect.NavigateBack -> onNavigateBack()
        }
    }

    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(TaskEditEvent.MessageShown) },
        actionLabel = stringResource(CoreRes.string.core_action_retry),
        onAction = { viewModel.onEvent(TaskEditEvent.RetryClicked) }
            .takeIf { message?.value == TaskEditMessage.LoadFailed },
        suppressed = state.hasTerminalLoadFailure,
    )

    // Only a newly-added task morphs in from the Dashboard's FAB - editing an existing task
    // is opened from its TaskCard, so it gets Nav3's plain scene transition instead.
    val screenModifier = if (taskId == null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(ADD_TASK_SHARED_KEY),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = NavAnimation.boundsTransform,
            )
        }
    } else {
        Modifier
    }

    TaskEditContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        modifier = screenModifier,
    )
}

/**
 * The form.
 *
 * Split into named sections rather than written as one tree. It was a single 188-line composable
 * nested ten levels deep — and detekt's `LongMethod` exempts `@Composable`, on the reasonable theory
 * that a declarative tree's length is layout rather than complexity, so nothing was ever going to
 * object. That theory holds for a flat tree; at this depth "which `if` is this arm inside?" had
 * stopped being answerable by reading. Each section below is one question the form asks.
 */
@Composable
internal fun TaskEditContent(
    state: TaskEditState,
    snackbarHostState: SnackbarHostState,
    onEvent: (TaskEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // The middle arm is the one this screen used to be missing: a failed load left a blank but
        // fully live form that still believed it was editing a real row, so a Save from it wrote
        // empty fields over the stored task.
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.tasks_error_load_failed),
            onRetry = { onEvent(TaskEditEvent.RetryClicked) },
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The activity is edge-to-edge, so the window is not resized for the keyboard
                    // and nothing else applies the inset. Without this the IME covers the bottom of
                    // a form that cannot scroll, which on a periodic task is the interval field, the
                    // end date and Save.
                    .imePadding()
                    .padding(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        // The fields scroll; the action row stays put. A periodic form is already
                        // taller than a phone screen at a large font scale, and `weight(1f)` only
                        // clipped the overflow — there was no way to reach Save at all.
                        .verticalScroll(rememberScrollState()),
                ) {
                    NameAndDescriptionFields(state.form, onEvent)
                    TypeSelector(state.form, onEvent)
                    ScheduleFields(state, onEvent)
                }
                ActionRow(state, onEvent)
            }

            TaskEditDialogs(state, onEvent)
        }
    }
}

@Composable
private fun NameAndDescriptionFields(form: TaskForm, onEvent: (TaskEditEvent) -> Unit) {
    val nameInvalid = TaskField.Name in form
    OutlinedTextField(
        value = form.name,
        onValueChange = { onEvent(TaskEditEvent.NameChanged(it)) },
        label = { Text(stringResource(Res.string.tasks_field_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        isError = nameInvalid,
        supportingText = if (nameInvalid) {
            { Text(stringResource(Res.string.tasks_error_name_blank)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    OutlinedTextField(
        value = form.description,
        onValueChange = { onEvent(TaskEditEvent.DescriptionChanged(it)) },
        label = { Text(stringResource(Res.string.tasks_field_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeSelector(form: TaskForm, onEvent: (TaskEditEvent) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        TaskType.entries.forEach { type ->
            val isSelected = form.type == type
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Material's RadioButton applies minimumInteractiveComponentSize() only when it
                    // owns the click; here the row does, so the button is a bare 24dp canvas and the
                    // row inherited that height. Three schedule choices, each with a target half the
                    // minimum and 24dp between their centres.
                    .heightIn(min = Sizing.minimumTouchTarget)
                    // The whole row is the radio button, so the reader announces one control
                    // rather than a button next to an unrelated label.
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onEvent(TaskEditEvent.TypeChanged(type)) },
                    ),
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = stringResource(type.displayLabelResource()),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (isSelected && type == TaskType.Periodic) {
                IntervalField(form, onEvent)
            }
        }
    }
}

@Composable
private fun IntervalField(form: TaskForm, onEvent: (TaskEditEvent) -> Unit) {
    val invalid = TaskField.DaysInterval in form
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = Spacing.nestedIndent, bottom = Spacing.xs),
    ) {
        Text(stringResource(Res.string.tasks_repeat_every))
        Spacer(modifier = Modifier.width(Spacing.xs))
        OutlinedTextField(
            value = form.daysInterval,
            onValueChange = { onEvent(TaskEditEvent.DaysIntervalChanged(it)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            isError = invalid,
            modifier = Modifier.width(Sizing.intervalFieldWidth),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        // A plural, not a fixed "days": an interval of 1 read "Repeat every 1 days", and a language
        // with more than two forms could not be expressed at all.
        Text(pluralStringResource(Res.plurals.tasks_repeat_days, form.daysInterval.toIntOrNull() ?: 0))
    }
    if (invalid) {
        FieldError(
            text = stringResource(Res.string.tasks_error_interval_invalid),
            modifier = Modifier.padding(start = Spacing.nestedIndent, bottom = Spacing.xs),
        )
    }
}

@Composable
private fun ColumnScope.ScheduleFields(state: TaskEditState, onEvent: (TaskEditEvent) -> Unit) {
    val form = state.form
    if (form.type != TaskType.Asap) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val startLabel = stringResource(
                if (form.type == TaskType.OneTime) {
                    Res.string.tasks_field_date
                } else {
                    Res.string.tasks_field_start_date
                },
            )
            TapToOpenField(
                value = state.effectiveStartDate.toFieldString(),
                label = startLabel,
                accessibilityHint = stringResource(
                    Res.string.tasks_a11y_pick_date,
                    startLabel,
                    state.effectiveStartDate.toFieldString(),
                ),
                accessibilityAction = stringResource(Res.string.tasks_a11y_action_choose_date),
                width = Sizing.dateFieldWidth,
                onClick = { onEvent(TaskEditEvent.DialogRequested(TaskEditDialog.StartDate)) },
            )
            val hourLabel = stringResource(Res.string.tasks_field_hour)
            TapToOpenField(
                value = form.hour?.toFieldString().orEmpty(),
                label = hourLabel,
                accessibilityHint = stringResource(
                    Res.string.tasks_a11y_pick_time,
                    hourLabel,
                    form.hour?.toFieldString() ?: stringResource(Res.string.tasks_a11y_not_set),
                ),
                accessibilityAction = stringResource(Res.string.tasks_a11y_action_choose_time),
                width = Sizing.timeFieldWidth,
                onClick = { onEvent(TaskEditEvent.DialogRequested(TaskEditDialog.Hour)) },
            )
        }
    }
    if (form.type == TaskType.Periodic) {
        val endLabel = stringResource(Res.string.tasks_field_end_date)
        TapToOpenField(
            value = form.endDate?.toFieldString().orEmpty(),
            label = endLabel,
            accessibilityHint = stringResource(
                Res.string.tasks_a11y_pick_date,
                endLabel,
                form.endDate?.toFieldString() ?: stringResource(Res.string.tasks_a11y_not_set),
            ),
            accessibilityAction = stringResource(Res.string.tasks_a11y_action_choose_date),
            width = Sizing.dateFieldWidth,
            isError = TaskField.EndDate in form,
            modifier = Modifier.padding(top = Spacing.sm),
            onClick = { onEvent(TaskEditEvent.DialogRequested(TaskEditDialog.EndDate)) },
        )
        if (TaskField.EndDate in form) {
            FieldError(text = stringResource(Res.string.tasks_error_end_before_start))
        }
    }
}

@Composable
private fun ActionRow(state: TaskEditState, onEvent: (TaskEditEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl),
        horizontalArrangement = if (state.isEditingExistingTask) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Center
        },
    ) {
        // Nothing to delete when the task has not been created yet.
        if (state.isEditingExistingTask) {
            Button(
                onClick = { onEvent(TaskEditEvent.DeleteClicked) },
                // Disabled while a write is in flight: the ViewModel also refuses re-entry, but
                // a control that stays live through a slow write is what invites the second tap.
                enabled = state.canSubmit,
            ) {
                Text(stringResource(CoreRes.string.core_action_delete))
            }
        }
        Button(
            onClick = { onEvent(TaskEditEvent.SaveClicked) },
            enabled = state.canSubmit,
        ) {
            Text(stringResource(Res.string.tasks_action_save))
        }
    }
}

@Composable
private fun TaskEditDialogs(state: TaskEditState, onEvent: (TaskEditEvent) -> Unit) {
    when (state.form.activeDialog) {
        TaskEditDialog.StartDate -> DateDialog(
            initialDate = state.effectiveStartDate,
            onDismiss = { onEvent(TaskEditEvent.DialogDismissed) },
            onConfirm = { onEvent(TaskEditEvent.StartDateChanged(it)) },
        )
        TaskEditDialog.EndDate -> DateDialog(
            initialDate = state.form.endDate,
            selectableDates = NotBefore(state.effectiveStartDate),
            onDismiss = { onEvent(TaskEditEvent.DialogDismissed) },
            onConfirm = { onEvent(TaskEditEvent.EndDateChanged(it)) },
        )
        TaskEditDialog.Hour -> TimeDialog(
            initialTime = state.form.hour,
            onDismiss = { onEvent(TaskEditEvent.DialogDismissed) },
            onConfirm = { onEvent(TaskEditEvent.HourChanged(it)) },
        )
        TaskEditDialog.ConfirmDelete -> ConfirmDeleteDialog(
            onDismiss = { onEvent(TaskEditEvent.DialogDismissed) },
            onConfirm = { onEvent(TaskEditEvent.DeleteConfirmed) },
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.tasks_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.tasks_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(CoreRes.string.core_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.core_action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private class NotBefore(private val startDate: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis >= startDate.toPickerMillis()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    selectableDates: SelectableDates = DatePickerDefaults.AllDates,
) {
    // rememberDatePickerState is rememberSaveable-backed, so the part-made selection survives
    // rotation and process death alongside the `activeDialog` flag that reopens this.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toPickerMillis(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onConfirm(millis.toPickerDate())
                }
                onDismiss()
            }) { Text(stringResource(CoreRes.string.core_action_ok)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(CoreRes.string.core_action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: DEFAULT_PICKER_HOUR,
        initialMinute = initialTime?.minute ?: 0,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm(LocalTime(timePickerState.hour, timePickerState.minute))
                onDismiss()
            }) { Text(stringResource(CoreRes.string.core_action_ok)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(CoreRes.string.core_action_cancel)) }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

/**
 * A read-only field that opens a picker instead of a keyboard.
 *
 * The text field is `enabled = false` so that tapping it cannot raise the IME, with the real tap
 * target laid over the top. That works visually and used to be invisible to assistive technology:
 * a screen reader found a *disabled* text field, which it announces as unavailable and skips, and
 * an unlabelled `Box` with a click listener and no role. So the field's own semantics are cleared
 * and the overlay carries the whole control — its description, its role, and the fact that
 * activating it opens a picker.
 */
@Composable
private fun TapToOpenField(
    value: String,
    label: String,
    accessibilityHint: String,
    accessibilityAction: String,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            // Material3 resolves disabled colors ahead of error ones, so the error state has to be
            // expressed through the disabled colors themselves.
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
                disabledLabelColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
            modifier = Modifier
                .width(width)
                // Purely decorative now: the overlay below is the control.
                .clearAndSetSemantics { },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = accessibilityHint
                    role = Role.Button
                    // The label describes the action; the service supplies the gesture for it.
                    onClick(label = accessibilityAction, action = null)
                },
        )
    }
}

private const val DEFAULT_PICKER_HOUR = 12

private fun TaskType.displayLabelResource() = when (this) {
    TaskType.Asap -> Res.string.tasks_type_asap
    TaskType.OneTime -> Res.string.tasks_type_one_time
    TaskType.Periodic -> Res.string.tasks_type_periodic
}

private fun TaskEditMessage.toStringResource() = when (this) {
    TaskEditMessage.LoadFailed -> Res.string.tasks_error_load_failed
    TaskEditMessage.SaveFailed -> Res.string.tasks_error_save_failed
    TaskEditMessage.DeleteFailed -> Res.string.tasks_error_delete_failed
    TaskEditMessage.TaskNoLongerExists -> Res.string.tasks_error_task_gone
}
