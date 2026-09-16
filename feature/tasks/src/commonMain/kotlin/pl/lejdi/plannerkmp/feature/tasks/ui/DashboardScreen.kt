package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.core.ui.components.LoadingView
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

@Composable
internal fun <S : MviState, E : MviEvent, F : MviEffect> LaunchedEffectCollectEffects(
    viewModel: BaseViewModel<S, E, F>,
    onEffect: suspend (F) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { onEffect(it) }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DashboardScreen(
    onNavigateToAddTask: () -> Unit,
    onNavigateToEditTask: (Task) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    // Nav3 tears down and recomposes this screen when returning from TaskEdit, even though
    // the retained ViewModel instance means init{} won't run again - so refresh on every mount.
    LaunchedEffect(Unit) { viewModel.onEvent(DashboardEvent.ScreenResumed) }

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAddTask -> onNavigateToAddTask()
            is DashboardEffect.NavigateToEditTask -> onNavigateToEditTask(effect.task)
            is DashboardEffect.ShowError -> errorMessage = effect.message
        }
    }

    Scaffold(
        floatingActionButton = {
            with(sharedTransitionScope) {
                FloatingActionButton(
                    onClick = { viewModel.onEvent(DashboardEvent.AddTaskClicked) },
                    modifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(AddTaskSharedKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> tween(500) },
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
    ) { padding ->
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
            val pagerState = rememberPagerState { state.days.size }
            LaunchedEffect(pagerState.currentPage) {
                viewModel.onEvent(DashboardEvent.DismissActions)
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 32.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                DayColumn(state.days[page], state.revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun DayColumn(day: DashboardDay, revealedTaskId: Long?, viewModel: DashboardViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = day.date.toCardDisplayString(),
            style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(day.tasks, key = { it.id }) { task ->
                TaskCard(task, revealed = task.id == revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, revealed: Boolean, viewModel: DashboardViewModel) {
    Card(
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.animateContentSize(),
        onClick = {
            viewModel.onEvent(
                if (revealed) DashboardEvent.DismissActions else DashboardEvent.RevealActions(task.id),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (revealed) 1.0f else 0.9f)
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = task.name,
                    style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1.0f),
                )
                task.hour?.let {
                    Text(text = it.toString(), modifier = Modifier.padding(start = 4.dp))
                }
            }
            task.description?.let {
                Text(text = it, modifier = Modifier.padding(top = 4.dp))
            }
            if (revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = { viewModel.onEvent(DashboardEvent.EditTaskClicked(task)) }) {
                        Text("Edit")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    }
                    Button(onClick = { viewModel.onEvent(DashboardEvent.CompleteTask(task)) }) {
                        Text("Complete")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    }
                }
            }
        }
    }
}
