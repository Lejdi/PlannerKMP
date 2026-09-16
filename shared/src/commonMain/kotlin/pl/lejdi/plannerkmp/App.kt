package pl.lejdi.plannerkmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.getKoin
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.Navigator
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.grocery.GroceryNavKey
import pl.lejdi.plannerkmp.feature.tasks.TasksNavKey

private enum class BottomNavTab { Tasks, Grocery }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    PlannerTheme {
        val koin = getKoin()
        val entryProvider = remember {
            entryProvider<NavKey> {
                koin.getAll<NavEntryProviderContributor>().forEach { contributor ->
                    with(contributor) { contribute() }
                }
            }
        }

        var selectedTab by remember { mutableStateOf(BottomNavTab.Tasks) }
        val tasksNavigator = remember { Navigator(TasksNavKey.Dashboard) }
        val groceryNavigator = remember { Navigator(GroceryNavKey) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.Tasks,
                        onClick = { selectedTab = BottomNavTab.Tasks },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Tasks") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.Grocery,
                        onClick = { selectedTab = BottomNavTab.Grocery },
                        icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                        label = { Text("Grocery") },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    BottomNavTab.Tasks -> CompositionLocalProvider(LocalNavigator provides tasksNavigator) {
                        SharedTransitionLayout {
                            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                                NavDisplay(
                                    backStack = tasksNavigator.backStack,
                                    onBack = { tasksNavigator.goBack() },
                                    entryDecorators = listOf(
                                        rememberSaveableStateHolderNavEntryDecorator(),
                                        rememberViewModelStoreNavEntryDecorator(),
                                    ),
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    entryProvider = entryProvider,
                                )
                            }
                        }
                    }
                    BottomNavTab.Grocery -> CompositionLocalProvider(LocalNavigator provides groceryNavigator) {
                        NavDisplay(
                            backStack = groceryNavigator.backStack,
                            onBack = { groceryNavigator.goBack() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            entryProvider = entryProvider,
                        )
                    }
                }
            }
        }
    }
}
