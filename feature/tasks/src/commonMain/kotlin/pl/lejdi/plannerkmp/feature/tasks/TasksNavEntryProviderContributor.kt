package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardScreen
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditScreen

class TasksNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<TasksNavKey.Dashboard> {
            val navigator = LocalNavigator.current
            DashboardScreen(
                onNavigateToAddTask = { navigator.navigateTo(TasksNavKey.TaskEdit(task = null)) },
                onNavigateToEditTask = { task -> navigator.navigateTo(TasksNavKey.TaskEdit(task)) },
            )
        }
        entry<TasksNavKey.TaskEdit> { key ->
            val navigator = LocalNavigator.current
            TaskEditScreen(task = key.task, onNavigateBack = { navigator.goBack() })
        }
    }
}
