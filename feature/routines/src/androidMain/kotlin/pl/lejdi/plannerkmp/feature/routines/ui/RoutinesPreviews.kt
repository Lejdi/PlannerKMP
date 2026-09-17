package pl.lejdi.plannerkmp.feature.routines.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.routines.domain.Routine
import pl.lejdi.plannerkmp.feature.routines.domain.TodayRoutine

private val TODAY = LocalDate(2026, 9, 17)

private fun previewRoutine(id: Long, name: String, description: String?, doneToday: Boolean) =
    TodayRoutine(
        routine = Routine(
            id = id,
            name = name,
            description = description,
            completedOn = if (doneToday) TODAY else null,
        ),
        isDoneToday = doneToday,
    )

private val PREVIEW_ROUTINES = listOf(
    previewRoutine(1L, "Stretch", "Ten minutes, before coffee", doneToday = true),
    previewRoutine(2L, "Read", null, doneToday = false),
    previewRoutine(3L, "Water the plants", "Only the ones by the window", doneToday = false),
)

@Preview(name = "Routines — today, part done")
@Composable
private fun RoutinesListPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(isLoading = false, routines = PREVIEW_ROUTINES),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Routines — empty")
@Composable
private fun RoutinesEmptyPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(isLoading = false),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Routines — loading")
@Composable
private fun RoutinesLoadingPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(isLoading = true),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Routines — load failed")
@Composable
private fun RoutinesLoadFailedPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(isLoading = false, loadFailed = true),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Routines — editing, name invalid")
@Composable
private fun RoutinesEditingPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(
            isLoading = false,
            routines = PREVIEW_ROUTINES,
            editor = RoutineEditor(
                target = EditorTarget.Existing(2L),
                name = "",
                description = "Twenty pages",
                nameError = true,
            ),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** A tick in flight on one row: that row's checkbox is disabled, the rest stay live. */
@Preview(name = "Routines — ticking one row")
@Composable
private fun RoutinesTogglingPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(
            isLoading = false,
            routines = PREVIEW_ROUTINES,
            togglingRoutineIds = setOf(2L),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

@Preview(name = "Routines — delete confirmation")
@Composable
private fun RoutinesDeleteConfirmationPreview() = PlannerTheme {
    RoutinesContent(
        state = RoutinesState(
            isLoading = false,
            routines = PREVIEW_ROUTINES,
            editor = RoutineEditor(target = EditorTarget.Existing(3L), name = "Water the plants"),
            pendingDeletionId = 3L,
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}
