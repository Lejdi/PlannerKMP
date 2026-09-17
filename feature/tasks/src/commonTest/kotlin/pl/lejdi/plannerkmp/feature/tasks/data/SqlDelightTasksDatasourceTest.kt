package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.InMemoryKeyValueCache
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupDateStore
import pl.lejdi.plannerkmp.feature.tasks.domain.CleanupPlan
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskDraft
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskSchedule
import pl.lejdi.plannerkmp.feature.tasks.domain.startingFrom
import pl.lejdi.plannerkmp.feature.tasks.domain.withId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In `commonTest`, so it runs against `NativeSqliteDriver` on iOS as well as the JVM driver.
 *
 * It used to live in `androidHostTest`, which `iosSimulatorArm64Test` never runs — so the entire
 * data layer was verified on a driver that ships nowhere. That is not academic here: the
 * `rowsAffected` contract these tests turn on is per-driver, and `checkSingleRowAffected` carries a
 * comment about the native driver getting it wrong when read the obvious way.
 */
class SqlDelightTasksDatasourceTest {

    private lateinit var driver: SqlDriver
    private lateinit var datasource: SqlDelightTasksDatasource
    private lateinit var cleanupDateStore: CleanupDateStore
    private var driverClosed = false

    /** Stands in for the application scope the datasource is given in production. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val sampleSchedule = TaskSchedule.Periodic(
        startDate = LocalDate(2026, 1, 1),
        daysInterval = 7,
        endDate = LocalDate(2026, 6, 1),
        hour = LocalTime(9, 30),
    )

    private val sampleDraft = TaskDraft.ofStored(
        name = "Water plants",
        description = "Every houseplant",
        schedule = sampleSchedule,
    )

    @BeforeTest
    fun setUp() {
        driverClosed = false
        driver = inMemorySqlDriver(TasksDatabase.Schema)
        val queries = TasksDatabase(
            driver,
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        ).taskEntityQueries
        // Unconfined as the io dispatcher: safeQuery's withContext then runs the
        // blocking driver call inline on the test thread, so runTest never sees an
        // idle scheduler and fast-forwards into safeQuery's timeout.
        val dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined)
        datasource = SqlDelightTasksDatasource(queries, appScope, dispatchers, NoOpLogger())
        cleanupDateStore = KeyValueCleanupDateStore(InMemoryKeyValueCache())
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        // One test closes the driver itself to force a failure; the native driver does not promise
        // that closing twice is harmless the way the JDBC one does.
        if (!driverClosed) driver.close()
    }

    private suspend fun storedTasks(): List<Task> =
        (datasource.observeTasks().first() as AppResult.Success).data

    /**
     * Every schedule kind, not just the periodic one.
     *
     * The flat `asap`/`daysInterval` columns are read back through a `when` whose branch *order* is
     * the invariant, and only `Periodic` was ever round-tripped — so swapping the first two arms, or
     * dropping `asap` from the update statement, turned every ASAP task into a one-off (gone
     * tomorrow) or every one-off into an ASAP (back every day) with a green suite.
     */
    @Test
    fun everyScheduleKindRoundTripsThroughTheFlatColumns() = runTest {
        val schedules = listOf(
            TaskSchedule.Asap(createdOn = LocalDate(2026, 3, 4), hour = LocalTime(7, 15)),
            TaskSchedule.OneTime(date = LocalDate(2026, 4, 5), hour = null),
            sampleSchedule,
        )

        schedules.forEach { schedule ->
            datasource.addTask(TaskDraft.ofStored(name = "T", description = null, schedule = schedule))
        }

        val stored = storedTasks().map { it.schedule }
        assertEquals(schedules, stored)
    }

    @Test
    fun addThenReadRoundTripsEveryField() = runTest {
        datasource.addTask(sampleDraft)

        val stored = storedTasks().single()

        assertEquals(sampleDraft.name, stored.name)
        assertEquals(sampleDraft.description, stored.description)
        // One assertion instead of five: the schedule round-trips as a whole, which is also the
        // only way to catch a mapping that loses *which kind* of schedule it was.
        assertEquals(sampleSchedule, stored.schedule)
    }

    @Test
    fun editUpdatesStoredFields() = runTest {
        datasource.addTask(sampleDraft)
        val added = storedTasks().single()

        datasource.editTask(
            added.copy(
                name = "Water plants twice",
                schedule = sampleSchedule.copy(daysInterval = 3),
            ),
        )

        val stored = storedTasks().single()
        assertEquals("Water plants twice", stored.name)
        assertEquals(sampleSchedule.copy(daysInterval = 3), stored.schedule)
    }

    /** The whole point of the narrow statement: a reschedule cannot touch what the user typed. */
    @Test
    fun rescheduleLeavesNameAndDescriptionAlone() = runTest {
        datasource.addTask(sampleDraft)
        val added = storedTasks().single()
        val staleSnapshot = added.copy(name = "Stale name", description = "Stale description")

        val result = datasource.rescheduleTask(staleSnapshot.startingFrom(LocalDate(2026, 2, 1)))

        assertTrue(result is AppResult.Success)
        val stored = storedTasks().single()
        assertEquals(sampleDraft.name, stored.name)
        assertEquals(sampleDraft.description, stored.description)
        assertEquals(LocalDate(2026, 2, 1), (stored.schedule as TaskSchedule.Periodic).startDate)
    }

