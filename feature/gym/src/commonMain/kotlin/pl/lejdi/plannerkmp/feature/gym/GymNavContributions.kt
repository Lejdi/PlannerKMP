package pl.lejdi.plannerkmp.feature.gym

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import pl.lejdi.plannerkmp.core.navigation.FeatureTab
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.NavKeySerializersContributor
import pl.lejdi.plannerkmp.feature.gym.resources.Res
import pl.lejdi.plannerkmp.feature.gym.resources.gym_tab_title
import pl.lejdi.plannerkmp.feature.gym.ui.GymExerciseEditScreen
import pl.lejdi.plannerkmp.feature.gym.ui.GymIcon
import pl.lejdi.plannerkmp.feature.gym.ui.GymScreen

class GymNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<GymNavKey.Week> {
            val navigator = LocalNavigator.current
            GymScreen(
                onNavigateToAddExercise = { navigator.navigateTo(GymNavKey.ExerciseAdd(it)) },
                onNavigateToEditExercise = { navigator.navigateTo(GymNavKey.ExerciseEdit(it)) },
            )
        }
        // Two keys, one screen. Adding seeds the form's weekday from the page it came from;
        // editing has no weekday to seed, because the row carries the only correct one.
        entry<GymNavKey.ExerciseAdd> { key ->
            val navigator = LocalNavigator.current
            GymExerciseEditScreen(
                exerciseId = null,
                dayOfWeek = key.dayOfWeek,
                onNavigateBack = { navigator.goBack() },
            )
        }
        entry<GymNavKey.ExerciseEdit> { key ->
            val navigator = LocalNavigator.current
            GymExerciseEditScreen(
                exerciseId = key.exerciseId,
                dayOfWeek = null,
                onNavigateBack = { navigator.goBack() },
            )
        }
    }
}

class GymNavKeySerializersContributor : NavKeySerializersContributor {
    override fun PolymorphicModuleBuilder<NavKey>.contribute() {
        subclass(GymNavKey.Week::class)
        subclass(GymNavKey.ExerciseAdd::class)
        subclass(GymNavKey.ExerciseEdit::class)
    }
}

/**
 * Three distinct classes, not three more `single<...>` of the existing types: Koin keys a
 * definition by type, so a second `single<FeatureTab>` would silently override its neighbour and
 * the app would come up a tab short.
 *
 * The icon is vendored — see [GymIcon] — because `material-icons-core` is the only set on the
 * classpath and has no dumbbell. It collides with none of Tasks' `AutoMirrored.Filled.List`,
 * Grocery's `Filled.ShoppingCart` or Routines' `Filled.Refresh`.
 */
class GymFeatureTab : FeatureTab {
    override val id: String = "gym"
    override val order: Int = 3
    override val title = Res.string.gym_tab_title
    override val icon = GymIcon
    override val rootKey = GymNavKey.Week
}
