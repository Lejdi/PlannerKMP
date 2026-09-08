package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
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

@Composable
fun DashboardScreen(
    onNavigateToAddTask: () -> Unit,
    onNavigateToEditTask: (Task) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAddTask -> onNavigateToAddTask()
            is DashboardEffect.NavigateToEditTask -> onNavigateToEditTask(effect.task)
            is DashboardEffect.ShowError -> Unit
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(DashboardEvent.AddTaskClicked) }) {
                Text("+")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyRow(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.days, key = { it.date.toString() }) { day ->
                DayColumn(day, state.revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun DayColumn(day: DashboardDay, revealedTaskId: Long?, viewModel: DashboardViewModel) {
    Column(modifier = Modifier.width(220.dp).padding(8.dp)) {
        Text(day.date.toCardDisplayString())
        LazyColumn {
            items(day.tasks, key = { it.id }) { task ->
                TaskCard(task, revealed = task.id == revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, revealed: Boolean, viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(task.name)
            task.hour?.let { Text(it.toString()) }
            if (!revealed) {
                Button(onClick = { viewModel.onEvent(DashboardEvent.RevealActions(task.id)) }) {
                    Text("...")
                }
            } else {
                Row {
                    Button(onClick = { viewModel.onEvent(DashboardEvent.EditTaskClicked(task)) }) { Text("Edit") }
                    Button(onClick = { viewModel.onEvent(DashboardEvent.CompleteTask(task)) }) { Text("Complete") }
                }
            }
        }
    }
}
