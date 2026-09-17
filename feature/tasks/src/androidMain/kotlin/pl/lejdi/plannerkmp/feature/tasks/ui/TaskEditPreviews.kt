package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskField
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

/*
 * Previews of the stateless content composables.
 *
 * `ui-tooling-preview` was declared in six places across this build and used in none: the
 * dependency was on every library module's Android classpath, `:androidApp` carried the
 * `debugImplementation` renderer to draw them, and there was not one `@Preview` in the codebase to
 * render. These are the states that are awkward to reach by hand — a form with two fields already
 * marked invalid, a save already in flight — which is exactly what the split into stateless
 * `...Content` composables was for.
 *
 * They live in `androidMain` because that is where the annotation is: `commonMain` taking
 * `ui-tooling-preview` would put it on the iOS classpath too, for a renderer that only runs on the
 * JVM.
 */

private val PREVIEW_TODAY = LocalDate(2026, 9, 17)

@Preview(name = "Edit — new task")
@Composable
private fun TaskEditNewPreview() = PlannerTheme {
    TaskEditContent(
        state = TaskEditState(today = PREVIEW_TODAY),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Edit — periodic, with errors")
@Composable
private fun TaskEditInvalidPreview() = PlannerTheme {
    TaskEditContent(
        state = TaskEditState(
            taskId = 1L,
            isFormSeeded = true,
            today = PREVIEW_TODAY,
            form = TaskForm(
                name = "",
                description = "Water the plants on the balcony",
                type = TaskType.Periodic,
                startDate = PREVIEW_TODAY,
                endDate = LocalDate(2026, 9, 1),
                hour = LocalTime(9, 30),
                daysInterval = "0",
                // Both at once — the state a single-error validator could never produce.
                invalidFields = setOf(TaskField.Name, TaskField.DaysInterval, TaskField.EndDate),
            ),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Edit — saving")
@Composable
private fun TaskEditSubmittingPreview() = PlannerTheme {
    TaskEditContent(
        state = TaskEditState(
            taskId = 1L,
            isFormSeeded = true,
            isSubmitting = true,
            today = PREVIEW_TODAY,
            form = TaskForm(name = "Take the bins out", type = TaskType.OneTime, startDate = PREVIEW_TODAY),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Edit — load failed")
@Composable
private fun TaskEditLoadFailedPreview() = PlannerTheme {
    TaskEditContent(
        state = TaskEditState(taskId = 1L, loadFailed = true, today = PREVIEW_TODAY),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}
