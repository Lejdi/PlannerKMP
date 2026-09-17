package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToggleRoutineDoneTest {

    private val today = LocalDate(2026, 9, 17)
    private val yesterday = LocalDate(2026, 9, 16)

    private fun routine(completedOn: LocalDate?) =
        Routine(id = 1, name = "Stretch", description = null, completedOn = completedOn)

    private fun useCase(
        datasource: FakeRoutinesDatasource,
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
    ) = ToggleRoutineDone(datasource = datasource, todayProvider = todayProvider)

    @Test
    fun markingDoneStoresTodaysDate() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(completedOn = null)))

        val result = useCase(datasource).invoke(ToggleRoutineDone.Params(id = 1, done = true))

        assertTrue(result is AppResult.Success)
        assertEquals(today, datasource.routines.single().completedOn)
    }

    @Test
    fun markingNotDoneClearsTheDate() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(completedOn = today)))

        val result = useCase(datasource).invoke(ToggleRoutineDone.Params(id = 1, done = false))

        assertTrue(result is AppResult.Success)
        assertNull(datasource.routines.single().completedOn)
    }

    /** Ticking replaces a stale date rather than leaving yesterday's in place. */
    @Test
    fun markingDoneOverwritesAnOlderDate() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(completedOn = yesterday)))

        useCase(datasource).invoke(ToggleRoutineDone.Params(id = 1, done = true))

        assertEquals(today, datasource.routines.single().completedOn)
    }

    /** It reads the clock at write time, not at construction time. */
    @Test
    fun theDateIsReadWhenTheWriteHappens() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(completedOn = null)))
        val todayProvider = FakeTodayProvider(yesterday)
        val toggle = useCase(datasource, todayProvider)

        todayProvider.setToday(today)
        toggle.invoke(ToggleRoutineDone.Params(id = 1, done = true))

        assertEquals(today, datasource.routines.single().completedOn)
    }

    @Test
    fun aStorageFailurePropagates() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(completedOn = null)))
        datasource.failNext(RoutinesWrite.UpdateCompletedOn, DomainError.Database("disk full"))

        val result = useCase(datasource).invoke(ToggleRoutineDone.Params(id = 1, done = true))

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.Database)
    }

    @Test
    fun aMissingRoutineReportsNotFound() = runTest {
        val datasource = FakeRoutinesDatasource()

        val result = useCase(datasource).invoke(ToggleRoutineDone.Params(id = 404, done = true))

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound)
    }
}
