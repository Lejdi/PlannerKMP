package pl.lejdi.plannerkmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.navigation3.runtime.rememberDecoratedNavEntries
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

        // Hoisted above the tab switch on purpose:
        //  - SharedTransitionLayout provides LocalSharedTransitionScope to *every* tab. Providing
        //    it around only one tab left a trap: the entryProvider is shared, so any feature screen
        //    reachable from another tab would have thrown on reading the local;
        //  - one set of decorators serves every tab. Both key their state by the entry's own
        //    content key, so two tabs' entries never share a ViewModelStore or saved state.
        val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val entryDecorators = remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
            listOf<NavEntryDecorator<NavKey>>(saveableStateHolderDecorator, viewModelStoreDecorator)
        }

        // Every tab's entries are decorated here, whichever tab is showing, and NavDisplay is handed
        // only the selected tab's already-decorated list.
        //
        // The decorators clear an entry's ViewModelStore and saved state once its key leaves the
        // back stack *they were decorating*. While NavDisplay took `backStack` directly and the
        // back stack was swapped on a tab change, every entry of the tab being left counted as
        // popped: each switch destroyed that tab's ViewModels, and switching back built them again
        // — a new ViewModel, a new database subscription, a loading frame, then the cross-fade.
        // Decorated here, a tab's entries stay in their own back stack for as long as the app
        // runs, and switching back finds them alive and already loaded.
        val tabEntries = tabs.map { tab ->
            key(tab.rootKey) {
                val backStack = rememberNavBackStack(savedStateConfiguration, tab.rootKey)
                val navigator = remember(backStack) { Navigator(backStack) }
                val entries = rememberDecoratedNavEntries(
                    backStack = backStack,
                    entryDecorators = entryDecorators,
                    entryProvider = entryProvider,
                )
                navigator to entries
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Scaffold(
                    // None. The bottom bar is all this Scaffold places, and NavigationBar pads
                    // itself for the system navigation bar. Each feature screen pads its own top
                    // and sides instead, so the colour behind the status bar is that screen's
                    // background. At the default this Scaffold pushed its content below the status
                    // bar, and the band left above every screen was this Scaffold's container
                    // colour — a white strip over any screen with another background.
                    contentWindowInsets = WindowInsets(0),
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
                    val (navigator, entries) = tabEntries.getOrNull(selectedTabIndex)
                        ?: return@Scaffold
                    // `padding` *and* `consumeWindowInsets`, which is the pair Scaffold's own
                    // contract asks for. `padding` is the bottom bar alone, and consuming it is what
                    // makes `imePadding()` on the edit forms lift them by only the part of the
                    // keyboard the bar does not already cover — and what keeps each feature
                    // Scaffold from padding its bottom a second time.
                    Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        // Keyed by tab, so a tab switch is a fresh NavDisplay showing that tab's top
                        // entry at once. Unkeyed, the one NavDisplay saw its entries replaced and
                        // animated from one tab's screen to the other's as if it were navigating,
                        // cross-fading for the full scene duration on every tap of the bottom bar.
                        key(tabs[selectedTabIndex].id) {
                            CompositionLocalProvider(LocalNavigator provides navigator) {
                                NavDisplay(
                                    entries = entries,
                                    onBack = { navigator.goBack() },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    // Matches the shared-element bounds transform. Nav3's own
                                    // default differs per platform, which left a half-faded
                                    // outgoing screen hanging over the FAB on Android.
                                    transitionSpec = { NavAnimation.fade },
                                    popTransitionSpec = { NavAnimation.fade },
                                    predictivePopTransitionSpec = { NavAnimation.fade },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
