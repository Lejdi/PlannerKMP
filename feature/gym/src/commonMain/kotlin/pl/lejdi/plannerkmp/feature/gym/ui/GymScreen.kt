package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.CollectEffects
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.components.PlainTextField
import pl.lejdi.plannerkmp.core.ui.format.displayName
import pl.lejdi.plannerkmp.core.ui.format.shortDisplayName
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.core.ui.theme.dayHeading
import pl.lejdi.plannerkmp.core.ui.theme.itemTitle
import pl.lejdi.plannerkmp.feature.gym.domain.DayExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymDay
import pl.lejdi.plannerkmp.feature.gym.resources.Res
import pl.lejdi.plannerkmp.feature.gym.resources.gym_add_exercise
import pl.lejdi.plannerkmp.feature.gym.resources.gym_day_has_exercises
import pl.lejdi.plannerkmp.feature.gym.resources.gym_edit_exercise
import pl.lejdi.plannerkmp.feature.gym.resources.gym_empty_day
import pl.lejdi.plannerkmp.feature.gym.resources.gym_empty_week
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_exercise_gone
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_load_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_toggle_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_weight_invalid
import pl.lejdi.plannerkmp.feature.gym.resources.gym_error_weight_save_failed
import pl.lejdi.plannerkmp.feature.gym.resources.gym_go_to_day
import pl.lejdi.plannerkmp.feature.gym.resources.gym_no_weight
import pl.lejdi.plannerkmp.feature.gym.resources.gym_serie_label
import pl.lejdi.plannerkmp.feature.gym.resources.gym_sets_by_reps
import pl.lejdi.plannerkmp.feature.gym.resources.gym_today
import pl.lejdi.plannerkmp.feature.gym.resources.gym_weight_field
import pl.lejdi.plannerkmp.feature.gym.resources.gym_weight_hint
import pl.lejdi.plannerkmp.feature.gym.resources.gym_weight_unit
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

/** Material's own disabled alpha: a done exercise keeps its place and stops asking for attention. */
private const val DONE_ALPHA = 0.38f

/** The dot under a peek-row chip for a day that has something planned. */
private val dayMarkerSize = 6.dp

@Composable
fun GymScreen(
    onNavigateToAddExercise: (DayOfWeek) -> Unit,
    onNavigateToEditExercise: (Long) -> Unit,
    viewModel: GymViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is GymEffect.NavigateToAddExercise -> onNavigateToAddExercise(effect.dayOfWeek)
            is GymEffect.NavigateToEditExercise -> onNavigateToEditExercise(effect.exerciseId)
        }
    }

    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(GymEvent.MessageShown) },
        actionLabel = stringResource(CoreRes.string.core_action_retry),
        // "Retry" retries: it re-subscribes to the week rather than only hiding the message.
        onAction = { viewModel.onEvent(GymEvent.RetryClicked) }
            .takeIf { message?.value == GymMessage.LoadFailed },
        suppressed = state.hasTerminalLoadFailure,
    )

    GymContent(state = state, snackbarHostState = snackbarHostState, onEvent = viewModel::onEvent)
}

/**
 * Stateless: it takes the state and a single event sink, never the ViewModel. That keeps it
 * previewable and testable, and stops leaf composables from depending on the whole screen's model.
 */
@Composable
internal fun GymContent(
    state: GymState,
    snackbarHostState: SnackbarHostState,
    onEvent: (GymEvent) -> Unit,
) {
    // One pager state, hoisted: the FAB adds to the weekday on screen, so it and the pager have to
    // be looking at the same one. Two `rememberPagerState` calls would compile and give the FAB a
    // pager that is never scrolled — it would always add to today.
    //
    // `initialPage` is only initial: `rememberPagerState` ignores later changes to it, which is
    // what should happen. The "Today" marker follows the date across midnight because it reads
    // `state.today`; the page does not, because yanking the page out from under someone mid-swipe
    // is not a feature.
    val pagerState = rememberPagerState(initialPage = state.today.ordinal) { DayOfWeek.entries.size }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Nothing to add to until the week has loaded.
            if (!state.isLoading && !state.hasTerminalLoadFailure) {
                FloatingActionButton(
                    // Reads the visible page inside `onClick`, never in the composable body:
                    // reading `currentPage` during composition subscribes the enclosing scope to
                    // it, so the whole screen would recompose on every settle — the trap
                    // `DashboardScreen` documents having been caught by. Inside the lambda it is
                    // read once, at the moment of the tap.
                    onClick = {
                        onEvent(GymEvent.AddExerciseClicked(DayOfWeek.entries[pagerState.currentPage]))
                    },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(Res.string.gym_add_exercise),
                    )
                }
            }
        },
    ) { padding ->
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.gym_error_load_failed),
            onRetry = { onEvent(GymEvent.RetryClicked) },
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            GymWeek(state = state, pagerState = pagerState, onEvent = onEvent)
        }
    }
}

/**
 * One page per weekday, seven of them, opening on today.
 *
 * Seven fixed pages rather than an endless wrap-around: the page index *is* the weekday ordinal,
 * which means no anchor arithmetic, nothing to re-anchor when the date rolls over, and a restored
 * page index that cannot come to mean a different day. Getting from Sunday to Monday is the peek
 * row's job, one tap.
 */
