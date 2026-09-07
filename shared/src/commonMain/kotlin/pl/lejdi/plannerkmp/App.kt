package pl.lejdi.plannerkmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme

// Placeholder root composable: no feature module exists yet to navigate to,
// so core:navigation's NavDisplay isn't wired up here until the first one does.
@Composable
fun App() {
    PlannerTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("PlannerKMP base architecture ready")
        }
    }
}
