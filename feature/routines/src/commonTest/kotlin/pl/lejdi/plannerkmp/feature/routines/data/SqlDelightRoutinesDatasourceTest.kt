package pl.lejdi.plannerkmp.feature.routines.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.core.testing.TestCoroutineDispatchers
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.routines.domain.Routine
import pl.lejdi.plannerkmp.feature.routines.domain.RoutineDraft
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In `commonTest`, so it runs on the native driver too — see the grocery equivalent for why. */
class SqlDelightRoutinesDatasourceTest {

    private val today = LocalDate(2026, 9, 17)

    private lateinit var driver: SqlDriver
    private lateinit var datasource: SqlDelightRoutinesDatasource

    /** Stands in for the application scope the datasource is given in production. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        driver = inMemorySqlDriver(RoutinesDatabase.Schema)
        datasource = SqlDelightRoutinesDatasource(
            RoutinesDatabase(
                driver,
                routineEntityAdapter = RoutineEntity.Adapter(completedOnAdapter = LocalDateColumnAdapter),
            ).routineEntityQueries,
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

    private suspend fun storedRoutines(): List<Routine> {
        val result = datasource.observeRoutines().first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    private suspend fun addStretch(): Routine {
        datasource.addRoutine(RoutineDraft.ofStored(name = "Stretch", description = "Ten minutes"))
        return storedRoutines().single()
    }

    @Test
    fun addThenObserveReturnsTheRoutineNotYetDone() = runTest {
        val stored = addStretch()

        assertEquals("Stretch", stored.name)
        assertEquals("Ten minutes", stored.description)
        assertNull(stored.completedOn, "a new routine has never been completed")
    }

    @Test
    fun updateCompletedOnStoresAndReadsBackTheDate() = runTest {
        val stored = addStretch()

        datasource.updateCompletedOn(stored.id, today)

        assertEquals(today, storedRoutines().single().completedOn)
    }

    @Test
    fun updateCompletedOnWithNullClearsIt() = runTest {
        val stored = addStretch()
        datasource.updateCompletedOn(stored.id, today)

        datasource.updateCompletedOn(stored.id, null)

        assertNull(storedRoutines().single().completedOn)
    }

    /** The pair below is what the two write shapes exist for. */
    @Test
    fun updateDetailsLeavesCompletedOnAlone() = runTest {
        val stored = addStretch()
        datasource.updateCompletedOn(stored.id, today)

        datasource.updateDetails(stored.id, RoutineDraft.ofStored(name = "Stretch well", description = null))

        val updated = storedRoutines().single()
        assertEquals("Stretch well", updated.name)
        assertNull(updated.description)
        assertEquals(today, updated.completedOn, "renaming a routine must not un-tick it")
    }

    @Test
    fun updateCompletedOnLeavesTheTextColumnsAlone() = runTest {
        val stored = addStretch()

        datasource.updateCompletedOn(stored.id, today)

        val updated = storedRoutines().single()
        assertEquals("Stretch", updated.name, "ticking a routine must not revert a rename")
        assertEquals("Ten minutes", updated.description)
    }

    @Test
    fun deleteRemovesTheRoutine() = runTest {
        val stored = addStretch()

        datasource.deleteRoutine(stored.id)

        assertTrue(storedRoutines().isEmpty())
    }

    @Test
    fun deletingAMissingRowReportsNotFound() = runTest {
        val result = datasource.deleteRoutine(id = 404L)

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound)
    }

    @Test
    fun updatingDetailsOfAMissingRowReportsNotFound() = runTest {
        val result = datasource.updateDetails(404L, RoutineDraft.ofStored(name = "Gone", description = null))

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound)
    }

    @Test
    fun updatingCompletionOfAMissingRowReportsNotFound() = runTest {
        val result = datasource.updateCompletedOn(404L, today)

        assertTrue(result is AppResult.Failure)
        assertTrue(result.error is DomainError.NotFound)
    }

    @Test
    fun theListIsOrderedByIdAndReEmitsAfterAWrite() = runTest {
        datasource.addRoutine(RoutineDraft.ofStored(name = "First", description = null))
        datasource.addRoutine(RoutineDraft.ofStored(name = "Second", description = null))

        assertEquals(listOf("First", "Second"), storedRoutines().map { it.name })

        datasource.addRoutine(RoutineDraft.ofStored(name = "Third", description = null))

        assertEquals(listOf("First", "Second", "Third"), storedRoutines().map { it.name })
    }
}
