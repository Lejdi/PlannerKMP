package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.gym.domain.GymExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymExerciseDraft
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In `commonTest`, so it runs against NativeSqliteDriver on iOS as well as JDBC on the host. */
class SqlDelightGymDatasourceTest {

    private val today = LocalDate(2026, 9, 18)

    private lateinit var driver: SqlDriver
    private lateinit var datasource: SqlDelightGymDatasource

    /** Stands in for the application scope the datasource is given in production. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        driver = inMemorySqlDriver(GymDatabase.Schema)
        datasource = SqlDelightGymDatasource(
            GymDatabase(
                driver,
                gymExerciseEntityAdapter = GymExerciseEntity.Adapter(
                    dayOfWeekAdapter = DayOfWeekColumnAdapter,
                    completedOnAdapter = LocalDateColumnAdapter,
                ),
            ).gymExerciseEntityQueries,
            appScope,
            // Unconfined as the io dispatcher: safeQuery's withContext then runs the blocking
            // driver call inline on the test thread, so runTest never sees an idle scheduler and
            // fast-forwards into safeQuery's timeout.
            TestCoroutineDispatchers(Dispatchers.Unconfined),
            NoOpLogger(),
        )
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        driver.close()
    }

    private suspend fun storedExercises(): List<GymExercise> {
        val result = datasource.observeExercises().first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    private fun draft(
        name: String = "Bench press",
        comment: String? = "slow eccentric",
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        setsCount: Int = 4,
        repsPerSet: Int = 8,
        weight: Double? = 60.0,
    ) = GymExerciseDraft.ofStored(
        name = name,
        comment = comment,
        dayOfWeek = dayOfWeek,
        setsCount = setsCount,
        repsPerSet = repsPerSet,
        weight = weight,
    )

    private suspend fun addBenchPress(): GymExercise {
        datasource.addExercise(draft())
        return storedExercises().single()
    }

    @Test
    fun addThenObserveReturnsTheExerciseWithNothingDone() = runTest {
        val stored = addBenchPress()

        assertEquals("Bench press", stored.name)
        assertEquals("slow eccentric", stored.comment)
        assertEquals(DayOfWeek.MONDAY, stored.dayOfWeek)
        assertEquals(4, stored.setsCount)
        assertEquals(8, stored.repsPerSet)
        assertEquals(60.0, stored.weight)
        assertEquals(0, stored.completedSets, "a new exercise has nothing ticked")
        assertNull(stored.completedOn)
    }

    @Test
    fun anInsertNeverReportsNotFound() = runTest {
        val result = datasource.addExercise(draft())

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun aBodyweightExerciseRoundTripsWithoutAWeight() = runTest {
        datasource.addExercise(draft(name = "Pull-ups", weight = null))

        assertNull(storedExercises().single().weight)
    }

    /** The real test of DayOfWeekColumnAdapter: every weekday, out and back. */
    @Test
    fun everyWeekdayRoundTrips() = runTest {
        DayOfWeek.entries.forEach { weekday ->
            datasource.addExercise(draft(name = weekday.name, dayOfWeek = weekday))
        }

        val stored = storedExercises()
        assertEquals(DayOfWeek.entries.toList(), stored.map { it.dayOfWeek })
        assertEquals(DayOfWeek.entries.map { it.name }, stored.map { it.name })
    }

    /** The ISO day number is stored so that the query's own ORDER BY is calendar order. */
    @Test
    fun theListIsOrderedByWeekdayThenId() = runTest {
        datasource.addExercise(draft(name = "Friday first insert", dayOfWeek = DayOfWeek.FRIDAY))
        datasource.addExercise(draft(name = "Monday second insert", dayOfWeek = DayOfWeek.MONDAY))
        datasource.addExercise(draft(name = "Monday third insert", dayOfWeek = DayOfWeek.MONDAY))

        assertEquals(
            listOf("Monday second insert", "Monday third insert", "Friday first insert"),
            storedExercises().map { it.name },
        )
    }

    @Test
    fun theListReEmitsAfterAWrite() = runTest {
        datasource.addExercise(draft(name = "First"))
        assertEquals(listOf("First"), storedExercises().map { it.name })

        datasource.addExercise(draft(name = "Second"))

        assertEquals(listOf("First", "Second"), storedExercises().map { it.name })
    }