@Composable
private fun GymWeek(
    state: GymState,
    pagerState: PagerState,
    onEvent: (GymEvent) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        WeekdayPeekRow(
            days = state.days,
            today = state.today,
            selectedPage = pagerState.currentPage,
            // "Instantly", per the design: jumping six days should not mean watching six pages go
            // past. This is also the only way back from Sunday to Monday.
            onDaySelected = { ordinal -> scope.launch { pagerState.scrollToPage(ordinal) } },
        )
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val day = state.days.getOrNull(page) ?: return@HorizontalPager
            DayPage(
                day = day,
                isToday = day.dayOfWeek == state.today,
                // On a first run every page is bare, and "nothing planned for this day" seven
                // times says less than "nothing planned yet" once.
                isWeekEmpty = state.isEmpty,
                state = state,
                onEvent = onEvent,
            )
        }
    }
}

/**
 * Every weekday at a glance: which one is on screen, which one is today, and which ones hold
 * anything.
 */
@Composable
private fun WeekdayPeekRow(
    days: List<GymDay>,
    today: DayOfWeek,
    selectedPage: Int,
    onDaySelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DayOfWeek.entries.forEachIndexed { ordinal, weekday ->
            val hasExercises = days.getOrNull(ordinal)?.exercises?.isNotEmpty() == true
            val goToDay = stringResource(Res.string.gym_go_to_day, weekday.displayName())
            val marked = stringResource(Res.string.gym_day_has_exercises)
            // The dot is announced as part of the chip rather than as a focus stop of its own:
            // it is information about this day, not something to interact with.
            val chipDescription = if (hasExercises) "$goToDay, $marked" else goToDay
            FilterChip(
                selected = ordinal == selectedPage,
                onClick = { onDaySelected(ordinal) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.Button
                        contentDescription = chipDescription
                    },
                label = {
                    DayChipLabel(
                        weekday = weekday,
                        isToday = weekday == today,
                        hasExercises = hasExercises,
                    )
                },
            )
        }
    }
}

