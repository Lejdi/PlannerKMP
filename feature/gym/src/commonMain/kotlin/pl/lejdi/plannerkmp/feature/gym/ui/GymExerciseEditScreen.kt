package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.lejdi.plannerkmp.core.ui.CollectEffects
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.format.displayName
import pl.lejdi.plannerkmp.core.ui.format.shortDisplayName
import pl.lejdi.plannerkmp.core.ui.resources.core_action_cancel
import pl.lejdi.plannerkmp.core.ui.resources.core_action_delete
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.feature.gym.domain.GymField
import pl.lejdi.plannerkmp.feature.gym.resources.Res
import pl.lejdi.plannerkmp.feature.gym.resources.gym_delete_message
import pl.lejdi.plannerkmp.feature.gym.resources.gym_delete_title
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_delete_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_exercise_gone
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_load_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_name_blank
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_reps_invalid
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_save_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_sets_invalid
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_weight_field_invalid
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_comment
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_name
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_pick_day
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_reps
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_save
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_sets
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_title_add
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_title_edit
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_weekday
import pl.lejdi.plannerkmp.feature.gym.resources.gym_form_weight
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

@Composable
fun GymExerciseEditScreen(
    exerciseId: Long?,
    dayOfWeek: DayOfWeek,
    onNavigateBack: () -> Unit,
    viewModel: GymExerciseEditViewModel = koinViewModel {
        parametersOf(exerciseId, dayOfWeek)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is GymExerciseEditEffect.NavigateBack -> onNavigateBack()
        }
    }

    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(GymExerciseEditEvent.MessageShown) },
        actionLabel = stringResource(CoreRes.string.core_action_retry),
        onAction = { viewModel.onEvent(GymExerciseEditEvent.RetryClicked) }
            .takeIf { message?.value == GymExerciseEditMessage.LoadFailed },
        suppressed = state.hasTerminalLoadFailure,
    )

    GymExerciseEditContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

/**
 * The form, split into named sections rather than written as one tree — the same correction
 * `TaskEditContent` documents, for the same reason: detekt's `LongMethod` exempts `@Composable`, so
 * nothing objects to a deeply nested tree, and past a certain depth "which branch is this inside?"
 * stops being answerable by reading.
 */
@Composable
internal fun GymExerciseEditContent(
    state: GymExerciseEditState,
    snackbarHostState: SnackbarHostState,
    onEvent: (GymExerciseEditEvent) -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        // The middle arm matters: without it a failed load leaves a blank but fully live form that
        // still believes it is editing a real row, and a Save from there writes empty fields over
        // the stored exercise.
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.gym_error_load_failed),
            onRetry = { onEvent(GymExerciseEditEvent.RetryClicked) },
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The activity is edge-to-edge, so the window is not resized for the keyboard
                    // and nothing else applies the inset. Without this the IME covers the weight
                    // field and Save.
                    .imePadding()
                    .padding(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        // The fields scroll; the action row stays put. At a large font scale this
                        // form is taller than a phone screen, and `weight(1f)` alone would clip
                        // the overflow with no way to reach Save.
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(
                            if (state.isEditingExisting) {
                                Res.string.gym_form_title_edit
                            } else {
                                Res.string.gym_form_title_add
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    NameAndCommentFields(state.form, onEvent)
                    WeekdaySelector(state.form, onEvent)
                    NumberFields(state.form, onEvent)
                }
                ActionRow(state, onEvent)
            }

            if (state.form.activeDialog == GymExerciseEditDialog.ConfirmDelete) {
                ConfirmDeleteDialog(onEvent)
            }
        }
    }
}

