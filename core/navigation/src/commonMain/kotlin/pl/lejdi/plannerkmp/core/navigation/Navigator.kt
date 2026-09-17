package pl.lejdi.plannerkmp.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Owns one tab's back stack.
 *
 * Backed by a [NavBackStack] supplied by the caller, so the stack can be created with
 * `rememberNavBackStack(...)` and restored after a configuration change or process death — a plain
 * `mutableStateListOf` here meant rotating the device dropped the user back at the start destination.
 */
class Navigator(val backStack: NavBackStack<NavKey>) {

    /**
     * Ignores a push of the key already on top: two taps on the same card used to put two identical
     * entries on the stack, so the user had to press back twice.
     */
    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    /**
     * Pops the top entry, or does nothing at the root.
     *
     * Returns nothing: it used to answer `Boolean`, and both call sites discarded it. Doing nothing
     * at the root is also not a fallback — `NavDisplay` only enables its back handler while the
     * scene has a previous entry, so the system back at a tab root never reaches here and still
     * leaves the app.
     */
    fun goBack() {
        if (backStack.size <= 1) return
        backStack.removeAt(backStack.lastIndex)
    }
}
