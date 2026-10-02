package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.db.QueryResult
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
        datasource = gymDatasource(driver, appScope)
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
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        setsCount: Int = 4,
        repsPerSet: Int = 8,
        weight: Double? = 60.0,
    ) = GymExerciseDraft.ofStored(
        name = name,
        comment = comment,
        days = days,
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
        assertEquals(setOf(DayOfWeek.MONDAY), stored.days)
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
            datasource.addExercise(draft(name = weekday.name, days = setOf(weekday)))
        }

        val stored = storedExercises()
        assertEquals(DayOfWeek.entries.map { setOf(it) }, stored.map { it.days })
        assertEquals(DayOfWeek.entries.map { it.name }, stored.map { it.name })
    }

    /** One row, however many days: the join folds back into a single exercise. */
    @Test
    fun anExerciseOnSeveralDaysIsOneExercise() = runTest {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        datasource.addExercise(draft(days = days))

        val stored = storedExercises().single()
        assertEquals(days, stored.days)

        val single = datasource.observeExercise(stored.id).first()
        assertTrue(single is AppResult.Success)
        assertEquals(days, single.data?.days)
    }

    /** The ISO day number is stored so that the query's own ORDER BY is calendar order. */
    @Test
    fun theDaysComeBackInCalendarOrder() = runTest {
        datasource.addExercise(draft(days = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            storedExercises().single().days.toList(),
        )
    }

    /** Within a page, exercises stay in the order they were added in. */
    @Test
    fun theListIsInTheOrderTheExercisesWereAdded() = runTest {
        datasource.addExercise(draft(name = "First", days = setOf(DayOfWeek.FRIDAY)))
        datasource.addExercise(draft(name = "Second", days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        datasource.addExercise(draft(name = "Third", days = setOf(DayOfWeek.MONDAY)))

        assertEquals(listOf("First", "Second", "Third"), storedExercises().map { it.name })
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
            draft(
                name = "Incline bench",
                comment = null,
                days = setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                setsCount = 3,
            ),
        )

        val updated = storedExercises().single()
        assertEquals("Incline bench", updated.name)
        assertNull(updated.comment)
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY), updated.days, "days are replaced, not added to")
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
        assertEquals(setOf(DayOfWeek.MONDAY), updated.days)
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
        assertEquals(0L, dayRowCount(), "there is no foreign key, so the delete takes its days itself")
    }

    /** The row is checked before its days are rewritten, so a missing one leaves nothing behind. */
    @Test
    fun updatingDetailsOfAMissingRowWritesNoDays() = runTest {
        datasource.updateDetails(404L, draft(name = "Gone"))

        assertEquals(0L, dayRowCount())
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

    private fun dayRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM gymExerciseDay",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
        parameters = 0,
    ).value ?: 0L

    private fun assertNotFound(result: AppResult<Unit>) {
        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound, "the row is gone, and no retry will help")
    }
}

/**
 * The datasource as the Koin module builds it, over whichever driver a test supplies.
 *
 * Unconfined as the io dispatcher: safeQuery's withContext then runs the blocking driver call
 * inline on the test thread, so runTest never sees an idle scheduler and fast-forwards into
 * safeQuery's timeout.
 */
internal fun gymDatasource(driver: SqlDriver, appScope: CoroutineScope) = SqlDelightGymDatasource(
    GymDatabase(
        driver,
        gymExerciseDayAdapter = GymExerciseDay.Adapter(dayOfWeekAdapter = DayOfWeekColumnAdapter),
        gymExerciseEntityAdapter = GymExerciseEntity.Adapter(completedOnAdapter = LocalDateColumnAdapter),
    ).gymExerciseEntityQueries,
    appScope,
    TestCoroutineDispatchers(Dispatchers.Unconfined),
    NoOpLogger(),
)
