package pl.lejdi.plannerkmp

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
        super.onCreate(savedInstanceState)

        // The graph comes from the Application that owns it rather than from Koin's global context.
        val koinApplication = (application as PlannerApplication).koinApplication

        setContent {
            App(koinApplication)
        }
    }
}
