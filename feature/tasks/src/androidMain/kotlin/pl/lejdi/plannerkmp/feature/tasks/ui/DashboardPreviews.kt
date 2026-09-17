package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule

/*
 * Previews of the dashboard's stateless content composable.
 *
 * This screen had none at all, though the convention is that every stateless `...Content` gets the
 * states that are awkward to reach by hand — and the dashboard is the screen whose missing error
 * branch is the story `LoadableContent` exists to tell.
 *
 * `fabModifier` defaults to `Modifier`, so none of this needs a `SharedTransitionScope`, and no
 * preview here touches Koin.
 */

private val PREVIEW_TODAY = LocalDate(2026, 9, 17)

private fun previewDays(): List<DashboardDay> = listOf(
    DashboardDay(
        date = PREVIEW_TODAY,
        tasks = listOf(
            Task(
                id = 1L,
                name = "Stand-up",
                description = "Daily, with the team",
                schedule = TaskSchedule.Periodic(
                    startDate = PREVIEW_TODAY,
                    daysInterval = 1,
                    hour = LocalTime(9, 30),
                ),
            ),
            Task(
                id = 2L,
                name = "Reply to the landlord",
                description = null,
                schedule = TaskSchedule.Asap(createdOn = PREVIEW_TODAY),
            ),
            Task(
                id = 3L,
                name = "Collect the parcel",
                description = "Depot closes at six",
                schedule = TaskSchedule.OneTime(date = PREVIEW_TODAY),
            ),
        ),
    ),
    DashboardDay(date = PREVIEW_TODAY.plus(1, DateTimeUnit.DAY), tasks = emptyList()),
)

@Preview(name = "Dashboard — a full day")
@Composable
private fun DashboardPreview() = PlannerTheme {
    DashboardContent(
        state = DashboardState(isLoading = false, days = previewDays()),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** A day with nothing on it — the second page of [previewDays], reachable only by swiping. */
@Preview(name = "Dashboard — empty day")
@Composable
private fun DashboardEmptyDayPreview() = PlannerTheme {
    DashboardContent(
        state = DashboardState(
            isLoading = false,
            days = listOf(DashboardDay(date = PREVIEW_TODAY, tasks = emptyList())),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** A card with its actions revealed, which takes a tap to reach and hides again on a swipe. */
@Preview(name = "Dashboard — actions revealed")
@Composable
private fun DashboardRevealedPreview() = PlannerTheme {
    DashboardContent(
        state = DashboardState(isLoading = false, days = previewDays(), revealedTaskId = 1L),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** One completion in flight: that card's Complete is disabled, the others stay live. */
@Preview(name = "Dashboard — completing one task")
@Composable
private fun DashboardCompletingPreview() = PlannerTheme {
    DashboardContent(
        state = DashboardState(
            isLoading = false,
            days = previewDays(),
            revealedTaskId = 1L,
            completingTaskIds = setOf(1L),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** The terminal branch: a failed load with nothing behind it earns a full screen and a retry. */
@Preview(name = "Dashboard — load failed")
@Composable
private fun DashboardLoadFailedPreview() = PlannerTheme {
    DashboardContent(
        state = DashboardState(isLoading = false, loadFailed = true),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}
