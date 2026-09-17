package pl.lejdi.plannerkmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.LocalSharedTransitionScope
import pl.lejdi.plannerkmp.core.navigation.NavAnimation
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.core.navigation.Navigator
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme

/**
 * The composition root names no feature.
 *
 * Tabs, nav entries and NavKey serializers are all contributed through Koin multibindings, so a new
 * feature is a new module and a new Gradle dependency — not an edit here. Previously the entry
 * providers were contributed but the bottom bar was still a hardcoded list of every feature.
 *
 * [koinApplication] is a parameter rather than a `getKoin()` read off Koin's global context. The
 * graph is built by [initKoin] and owned by the platform entry point, so the one dependency this
 * composable cannot declare in its signature used to be the whole object graph.
 *
 * [KoinIsolatedContext] — not the deprecated `KoinContext`, and not `KoinApplication { }`, which
 * would build the graph inside composition — is Koin's own API for a context that was created with
 * `koinApplication()` and held in a field. It publishes the graph to `LocalKoinApplication` and
 * `LocalKoinScope`, which is what `koinViewModel()` in each feature screen resolves against.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(koinApplication: KoinApplication) {
    KoinIsolatedContext(koinApplication) {
        AppContent(koinApplication.koin)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppContent(koin: Koin) {
    PlannerTheme {
        val entryProvider = remember {
            entryProvider<NavKey> {
                koin.getAll<NavEntryProviderContributor>().forEach { contributor ->
                    with(contributor) { contribute() }
                }
            }
        }
        val tabs = remember { koin.getAll<FeatureTab>().sortedBy { it.order } }

        // rememberNavBackStack needs every NavKey subtype registered up front: reflection-based
        // polymorphism is Android-only, and this back stack has to restore on iOS too.
        val savedStateConfiguration = remember {
            SavedStateConfiguration {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        koin.getAll<NavKeySerializersContributor>().forEach { contributor ->
                            with(contributor) { contribute() }
                        }
                    }
                }
            }
        }

        // rememberSaveable, so the chosen tab survives rotation with the back stacks. Saved by the
        // tab's own id rather than by its position: the tab list is assembled from whichever
        // features Koin knows about, so a restored index could land on a different tab than the
        // user left on. An id that no longer resolves falls back to the first tab.
        var selectedTabId by rememberSaveable { mutableStateOf(tabs.firstOrNull()?.id) }
        val selectedTabIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)

        val navigators = tabs.map { tab ->
            key(tab.rootKey) {
                val backStack = rememberNavBackStack(savedStateConfiguration, tab.rootKey)
                remember(backStack) { Navigator(backStack) }
            }
        }

        // Hoisted above the tab switch on purpose:
        //  - the decorators keep each entry's ViewModelStore and saved state alive rather than
        //    being rebuilt every time the user changes tab;
        //  - SharedTransitionLayout provides LocalSharedTransitionScope to *every* tab. Providing
        //    it around only one tab left a trap: the entryProvider is shared, so any feature screen
        //    reachable from another tab would have thrown on reading the local.
        val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val entryDecorators = remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
            listOf<NavEntryDecorator<NavKey>>(saveableStateHolderDecorator, viewModelStoreDecorator)
        }

        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabId = tab.id },
                                    // null, not the title: the item already carries a visible
                                    // `label` with that exact string, and a content description
                                    // on the icon as well made every tab announce its name twice.
                                    icon = {
                                        Icon(imageVector = tab.icon, contentDescription = null)
                                    },
                                    label = { Text(stringResource(tab.title)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val navigator = navigators.getOrNull(selectedTabIndex) ?: return@Scaffold
                    Box(modifier = Modifier.padding(padding)) {
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            NavDisplay(
                                backStack = navigator.backStack,
                                onBack = { navigator.goBack() },
                                entryDecorators = entryDecorators,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                // Matches the shared-element bounds transform. Nav3's own default
                                // differs per platform, which left a half-faded outgoing screen
                                // hanging over the FAB on Android.
                                transitionSpec = { NavAnimation.fade },
                                popTransitionSpec = { NavAnimation.fade },
                                predictivePopTransitionSpec = { NavAnimation.fade },
                                entryProvider = entryProvider,
                            )
                        }
                    }
                }
            }
        }
    }
}
