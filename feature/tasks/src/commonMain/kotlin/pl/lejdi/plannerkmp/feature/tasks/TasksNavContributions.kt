package pl.lejdi.plannerkmp.feature.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.tasks.resources.Res
import pl.lejdi.plannerkmp.feature.tasks.resources.tasks_tab_title
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardScreen
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditScreen

class TasksNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<TasksNavKey.Dashboard> {
            val navigator = LocalNavigator.current
            DashboardScreen(
                onNavigateToAddTask = { navigator.navigateTo(TasksNavKey.TaskEdit(taskId = null)) },
                onNavigateToEditTask = { taskId -> navigator.navigateTo(TasksNavKey.TaskEdit(taskId)) },
            )
        }
        entry<TasksNavKey.TaskEdit> { key ->
            val navigator = LocalNavigator.current
            TaskEditScreen(taskId = key.taskId, onNavigateBack = { navigator.goBack() })
        }
    }
}

class TasksNavKeySerializersContributor : NavKeySerializersContributor {
    override fun PolymorphicModuleBuilder<NavKey>.contribute() {
        subclass(TasksNavKey.Dashboard::class)
        subclass(TasksNavKey.TaskEdit::class)
    }
}

class TasksFeatureTab : FeatureTab {
    override val id: String = "tasks"
    override val order: Int = 0
    override val title = Res.string.tasks_tab_title
    override val icon = Icons.AutoMirrored.Filled.List
    override val rootKey = TasksNavKey.Dashboard
}