    @Test
    fun observingOneExerciseEmitsItAndThenNullOnceDeleted() = runTest {
        val stored = addBenchPress()

        val before = datasource.observeExercise(stored.id).first()
        assertTrue(before is AppResult.Success)
        assertEquals(stored.id, before.data?.id)

        datasource.deleteExercise(stored.id)

        val after = datasource.observeExercise(stored.id).first()
        assertTrue(after is AppResult.Success)
        assertNull(after.data, "a deleted row is absent, not an error")
    }

    @Test
    fun updateCompletedSetsStoresAndReadsBackThePair() = runTest {
        val stored = addBenchPress()

        datasource.updateCompletedSets(stored.id, completedSets = 2, completedOn = today)

        val updated = storedExercises().single()
        assertEquals(2, updated.completedSets)
        assertEquals(today, updated.completedOn)
    }

    // The three tests below are what the three write shapes exist for.

    @Test
    fun updateDetailsLeavesTheCompletionPairAlone() = runTest {
        val stored = addBenchPress()
        datasource.updateCompletedSets(stored.id, completedSets = 2, completedOn = today)

        datasource.updateDetails(
            stored.id,
            draft(name = "Incline bench", comment = null, dayOfWeek = DayOfWeek.THURSDAY, setsCount = 3),
        )

        val updated = storedExercises().single()
        assertEquals("Incline bench", updated.name)
        assertNull(updated.comment)
        assertEquals(DayOfWeek.THURSDAY, updated.dayOfWeek)
        assertEquals(3, updated.setsCount)
        assertEquals(2, updated.completedSets, "editing an exercise must not un-tick it")
        assertEquals(today, updated.completedOn)
    }

    @Test
    fun updateWeightTouchesOnlyTheWeight() = runTest {
        val stored = addBenchPress()
        datasource.updateCompletedSets(stored.id, completedSets = 2, completedOn = today)

        datasource.updateWeight(stored.id, weight = 82.5)

        val updated = storedExercises().single()
        assertEquals(82.5, updated.weight)
        assertEquals("Bench press", updated.name, "an inline weight edit must not revert a rename")
        assertEquals("slow eccentric", updated.comment)
        assertEquals(4, updated.setsCount)
        assertEquals(8, updated.repsPerSet)
        assertEquals(2, updated.completedSets, "an inline weight edit must not un-tick the day")
        assertEquals(today, updated.completedOn)
    }

    @Test
    fun updateCompletedSetsLeavesEveryOtherColumnAlone() = runTest {
        val stored = addBenchPress()

        datasource.updateCompletedSets(stored.id, completedSets = 1, completedOn = today)

        val updated = storedExercises().single()
        assertEquals("Bench press", updated.name, "ticking a series must not revert an edit")
        assertEquals("slow eccentric", updated.comment)
        assertEquals(DayOfWeek.MONDAY, updated.dayOfWeek)
        assertEquals(4, updated.setsCount)
        assertEquals(8, updated.repsPerSet)
        assertEquals(60.0, updated.weight, "ticking a series must not revert a weight edit")
    }

    @Test
    fun updateWeightWithNullClearsIt() = runTest {
        val stored = addBenchPress()

        datasource.updateWeight(stored.id, weight = null)

        assertNull(storedExercises().single().weight, "an emptied field means bodyweight")
    }

    @Test
    fun deleteRemovesTheExercise() = runTest {
        val stored = addBenchPress()

        datasource.deleteExercise(stored.id)

        assertTrue(storedExercises().isEmpty())
    }

    @Test
    fun updatingDetailsOfAMissingRowReportsNotFound() = runTest {
        assertNotFound(datasource.updateDetails(404L, draft(name = "Gone")))
    }

    @Test
    fun updatingTheWeightOfAMissingRowReportsNotFound() = runTest {
        assertNotFound(datasource.updateWeight(404L, weight = 50.0))
    }

    @Test
    fun updatingTheCompletionOfAMissingRowReportsNotFound() = runTest {
        assertNotFound(datasource.updateCompletedSets(404L, completedSets = 1, completedOn = today))
    }

    @Test
    fun deletingAMissingRowReportsNotFound() = runTest {
        assertNotFound(datasource.deleteExercise(404L))
    }

    private fun assertNotFound(result: AppResult<Unit>) {
        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound, "the row is gone, and no retry will help")
    }
}
