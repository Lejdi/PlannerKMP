package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveGymWeekTest {

    /** A Friday, so "today" is never the first page and a grouping bug cannot pass by luck. */
    private val today = LocalDate(2026, 9, 18)
    private val yesterday = today.plus(-1, DateTimeUnit.DAY)
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private val todayProvider = FakeTodayProvider(today)

    /** Planned for today, since a tick only ever shows on today's page. */
    private val todayOnly = setOf(DayOfWeek.FRIDAY)

    private fun observeWeek(datasource: FakeGymDatasource) = ObserveGymWeek(
        datasource = datasource,
        todayProvider = todayProvider,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    private suspend fun week(datasource: FakeGymDatasource): List<GymDay> {
        val result = observeWeek(datasource).invoke(Unit).first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    @Test
    fun anEmptyPlanStillHasSevenDaysBeginningOnMonday() = runTest {
        val days = week(FakeGymDatasource())

        assertEquals(7, days.size)
        assertEquals(DayOfWeek.entries.toList(), days.map { it.dayOfWeek })
        assertTrue(days.all { it.exercises.isEmpty() })
    }

    @Test
    fun exercisesAreGroupedOntoTheirOwnWeekday() = runTest {
        val datasource = FakeGymDatasource(
            listOf(
                exercise(id = 1L, name = "Squat", days = setOf(DayOfWeek.MONDAY)),
                exercise(id = 2L, name = "Leg press", days = setOf(DayOfWeek.MONDAY)),
                exercise(id = 3L, name = "Bench press", days = setOf(DayOfWeek.THURSDAY)),
            ),
        )

        val days = week(datasource).associateBy { it.dayOfWeek }

        assertEquals(listOf("Squat", "Leg press"), days.exerciseNames(DayOfWeek.MONDAY))
        assertEquals(listOf("Bench press"), days.exerciseNames(DayOfWeek.THURSDAY))
        assertTrue(
            DayOfWeek.entries
                .filterNot { it == DayOfWeek.MONDAY || it == DayOfWeek.THURSDAY }
                .all { days.exerciseNames(it).isEmpty() },
        )
    }

    @Test
    fun anExerciseOnSeveralDaysAppearsOnEachOfThem() = runTest {
        val datasource = FakeGymDatasource(
            listOf(
                exercise(id = 1L, name = "Squat", days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
                exercise(id = 2L, name = "Bench press", days = setOf(DayOfWeek.FRIDAY)),
            ),
        )

        val days = week(datasource).associateBy { it.dayOfWeek }

        assertEquals(listOf("Squat"), days.exerciseNames(DayOfWeek.MONDAY))
        assertEquals(listOf("Squat", "Bench press"), days.exerciseNames(DayOfWeek.FRIDAY))
        assertTrue(days.exerciseNames(DayOfWeek.WEDNESDAY).isEmpty())
    }

    /**
     * One completion pair serves every page an exercise is on, so this is the check that keeps a
     * tick made today off the other days' cards.
     */
    @Test
    fun aTickMadeTodayShowsOnlyOnTodaysPage() = runTest {
        val datasource = FakeGymDatasource(
            listOf(
                exercise(
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    setsCount = 2,
                    completedSets = 2,
                    completedOn = today,
                ),
            ),
        )

        val days = week(datasource).associateBy { it.dayOfWeek }

        assertTrue(days.getValue(DayOfWeek.FRIDAY).exercises.single().isDone)
        assertEquals(0, days.getValue(DayOfWeek.MONDAY).exercises.single().doneSets)
    }

    @Test
    fun aTickMadeTodayCounts() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(days = todayOnly, setsCount = 4, completedSets = 2, completedOn = today)),
        )

        val onToday = week(datasource).single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single()

        assertEquals(2, onToday.doneSets)
        assertFalse(onToday.isDone)
    }

    @Test
    fun aTickMadeYesterdayDoesNotCount() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(days = todayOnly, setsCount = 4, completedSets = 4, completedOn = yesterday)),
        )

        val onToday = week(datasource).single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single()

        assertEquals(0, onToday.doneSets, "yesterday's ticks are gone without anything clearing them")
        assertFalse(onToday.isDone)
    }

    /**
     * The whole point of combining `todayFlow()` rather than reading the date per emission.
     *
     * A SQLDelight query flow re-emits only when its table is written, so a phone left on this
     * screen overnight crosses midnight with nothing to trigger a recompute. This asserts the
     * reset arrives with no write at all.
     */
    @Test
    fun theMidnightTickResetsTheDayWithoutAWrite() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(days = todayOnly, setsCount = 2, completedSets = 2, completedOn = today)),
        )
        val weeks = observeWeek(datasource).invoke(Unit)

        val before = weeks.first()
        assertTrue(before is AppResult.Success)
        assertTrue(before.data.single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single().isDone)

        todayProvider.setToday(tomorrow)

        val after = weeks.first()
        assertTrue(after is AppResult.Success)
        val onToday = after.data.single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single()
        assertEquals(0, onToday.doneSets)
        assertTrue(datasource.writes.isEmpty(), "the reset is derived, so nothing was written")
    }

    /** The form may shrink the series count under a row that was already ticked. */
    @Test
    fun aTickCountAboveTheSeriesCountIsClamped() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(days = todayOnly, setsCount = 3, completedSets = 5, completedOn = today)),
        )

        val onToday = week(datasource).single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single()

        assertEquals(3, onToday.doneSets, "an exercise cannot have more done than it has")
        assertTrue(onToday.isDone)
    }

    /** And it may grow it, which un-finishes the exercise without touching the completion pair. */
    @Test
    fun growingTheSeriesCountStopsTheExerciseBeingDone() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(days = todayOnly, setsCount = 5, completedSets = 3, completedOn = today)),
        )

        val onToday = week(datasource).single { it.dayOfWeek == DayOfWeek.FRIDAY }.exercises.single()

        assertEquals(3, onToday.doneSets)
        assertFalse(onToday.isDone)
    }

    @Test
    fun aFailureIsPassedThrough() = runTest {
        val datasource = FakeGymDatasource(listOf(exercise()))
        datasource.failLiveStream()

        val result = observeWeek(datasource).invoke(Unit).first()

        assertTrue(result is AppResult.Failure)
    }

    private fun Map<DayOfWeek, GymDay>.exerciseNames(day: DayOfWeek): List<String> =
        getValue(day).exercises.map { it.exercise.name }
}
