package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.navigation.NavAnimation
import pl.lejdi.plannerkmp.core.ui.CollectEffects
import pl.lejdi.plannerkmp.core.ui.components.LoadableContent
import pl.lejdi.plannerkmp.core.ui.components.MessageHost
import pl.lejdi.plannerkmp.core.ui.format.toFieldString
import pl.lejdi.plannerkmp.core.ui.resources.core_action_retry
import pl.lejdi.plannerkmp.core.ui.theme.Sizing
import pl.lejdi.plannerkmp.core.ui.theme.Spacing
import pl.lejdi.plannerkmp.core.ui.theme.dayHeading
import pl.lejdi.plannerkmp.core.ui.theme.itemTitle
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.resources.Res
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_add_task
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_complete_task
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_edit_task
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_empty_day
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_complete_failed
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_error_load_failed
import pl.lejdi.plannerkmp.core.ui.resources.Res as CoreRes

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DashboardScreen(
    onNavigateToAddTask: () -> Unit,
    onNavigateToEditTask: (Long) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAddTask -> onNavigateToAddTask()
            is DashboardEffect.NavigateToEditTask -> onNavigateToEditTask(effect.taskId)
        }
    }

    // The message is state, so it survives rotation and is cleared through an event rather than by
    // mutating a second copy of it held in the composition. MessageHost owns the snackbar
    // sequencing, which all three screens used to spell out for themselves.
    val message = state.message
    MessageHost(
        snackbarHostState = snackbarHostState,
        message = message,
        messageText = message?.let { stringResource(it.value.toStringResource()) },
        onShown = { viewModel.onEvent(DashboardEvent.MessageShown) },
        actionLabel = stringResource(CoreRes.string.core_action_retry),
        // "Retry" retries: it re-subscribes to the task flow rather than only hiding the message.
        onAction = { viewModel.onEvent(DashboardEvent.RetryClicked) }
            .takeIf { message?.value == DashboardMessage.LoadFailed },
        suppressed = state.hasTerminalLoadFailure,
    )

    DashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        fabModifier = with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(ADD_TASK_SHARED_KEY),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = NavAnimation.boundsTransform,
            )
        },
    )
}

/**
 * Stateless: it takes the state and a single event sink, never the ViewModel. That keeps it
 * previewable and testable, and stops leaf composables from depending on the whole screen's model.
 */
@Composable
internal fun DashboardContent(
    state: DashboardState,
    snackbarHostState: SnackbarHostState,
    onEvent: (DashboardEvent) -> Unit,
    fabModifier: Modifier = Modifier,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(DashboardEvent.AddTaskClicked) },
                modifier = fabModifier,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.tasks_add_task))
            }
        },
    ) { padding ->
        LoadableContent(
            isLoading = state.isLoading,
            hasTerminalLoadFailure = state.hasTerminalLoadFailure,
            errorMessage = stringResource(Res.string.tasks_error_load_failed),
            onRetry = { onEvent(DashboardEvent.RetryClicked) },
            // Background first, insets second: the colour fills the window edge to edge while the
            // content stays clear of the system bars, which is what the hand-rolled
            // `padding(top = …)` was reaching for while dropping the other three edges.
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val pagerState = rememberPagerState { state.days.size }
                // Changes only, not the first composition.
                //
                // A LaunchedEffect runs its block when it enters composition, so keying it on
                // `currentPage` dismissed the revealed actions on the first frame — which is the
                // frame that had just restored `revealedTaskId` from saved state. The one thing
                // this screen persists was wiped every rotation and every return from the editor.
                // Reading `currentPage` in the composable body also subscribed this scope to it, so
                // the pager's parent recomposed on every settle.
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .drop(1)
                        .collect { onEvent(DashboardEvent.DismissActions) }
                }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = Spacing.xxl),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    DayColumn(
                        day = state.days[page],
                        revealedTaskId = state.revealedTaskId,
                        completingTaskIds = state.completingTaskIds,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: DashboardDay,
    revealedTaskId: Long?,
    completingTaskIds: Set<Long>,
    onEvent: (DashboardEvent) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        // One heading node, two lines. Marking it as a heading is what lets TalkBack and VoiceOver
        // jump between days; without it, reaching the fifth day meant swiping through every card
        // before it.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(Spacing.sm)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = day.date.toDateLine(),
                style = MaterialTheme.typography.dayHeading,
                textAlign = TextAlign.Center,
            )
            Text(
                text = day.date.toWeekdayLine(),
                style = MaterialTheme.typography.dayHeading,
                textAlign = TextAlign.Center,
            )
        }
        if (day.tasks.isEmpty()) {
            Text(
                text = stringResource(Res.string.tasks_empty_day),
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
            // day comes to rest underneath it with its actions unreachable.
            contentPadding = PaddingValues(bottom = Sizing.fabClearance),
        ) {
            items(day.tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    // The column's own date, not the task's: a periodic task appears on several
                    // pages, and completing it has to mean "this occurrence".
                    onDate = day.date,
                    revealed = task.id == revealedTaskId,
                    canSubmit = task.id !in completingTaskIds,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onDate: LocalDate,
    revealed: Boolean,
    canSubmit: Boolean,
    onEvent: (DashboardEvent) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        // Material's clickable Surface sets no semantic role by default, so this card — the only
        // way to reach Edit and Complete — reached a screen reader as an unnamed clickable with no
        // announced action. The grocery row already does this; the two had drifted apart.
        modifier = Modifier
            .animateContentSize()
            .semantics { role = Role.Button },
        onClick = {
            onEvent(
                if (revealed) DashboardEvent.DismissActions else DashboardEvent.RevealActions(task.id),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (revealed) 1.0f else Sizing.CARD_WIDTH_FRACTION)
                .padding(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.itemTitle,
                    modifier = Modifier.weight(1.0f),
                )
                task.schedule.hour?.let {
                    Text(text = it.toFieldString(), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            task.description?.let {
                Text(text = it, modifier = Modifier.padding(top = Spacing.xs))
            }
            if (revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = { onEvent(DashboardEvent.EditTaskClicked(task.id)) }) {
                        Text(stringResource(Res.string.tasks_edit_task))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    }
                    Button(
                        onClick = { onEvent(DashboardEvent.CompleteTask(task.id, onDate)) },
                        // Stays disabled until the write comes back: a live tick over a task that
                        // is already being completed is what let a double tap advance it twice.
                        enabled = canSubmit,
                    ) {
                        Text(stringResource(Res.string.tasks_complete_task))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    }
                }
            }
        }
    }
}

private fun DashboardMessage.toStringResource() = when (this) {
    DashboardMessage.LoadFailed -> Res.string.tasks_error_load_failed
    DashboardMessage.CompleteFailed -> Res.string.tasks_error_complete_failed
}