@Composable
private fun NameAndCommentFields(
    form: GymExerciseForm,
    onEvent: (GymExerciseEditEvent) -> Unit,
) {
    val nameInvalid = GymField.Name in form
    OutlinedTextField(
        value = form.name,
        onValueChange = { onEvent(GymExerciseEditEvent.NameChanged(it)) },
        label = { Text(stringResource(Res.string.gym_form_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        isError = nameInvalid,
        supportingText = if (nameInvalid) {
            { Text(stringResource(Res.string.gym_error_name_blank)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    OutlinedTextField(
        value = form.comment,
        onValueChange = { onEvent(GymExerciseEditEvent.CommentChanged(it)) },
        label = { Text(stringResource(Res.string.gym_form_comment)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Which weekday the exercise sits on — and the only way to move it, since an exercise belongs to
 * exactly one day.
 *
 * Chips rather than a dropdown: seven options that all fit, and the same short weekday names the
 * list screen's peek row uses, so the two read as the same week.
 */
@Composable
private fun WeekdaySelector(form: GymExerciseForm, onEvent: (GymExerciseEditEvent) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = stringResource(Res.string.gym_form_weekday),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            DayOfWeek.entries.forEach { weekday ->
                // It opens nothing and types nothing — it is a choice — so it carries its own
                // description and button role rather than leaving a three-letter label to explain
                // itself to a screen reader.
                val label = stringResource(Res.string.gym_form_pick_day, weekday.displayName())
                FilterChip(
                    selected = form.dayOfWeek == weekday,
                    onClick = { onEvent(GymExerciseEditEvent.DayOfWeekChanged(weekday)) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Button
                            contentDescription = label
                        },
                    label = {
                        Text(
                            text = weekday.shortDisplayName(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Series, reps and weight.
 *
 * All three are text fields rather than steppers because a plan is typed once and then mostly read;
 * the weight is the one that changes often, and it has the inline editor on the list row for that.
 * The weight's error wording says what empty means, because empty is *valid* here and a field that
 * only says "invalid" would not tell the user that.
 */
@Composable
private fun NumberFields(form: GymExerciseForm, onEvent: (GymExerciseEditEvent) -> Unit) {
    val setsInvalid = GymField.Sets in form
    val repsInvalid = GymField.Reps in form
    val weightInvalid = GymField.Weight in form

    Spacer(modifier = Modifier.height(Spacing.sm))
    OutlinedTextField(
        value = form.setsCount,
        onValueChange = { onEvent(GymExerciseEditEvent.SetsCountChanged(it)) },
        label = { Text(stringResource(Res.string.gym_form_sets)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        isError = setsInvalid,
        supportingText = if (setsInvalid) {
            { Text(stringResource(Res.string.gym_error_sets_invalid)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    OutlinedTextField(
        value = form.repsPerSet,
        onValueChange = { onEvent(GymExerciseEditEvent.RepsPerSetChanged(it)) },
        label = { Text(stringResource(Res.string.gym_form_reps)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        isError = repsInvalid,
        supportingText = if (repsInvalid) {
            { Text(stringResource(Res.string.gym_error_reps_invalid)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    OutlinedTextField(
        value = form.weight,
        onValueChange = { onEvent(GymExerciseEditEvent.WeightChanged(it)) },
        label = { Text(stringResource(Res.string.gym_form_weight)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        isError = weightInvalid,
        supportingText = if (weightInvalid) {
            { Text(stringResource(Res.string.gym_error_weight_field_invalid)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActionRow(state: GymExerciseEditState, onEvent: (GymExerciseEditEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl),
        horizontalArrangement = if (state.isEditingExisting) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Center
        },
    ) {
        // Nothing to delete when the exercise has not been created yet.
        if (state.isEditingExisting) {
            Button(
                onClick = { onEvent(GymExerciseEditEvent.DeleteClicked) },
                // Disabled while a write is in flight: the ViewModel also refuses re-entry, but a
                // control that stays live through a slow write is what invites the second tap.
                enabled = state.canSubmit,
            ) {
                Text(stringResource(CoreRes.string.core_action_delete))
            }
        }
        Button(
            onClick = { onEvent(GymExerciseEditEvent.SaveClicked) },
            enabled = state.canSubmit,
        ) {
            Text(stringResource(Res.string.gym_form_save))
        }
    }
}

/**
 * Delete asks first, and says what goes with it.
 *
 * A confirmation rather than grocery's undo snackbar: removing an exercise from a plan is rare and
 * deliberate, not a tap made forty times a trip — the same judgement `:feature:routines` makes.
 */
@Composable
private fun ConfirmDeleteDialog(onEvent: (GymExerciseEditEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(GymExerciseEditEvent.DialogDismissed) },
        title = { Text(stringResource(Res.string.gym_delete_title)) },
        text = { Text(stringResource(Res.string.gym_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onEvent(GymExerciseEditEvent.DeleteConfirmed) }) {
                Text(stringResource(CoreRes.string.core_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(GymExerciseEditEvent.DialogDismissed) }) {
                Text(stringResource(CoreRes.string.core_action_cancel))
            }
        },
    )
}

private fun GymExerciseEditMessage.toStringResource() = when (this) {
    GymExerciseEditMessage.LoadFailed -> Res.string.gym_error_load_failed
    GymExerciseEditMessage.SaveFailed -> Res.string.gym_error_save_failed
    GymExerciseEditMessage.DeleteFailed -> Res.string.gym_error_delete_failed
    GymExerciseEditMessage.ExerciseNoLongerExists -> Res.string.gym_error_exercise_gone
}
