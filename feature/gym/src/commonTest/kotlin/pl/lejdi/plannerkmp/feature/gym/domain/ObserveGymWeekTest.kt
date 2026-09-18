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
                exercise(id = 1L, name = "Squat", dayOfWeek = DayOfWeek.MONDAY),
                exercise(id = 2L, name = "Leg press", dayOfWeek = DayOfWeek.MONDAY),
                exercise(id = 3L, name = "Bench press", dayOfWeek = DayOfWeek.THURSDAY),
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
    fun aTickMadeTodayCounts() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(setsCount = 4, completedSets = 2, completedOn = today)),
        )

        val monday = week(datasource).single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single()

        assertEquals(2, monday.doneSets)
        assertFalse(monday.isDone)
    }

    @Test
    fun aTickMadeYesterdayDoesNotCount() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(setsCount = 4, completedSets = 4, completedOn = yesterday)),
        )

        val monday = week(datasource).single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single()

        assertEquals(0, monday.doneSets, "yesterday's ticks are gone without anything clearing them")
        assertFalse(monday.isDone)
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
            listOf(exercise(setsCount = 2, completedSets = 2, completedOn = today)),
        )
        val weeks = observeWeek(datasource).invoke(Unit)

        val before = weeks.first()
        assertTrue(before is AppResult.Success)
        assertTrue(before.data.single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single().isDone)

        todayProvider.setToday(tomorrow)

        val after = weeks.first()
        assertTrue(after is AppResult.Success)
        val monday = after.data.single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single()
        assertEquals(0, monday.doneSets)
        assertTrue(datasource.writes.isEmpty(), "the reset is derived, so nothing was written")
    }

    /** The form may shrink the series count under a row that was already ticked. */
    @Test
    fun aTickCountAboveTheSeriesCountIsClamped() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(setsCount = 3, completedSets = 5, completedOn = today)),
        )

        val monday = week(datasource).single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single()

        assertEquals(3, monday.doneSets, "an exercise cannot have more done than it has")
        assertTrue(monday.isDone)
    }

    /** And it may grow it, which un-finishes the exercise without touching the completion pair. */
    @Test
    fun growingTheSeriesCountStopsTheExerciseBeingDone() = runTest {
        val datasource = FakeGymDatasource(
            listOf(exercise(setsCount = 5, completedSets = 3, completedOn = today)),
        )

        val monday = week(datasource).single { it.dayOfWeek == DayOfWeek.MONDAY }.exercises.single()

        assertEquals(3, monday.doneSets)
        assertFalse(monday.isDone)
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
