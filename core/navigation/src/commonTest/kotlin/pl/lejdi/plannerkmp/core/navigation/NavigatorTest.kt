package pl.lejdi.plannerkmp.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

private data class ScreenKey(val id: Int) : NavKey

private fun navigator(vararg keys: NavKey) = Navigator(NavBackStack(*keys))

class NavigatorTest {

    @Test
    fun startsWithOnlyTheStartDestination() {
        val navigator = navigator(ScreenKey(1))

        assertEquals(listOf<NavKey>(ScreenKey(1)), navigator.backStack.toList())
    }

    @Test
    fun navigateToPushesOntoBackStack() {
        val navigator = navigator(ScreenKey(1))

        navigator.navigateTo(ScreenKey(2))

        assertEquals(listOf<NavKey>(ScreenKey(1), ScreenKey(2)), navigator.backStack.toList())
    }

    @Test
    fun goBackPopsTheTopDestination() {
        val navigator = navigator(ScreenKey(1))
        navigator.navigateTo(ScreenKey(2))

        navigator.goBack()

        assertEquals(listOf<NavKey>(ScreenKey(1)), navigator.backStack.toList())
    }

    @Test
    fun goBackFailsWhenOnlyStartDestinationRemains() {
        val navigator = navigator(ScreenKey(1))

        navigator.goBack()

        assertEquals(
            listOf<NavKey>(ScreenKey(1)),
            navigator.backStack.toList(),
            "the start destination is never popped",
        )
    }

    @Test
    fun pushingTheKeyAlreadyOnTopIsIgnored() {
        val navigator = navigator(ScreenKey(1))

        // A double tap used to put the same destination on the stack twice, so the user had to
        // press back once per tap to get out of it.
        navigator.navigateTo(ScreenKey(2))
        navigator.navigateTo(ScreenKey(2))

        assertEquals(listOf<NavKey>(ScreenKey(1), ScreenKey(2)), navigator.backStack.toList())
    }

    @Test
    fun theSameKeyCanStillAppearDeeperInTheStack() {
        val navigator = navigator(ScreenKey(1))

        navigator.navigateTo(ScreenKey(2))
        navigator.navigateTo(ScreenKey(1))

        assertEquals(listOf<NavKey>(ScreenKey(1), ScreenKey(2), ScreenKey(1)), navigator.backStack.toList())
    }
}
