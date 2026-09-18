package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToggleExerciseSetTest {

    private val today = LocalDate(2026, 9, 18)
    private val todayProvider = FakeTodayProvider(today)

    private fun toggle(datasource: FakeGymDatasource) =
        ToggleExerciseSet(datasource = datasource, todayProvider = todayProvider)

    /** The completion write the fake recorded, which is what every case here is about. */
    private fun FakeGymDatasource.recordedCompletion(): FakeGymDatasource.RecordedWrite.UpdateCompletedSets {
        val write = writes.single()
        assertTrue(write is FakeGymDatasource.RecordedWrite.UpdateCompletedSets)
        return write
    }

    private suspend fun tap(
        serieNumber: Int,
        doneSets: Int,
        setsCount: Int = 4,
    ): FakeGymDatasource.RecordedWrite.UpdateCompletedSets {
        val datasource = FakeGymDatasource(listOf(exercise(setsCount = setsCount)))
        toggle(datasource).invoke(
            ToggleExerciseSet.Params(
                exercise = dayExercise(setsCount = setsCount, doneSets = doneSets),
                serieNumber = serieNumber,
            ),
        )
        return datasource.recordedCompletion()
    }

    @Test
    fun tickingAnUntouchedSeriesCountsUpToIt() = runTest {
        val write = tap(serieNumber = 1, doneSets = 0)

        assertEquals(1, write.completedSets)
        assertEquals(today, write.completedOn)
    }

    /**
     * The count model's stated consequence, pinned deliberately.
     *
     * Completion is a number, not a set of series ids, so tapping the third box while none is
     * ticked means "three are done". This is intended, not a bug to be fixed later.
     */
    @Test
    fun tickingAheadCountsUpToTheTappedSeries() = runTest {
        val write = tap(serieNumber = 3, doneSets = 1)

        assertEquals(3, write.completedSets)
    }

    @Test
    fun unTickingTheLastDoneSeriesCountsDownByOne() = runTest {
        val write = tap(serieNumber = 3, doneSets = 3)

        assertEquals(2, write.completedSets)
        assertEquals(today, write.completedOn)
    }

    @Test
    fun unTickingAnEarlierDoneSeriesCountsDownToJustBeforeIt() = runTest {
        val write = tap(serieNumber = 1, doneSets = 3)

        assertEquals(0, write.completedSets)
    }

    /** A row with nothing done carries no date, so there is nothing to compare against. */
    @Test
    fun theDateIsClearedWhenTheCountReachesZero() = runTest {
        val write = tap(serieNumber = 1, doneSets = 1)

        assertEquals(0, write.completedSets)
        assertNull(write.completedOn)
    }

    @Test
    fun theCountNeverExceedsTheSeriesCount() = runTest {
        val write = tap(serieNumber = 4, doneSets = 0, setsCount = 3)

        assertEquals(3, write.completedSets)
    }

    @Test
    fun theProvidersTodayIsStamped() = runTest {
        val otherDay = LocalDate(2026, 12, 24)
        todayProvider.setToday(otherDay)

        val write = tap(serieNumber = 2, doneSets = 0)

        assertEquals(otherDay, write.completedOn, "a tick belongs to the day it was made on")
    }

    @Test
    fun aWriteFailureIsPassedThrough() = runTest {
        val datasource = FakeGymDatasource(listOf(exercise()))
        datasource.failNext(GymWrite.UpdateCompletedSets, DomainError.Database("boom"))

        val result = toggle(datasource).invoke(
            ToggleExerciseSet.Params(exercise = dayExercise(), serieNumber = 1),
        )

        assertTrue(result is AppResult.Failure)
    }
}
