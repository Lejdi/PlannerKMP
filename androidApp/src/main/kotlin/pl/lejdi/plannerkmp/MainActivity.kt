package pl.lejdi.plannerkmp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// No @Preview of App() here: it resolves its tabs, nav entries and ViewModels from Koin, which the
// preview renderer never starts, so the preview could only ever throw. Feature screens are
// previewable through their stateless ...Content composables instead.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // enableEdgeToEdge leaves the system to guarantee contrast behind the navigation bar, which
        // with three-button navigation means painting its own 90%-white scrim (dark grey at night)
        // over whatever the app draws there. The app's NavigationBar already fills that area with
        // its own container colour, so the scrim only showed as a white strip under the bottom
        // bar — and only on phones using buttons rather than gestures.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // The graph comes from the Application that owns it rather than from Koin's global context.
        val koinApplication = (application as PlannerApplication).koinApplication

        setContent {
            App(koinApplication)
        }
    }
}
