package pl.lejdi.plannerkmp

import androidx.compose.ui.window.ComposeUIViewController

/**
 * The app's object graph, built once however many times the hosting controller is recreated.
 *
 * A `by lazy` rather than the `if (!koinStarted)` flag this used to need: that flag existed only
 * because `startKoin` installs into Koin's global context and throws on a second call. With the
 * graph an ordinary value, "build it once" is what `lazy` already means — and on Kotlin/Native it
 * is thread-safe by default.
 */
private val koinApplication by lazy { initKoin() }

@Suppress("FunctionNaming") // Objective-C entry point: Swift calls it by this name.
fun MainViewController() = ComposeUIViewController { App(koinApplication) }
