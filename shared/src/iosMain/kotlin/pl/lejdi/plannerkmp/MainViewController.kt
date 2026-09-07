package pl.lejdi.plannerkmp

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = run {
    initKoin()
    ComposeUIViewController { App() }
}