@Composable
private fun DayChipLabel(weekday: DayOfWeek, isToday: Boolean, hasExercises: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = weekday.shortDisplayName(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // A dot rather than a count: it is there to find leg day at a glance, and the number is on
        // the page itself. Described on the chip as a whole, so it is not a second focus stop.
        if (hasExercises) {
            Spacer(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(dayMarkerSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * `internal` rather than private so a `@Preview` can render one page directly.
 *
 * The pager always opens on today, so the state where a page shows a plan *without* checkboxes —
 * the one the "a tick belongs to today" rule produces, and the one no layout hints at — is
 * unreachable through [GymContent] in a preview. It is also the state most likely to regress.
 */
@Composable
internal fun DayPage(
    day: GymDay,
    isToday: Boolean,
    isWeekEmpty: Boolean,
    state: GymState,
    onEvent: (GymEvent) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        // One heading node, marked as a heading: that is what lets TalkBack and VoiceOver jump
        // between days instead of swiping through every card on the way.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(Spacing.sm)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = day.dayOfWeek.displayName(),
                style = MaterialTheme.typography.dayHeading,
                textAlign = TextAlign.Center,
            )
            if (isToday) {
                Text(
                    text = stringResource(Res.string.gym_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (day.exercises.isEmpty()) {
            Text(
                text = if (isWeekEmpty) {
                    stringResource(Res.string.gym_empty_week)
                } else {
                    stringResource(Res.string.gym_empty_day)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xl, start = Spacing.lg, end = Spacing.lg),
            )
            return@Column
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Scaffold reserves no room for its own FAB, so without this the last card of a full
            // day comes to rest underneath it.
            contentPadding = PaddingValues(bottom = Sizing.fabClearance),
        ) {
            items(day.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    // Only today's page can be ticked: a tick is stamped with today's date, so one
                    // made here on another weekday would show up on today's page instead.
                    isToday = isToday,
                    weightEditor = state.weightEditor?.takeIf { it.exerciseId == exercise.id },
                    canSubmit = !state.isPending(exercise.id),
                    onEvent = onEvent,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseCard(
    exercise: DayExercise,
    isToday: Boolean,
    weightEditor: WeightEditor?,
    canSubmit: Boolean,
    onEvent: (GymEvent) -> Unit,
) {
    val editLabel = stringResource(Res.string.gym_edit_exercise)
    Card(
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth(Sizing.CARD_WIDTH_FRACTION)
            // A long press opens the editor, and the icon below sends the same event: two
            // affordances for one intent, because a long-press-only card is undiscoverable.
            .combinedClickable(
                onClick = {},
                onLongClick = { onEvent(GymEvent.EditExerciseClicked(exercise.id)) },
                onLongClickLabel = editLabel,
            )
            .semantics { role = Role.Button },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                // Done exercises stay exactly where they are and stop asking for attention.
                .alpha(if (exercise.isDone) DONE_ALPHA else 1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = exercise.exercise.name, style = MaterialTheme.typography.itemTitle)
                    exercise.exercise.comment?.let {
                        Text(text = it, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                IconButton(onClick = { onEvent(GymEvent.EditExerciseClicked(exercise.id)) }) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = editLabel)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        Res.string.gym_sets_by_reps,
                        exercise.setsCount,
                        exercise.exercise.repsPerSet,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                WeightControl(
                    exercise = exercise,
                    editor = weightEditor,
                    onEvent = onEvent,
                )
            }
            if (isToday) {
                SerieCheckboxes(exercise = exercise, canSubmit = canSubmit, onEvent = onEvent)
            }
        }
    }
}

/**
 * The weight, and the inline editor it turns into.
 *
 * Tapping it is the whole point of the feature's "make the weight easy to change" requirement: no
 * navigation, no form, type a number and it is written.
 *
 * **The keyboard's Done action is the way out.** Committing on lost focus is a second path, not the
 * main one: on a touch screen a tap on empty space or on a card does not move focus — verified on
 * device — so it fires only when focus genuinely goes somewhere else, such as another row's weight
 * field. Claiming "tap away to save" here would describe a gesture Android does not deliver.
 */
@Composable
private fun WeightControl(
    exercise: DayExercise,
    editor: WeightEditor?,
    onEvent: (GymEvent) -> Unit,
) {
    if (editor != null) {
        val focusRequester = remember { FocusRequester() }
        // Seeded once per opening, with the caret *after* the number rather than before it. The
        // field opens on a weight that already exists, so typing means appending or replacing a
        // digit — never prepending, which is what Compose's String-based field does by default
        // (its selection starts at 0 and the String API cannot move it). The ViewModel still owns
        // the text; this holds the cursor, which is the field's own business.
        var fieldValue by remember(editor.exerciseId) {
            mutableStateOf(TextFieldValue(editor.text, TextRange(editor.text.length)))
        }
        // The editor exists *because* the user tapped the weight, so the field takes focus itself
        // and raises the keyboard. Without this the tap only swapped a text for an empty-looking
        // field and the user had to tap again to type — which is not "very easy".
        LaunchedEffect(editor.exerciseId) { focusRequester.requestFocus() }
        PlainTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onEvent(GymEvent.WeightTextChanged(it.text))
            },
            placeholder = stringResource(Res.string.gym_weight_hint),
            textStyle = MaterialTheme.typography.bodyMedium,
            isError = editor.isInvalid,
            modifier = Modifier
                .width(Sizing.intervalFieldWidth)
                .focusRequester(focusRequester)
                // Forwards the fact only. Whether losing focus means the user left the field is a
                // rule, and it lives in the ViewModel — see GymViewModel.onWeightEditorFocusChanged
                // for what went wrong when this composable decided it.
                .onFocusChanged { onEvent(GymEvent.WeightEditorFocusChanged(it.isFocused)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onEvent(GymEvent.WeightEditCommitted) }),
        )
        return
    }
    val weightLabel = stringResource(Res.string.gym_weight_field)
    val weightText = exercise.exercise.weight.toWeightText()
    Text(
        text = if (weightText.isEmpty()) {
            stringResource(Res.string.gym_no_weight)
        } else {
            "$weightText ${stringResource(Res.string.gym_weight_unit)}"
        },
        style = MaterialTheme.typography.itemTitle,
        modifier = Modifier
            // Its own description and role: a tap here opens an editor rather than following the
            // card's own action, and the number alone says nothing about what it is.
            .semantics {
                role = Role.Button
                contentDescription = weightLabel
            }
            .combinedClickable(
                onClick = { onEvent(GymEvent.WeightEditStarted(exercise.id)) },
                onClickLabel = weightLabel,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/**
 * One checkbox per series, wrapping onto as many lines as it takes.
 *
 * Each box is checked when its 1-based number is within [DayExercise.doneSets] — completion is a
 * count, so "two done" is the first two boxes. Tapping one sends its number and
 * `ToggleExerciseSet` decides what that means.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SerieCheckboxes(
    exercise: DayExercise,
    canSubmit: Boolean,
    onEvent: (GymEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(exercise.setsCount) { index ->
            val serieNumber = index + 1
            val label = stringResource(Res.string.gym_serie_label, serieNumber)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                Checkbox(
                    checked = serieNumber <= exercise.doneSets,
                    onCheckedChange = { onEvent(GymEvent.SerieToggled(exercise.id, serieNumber)) },
                    // Stays disabled until the write comes back: a live box over an exercise whose
                    // count is already being written is what lets a double tap count twice.
                    enabled = canSubmit,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }
}

private fun GymMessage.toStringResource() = when (this) {
    GymMessage.LoadFailed -> Res.string.gym_error_load_failed
    GymMessage.ToggleFailed -> Res.string.gym_error_toggle_failed
    GymMessage.WeightSaveFailed -> Res.string.gym_error_weight_save_failed
    GymMessage.WeightInvalid -> Res.string.gym_error_weight_invalid
    GymMessage.ExerciseNoLongerExists -> Res.string.gym_error_exercise_gone
}
