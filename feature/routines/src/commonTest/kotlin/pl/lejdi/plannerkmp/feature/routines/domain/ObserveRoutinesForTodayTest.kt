package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.invoke
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRoutinesForTodayTest {

    private val today = LocalDate(2026, 9, 17)
    private val yesterday = LocalDate(2026, 9, 16)
    private val tomorrow = LocalDate(2026, 9, 18)

    private fun routine(id: Long, name: String, completedOn: LocalDate?) =
        Routine(id = id, name = name, description = null, completedOn = completedOn)

    private fun useCase(
        datasource: FakeRoutinesDatasource,
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
    ) = ObserveRoutinesForToday(
        datasource = datasource,
        todayProvider = todayProvider,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun aRoutineCompletedTodayReadsAsDone() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(1, "Stretch", today)))

        val result = useCase(datasource).invoke().first()

        assertTrue(result is AppResult.Success)
        assertTrue(result.data.single().isDoneToday)
    }

    @Test
    fun aRoutineCompletedYesterdayReadsAsNotDone() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(1, "Stretch", yesterday)))

        val result = useCase(datasource).invoke().first()

        assertTrue(result is AppResult.Success)
        assertFalse(result.data.single().isDoneToday)
    }

    @Test
    fun aRoutineNeverCompletedReadsAsNotDone() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(1, "Stretch", null)))

        val result = useCase(datasource).invoke().first()

        assertTrue(result is AppResult.Success)
        assertFalse(result.data.single().isDoneToday)
    }

    /**
     * The whole feature, in one test.
     *
     * Nothing writes to the table here — the date simply changes, as it does at local midnight on
     * a phone left on this screen. An implementation that reads `today()` per emission passes every
     * other test in this class and fails this one, because a SQLDelight query flow re-emits only
     * when its table is written: on a quiet night nothing re-emits at all, and the list goes on
     * showing yesterday's ticks until the user happens to write something.
     */
    @Test
    fun midnightAloneUnticksTheListWithNoWriteToTheTable() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(1, "Stretch", today)))
        val todayProvider = FakeTodayProvider(today)
        val seen = mutableListOf<List<TodayRoutine>>()
        backgroundScope.launch {
            useCase(datasource, todayProvider).invoke().collect { result ->
                if (result is AppResult.Success) seen += result.data
            }
        }
        runCurrent()
        assertTrue(seen.last().single().isDoneToday, "it was ticked today")

        todayProvider.setToday(tomorrow)
        runCurrent()

        assertFalse(seen.last().single().isDoneToday, "the same row, one day later, is not done")
        assertEquals(today, datasource.routines.single().completedOn, "nothing was written")
    }

    @Test
    fun aWriteReEmitsWithoutTheDateChanging() = runTest {
        val datasource = FakeRoutinesDatasource(listOf(routine(1, "Stretch", null)))
        val seen = mutableListOf<List<TodayRoutine>>()
        backgroundScope.launch {
            useCase(datasource).invoke().collect { result ->
                if (result is AppResult.Success) seen += result.data
            }
        }
        runCurrent()
        assertFalse(seen.last().single().isDoneToday)

        datasource.updateCompletedOn(id = 1, completedOn = today)
        runCurrent()

        assertTrue(seen.last().single().isDoneToday)
    }

    @Test
    fun everyRoutineIsCarriedThroughInOrder() = runTest {
        val datasource = FakeRoutinesDatasource(
            listOf(
                routine(1, "Stretch", today),
                routine(2, "Read", null),
                routine(3, "Water plants", yesterday),
            ),
        )

        val result = useCase(datasource).invoke().first()

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(1L, 2L, 3L), result.data.map { it.id })
        assertEquals(listOf(true, false, false), result.data.map { it.isDoneToday })
        assertEquals("Stretch", result.data.first().routine.name)
    }

    @Test
    fun aFailingSourcePropagates() = runTest {
        val datasource = FakeRoutinesDatasource()
        datasource.observeFailure = DomainError.Database("driver gone")

        val result = useCase(datasource).invoke().first()

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.Database)
    }
}