    @Test
    fun observeTaskEmitsOneRowOrNull() = runTest {
        datasource.addTask(sampleDraft)
        val added = storedTasks().single()

        val found = datasource.observeTask(added.id).first()
        val missing = datasource.observeTask(999).first()

        assertEquals(AppResult.Success(added), found)
        assertTrue(missing is AppResult.Success)
        assertNull(missing.data)
    }

    /** The edit screen depends on this: a row deleted elsewhere has to reach it as a null. */
    @Test
    fun observeTaskReEmitsWhenTheRowChangesAndNullsWhenItIsDeleted() = runTest {
        datasource.addTask(sampleDraft)
        val added = storedTasks().single()

        val emissions = mutableListOf<AppResult<Task?>>()
        val job = launch { datasource.observeTask(added.id).toList(emissions) }
        runCurrent()

        datasource.editTask(added.copy(name = "Renamed"))
        runCurrent()
        datasource.deleteTask(added.id)
        runCurrent()
        job.cancel()

        assertEquals("Renamed", (emissions[1] as AppResult.Success).data?.name)
        assertNull((emissions.last() as AppResult.Success).data)
    }

    /**
     * Zero rows affected is the row being gone, not a driver malfunction — the distinction the
     * edit screen needs to tell "this task was deleted" from "saving broke". This is the assertion
     * that only means something once it runs on the native driver too.
     */
    @Test
    fun mutatingAMissingRowFailsAsNotFound() = runTest {
        val edit = datasource.editTask(sampleDraft.withId(999))
        val reschedule = datasource.rescheduleTask(sampleDraft.withId(999))
        val delete = datasource.deleteTask(999)

        assertTrue(edit is AppResult.Failure && edit.error is DomainError.NotFound)
        assertTrue(reschedule is AppResult.Failure && reschedule.error is DomainError.NotFound)
        assertTrue(delete is AppResult.Failure && delete.error is DomainError.NotFound)
    }

    @Test
    fun deleteRemovesTheTask() = runTest {
        datasource.addTask(sampleDraft)
        val added = storedTasks().single()

        datasource.deleteTask(added.id)

        assertEquals(emptyList(), storedTasks())
    }

    @Test
    fun observeTasksEmitsAgainAfterAWrite() = runTest {
        val emissions = mutableListOf<List<Task>>()
        val job = launch {
            datasource.observeTasks().collect { result ->
                if (result is AppResult.Success) emissions += result.data
            }
        }
        runCurrent()

        datasource.addTask(sampleDraft)
        runCurrent()
        job.cancel()

        assertEquals(2, emissions.size)
        assertTrue(emissions.first().isEmpty())
        assertEquals("Water plants", emissions.last().single().name)
    }

    @Test
    fun runCleanupDeletesAndReschedulesInOneGo() = runTest {
        datasource.addTask(TaskDraft.ofStored("Keep", sampleDraft.description, sampleSchedule))
        datasource.addTask(TaskDraft.ofStored("Drop", sampleDraft.description, sampleSchedule))

        val result = datasource.runCleanup { tasks ->
            CleanupPlan(
                deletedIds = tasks.filter { it.name == "Drop" }.map { it.id },
                updatedTasks = tasks.filter { it.name == "Keep" }
                    .map { it.startingFrom(LocalDate(2026, 2, 1)) },
            )
        }

        assertTrue(result is AppResult.Success)
        val after = storedTasks()
        assertEquals(1, after.size)
        assertEquals("Keep", after.single().name)
        assertEquals(
            LocalDate(2026, 2, 1),
            (after.single().schedule as TaskSchedule.Periodic).startDate,
        )
    }

    /**
     * The planner sees the rows the writes will land on.
     *
     * Reading through a separate call first is what let a task edited in between be rolled forward
     * from an anchor that no longer existed.
     */
    @Test
    fun runCleanupPlansFromTheRowsInsideItsOwnTransaction() = runTest {
        datasource.addTask(sampleDraft)

        var seen: List<Task> = emptyList()
        datasource.runCleanup { tasks ->
            seen = tasks
            CleanupPlan(emptyList(), emptyList())
        }

        assertEquals(1, seen.size)
        assertEquals("Water plants", seen.single().name)
    }

    @Test
    fun runCleanupWithNothingToDoIsASuccess() = runTest {
        val result = datasource.runCleanup { CleanupPlan(emptyList(), emptyList()) }

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun lastCleanupDateIsNullUntilSet() = runTest {
        val result = cleanupDateStore.getLastCleanupDate()

        assertTrue(result is AppResult.Success)
        assertNull(result.data)
    }

    @Test
    fun setThenGetLastCleanupDateRoundTrips() = runTest {
        cleanupDateStore.setLastCleanupDate(LocalDate(2026, 9, 8))

        val result = cleanupDateStore.getLastCleanupDate()

        assertEquals(AppResult.Success(LocalDate(2026, 9, 8)), result)
    }

    @Test
    fun observeTasksSurfacesADatabaseFailureAsAResult() = runTest {
        driver.close()
        driverClosed = true

        val result = datasource.observeTasks().first()

        assertTrue(result is AppResult.Failure)
    }
}
