package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.gym.domain.DayExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymDay
import pl.lejdi.plannerkmp.feature.gym.domain.GymExercise

private val TODAY = LocalDate(2026, 9, 18)
private val TODAY_WEEKDAY = DayOfWeek.FRIDAY

/**
 * Built through one factory rather than copied, so `completedOn` and `doneSets` cannot disagree —
 * the one inconsistency a hand-written preview state can hold that the real derivation cannot.
 */
private fun previewExercise(
    id: Long,
    name: String,
    comment: String?,
    setsCount: Int,
    repsPerSet: Int,
    weight: Double?,
    doneSets: Int = 0,
) = DayExercise(
    exercise = GymExercise(
        id = id,
        name = name,
        comment = comment,
        dayOfWeek = TODAY_WEEKDAY,
        setsCount = setsCount,
        repsPerSet = repsPerSet,
        weight = weight,
        completedSets = doneSets,
        completedOn = TODAY.takeIf { doneSets > 0 },
    ),
    doneSets = doneSets,
)

private val BENCH = previewExercise(1L, "Bench press", "slow eccentric", 4, 8, 60.0, doneSets = 2)
private val BENCH_DONE =
    previewExercise(1L, "Bench press", "slow eccentric", 4, 8, 60.0, doneSets = 4)
private val PULL_UPS = previewExercise(2L, "Pull-ups", null, 3, 10, null, doneSets = 3)
private val ROWS = previewExercise(3L, "Barbell row", null, 4, 10, 52.5)

/** Today's page holds [onToday]; Monday holds one exercise so a second chip is marked. */
private fun week(onToday: List<DayExercise>): List<GymDay> = DayOfWeek.entries.map { weekday ->
    GymDay(
        dayOfWeek = weekday,
        exercises = when (weekday) {
            TODAY_WEEKDAY -> onToday
            DayOfWeek.MONDAY -> listOf(ROWS)
            else -> emptyList()
        },
    )
}

private fun emptyWeek(): List<GymDay> =
    DayOfWeek.entries.map { GymDay(dayOfWeek = it, exercises = emptyList()) }

private fun state(days: List<GymDay>, weightEditor: WeightEditor? = null) = GymState(
    isLoading = false,
    days = days,
    today = TODAY_WEEKDAY,
    weightEditor = weightEditor,
)

/** The resting state: one exercise part-done, one finished and faded, series boxes live. */
@Preview(name = "Gym — today, part-done")
@Composable
private fun GymTodayPreview() = PlannerTheme {
    GymContent(
        state = state(week(listOf(BENCH, PULL_UPS))),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** Everything ticked: the whole page faded, which is the point of keeping them in place. */
@Preview(name = "Gym — all done")
@Composable
private fun GymAllDonePreview() = PlannerTheme {
    GymContent(
        state = state(week(listOf(BENCH_DONE, PULL_UPS))),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** Today with nothing on it, while other days have something. */
@Preview(name = "Gym — empty day")
@Composable
private fun GymEmptyDayPreview() = PlannerTheme {
    GymContent(
        state = state(week(emptyList())),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** The inline weight editor open, which is this feature's whole "make the weight easy" claim. */
@Preview(name = "Gym — weight editor open")
@Composable
private fun GymWeightEditorPreview() = PlannerTheme {
    GymContent(
        state = state(
            days = week(listOf(BENCH, ROWS)),
            weightEditor = WeightEditor(exerciseId = 1L, text = "82.5"),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** The same, rejected: the underline and the text both have to say so. */
@Preview(name = "Gym — weight editor invalid")
@Composable
private fun GymWeightEditorInvalidPreview() = PlannerTheme {
    GymContent(
        state = state(
            days = week(listOf(BENCH, ROWS)),
            weightEditor = WeightEditor(exerciseId = 1L, text = "heavy", isInvalid = true),
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** Nothing planned anywhere: the week-level wording, not seven copies of the day-level one. */
@Preview(name = "Gym — empty week")
@Composable
private fun GymEmptyWeekPreview() = PlannerTheme {
    GymContent(
        state = state(emptyWeek()),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/** A load that failed with nothing behind it: full screen and a retry, not a snackbar. */
@Preview(name = "Gym — load failed")
@Composable
private fun GymLoadFailedPreview() = PlannerTheme {
    GymContent(
        state = GymState(
            isLoading = false,
            loadFailed = true,
            days = emptyWeek(),
            today = TODAY_WEEKDAY,
        ),
        snackbarHostState = SnackbarHostState(),
        onEvent = {},
    )
}

/**
 * A weekday that is **not** today: the same cards, no checkboxes.
 *
 * Rendered as a single page rather than through `GymContent`, because the pager always opens on
 * today and so this state is unreachable from there — see [DayPage]. It is also the state most
 * likely to regress, since nothing in the layout hints that a tick belongs to one day.
 */
@Preview(name = "Gym — another weekday, plan only")
@Composable
private fun GymOtherWeekdayPreview() = PlannerTheme {
    DayPage(
        day = GymDay(dayOfWeek = DayOfWeek.MONDAY, exercises = listOf(ROWS, PULL_UPS)),
        isToday = false,
        isWeekEmpty = false,
        state = state(week(listOf(BENCH))),
        onEvent = {},
    )
}
