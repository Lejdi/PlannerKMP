package pl.lejdi.plannerkmp.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class ScreenKey(val id: Int) : NavKey

class NavigatorTest {

    @Test
    fun startsWithOnlyTheStartDestination() {
        val navigator = Navigator(ScreenKey(1))

        assertEquals(listOf<NavKey>(ScreenKey(1)), navigator.backStack)
    }

    @Test
    fun navigateToPushesOntoBackStack() {
        val navigator = Navigator(ScreenKey(1))

        navigator.navigateTo(ScreenKey(2))

        assertEquals(listOf<NavKey>(ScreenKey(1), ScreenKey(2)), navigator.backStack)
    }

    @Test
    fun goBackPopsTheTopDestination() {
        val navigator = Navigator(ScreenKey(1))
        navigator.navigateTo(ScreenKey(2))

        val popped = navigator.goBack()

        assertTrue(popped)
        assertEquals(listOf<NavKey>(ScreenKey(1)), navigator.backStack)
    }

    @Test
    fun goBackFailsWhenOnlyStartDestinationRemains() {
        val navigator = Navigator(ScreenKey(1))

        val popped = navigator.goBack()

        assertFalse(popped)
        assertEquals(listOf<NavKey>(ScreenKey(1)), navigator.backStack)
    }
}
