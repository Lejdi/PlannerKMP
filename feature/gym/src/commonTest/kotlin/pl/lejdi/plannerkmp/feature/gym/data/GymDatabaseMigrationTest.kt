package pl.lejdi.plannerkmp.feature.gym.data

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.testing.inMemorySqlDriver
import pl.lejdi.plannerkmp.feature.gym.domain.GymExercise
import pl.lejdi.plannerkmp.feature.gym.domain.GymExerciseDraft
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Version 1 -> 2, run over rows written the way version 1 wrote them.
 *
 * `verifyMigrations` already proves the migrated *schema* equals a fresh one; it says nothing about
 * the *data*, which is the half a user who already has a plan cares about. In `commonTest`, so the
 * migration runs on NativeSqliteDriver as well as JDBC.
 */
class GymDatabaseMigrationTest {

    private val today = LocalDate(2026, 9, 18)

    private lateinit var driver: SqlDriver
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        driver = inMemorySqlDriver(VersionOneSchema)
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO gymExerciseEntity
                    (id, name, comment, dayOfWeek, setsCount, repsPerSet, weight, completedSets, completedOn)
                VALUES
                    (1, 'Bench press', 'slow eccentric', 1, 4, 8, 60.0, 2, '$today'),
                    (2, 'Pull-ups', NULL, 5, 3, 10, NULL, 0, NULL),
                    (7, 'Squat', NULL, 1, 5, 5, 100.0, 0, NULL)
            """.trimIndent(),
            parameters = 0,
        )
        GymDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = GymDatabase.Schema.version)
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        driver.close()
    }

    private suspend fun storedExercises(): List<GymExercise> {
        val result = gymDatasource(driver, appScope).observeExercises().first()
        assertTrue(result is AppResult.Success)
        return result.data
    }

    @Test
    fun everyExerciseKeepsItsOwnDayAsItsOnlyDay() = runTest {
        assertEquals(
            listOf(setOf(DayOfWeek.MONDAY), setOf(DayOfWeek.FRIDAY), setOf(DayOfWeek.MONDAY)),
            storedExercises().map { it.days },
        )
    }

    @Test
    fun idsWeightsAndTodaysTicksSurvive() = runTest {
        val bench = storedExercises().first()

        assertEquals(1L, bench.id)
        assertEquals("Bench press", bench.name)
        assertEquals("slow eccentric", bench.comment)
        assertEquals(4, bench.setsCount)
        assertEquals(8, bench.repsPerSet)
        assertEquals(60.0, bench.weight)
        assertEquals(2, bench.completedSets)
        assertEquals(today, bench.completedOn)
        assertNull(storedExercises()[1].weight, "bodyweight stays bodyweight")
    }

    /** AUTOINCREMENT's high-water mark moves with the rebuilt table, so no id is handed out twice. */
    @Test
    fun aNewExerciseAfterTheMigrationGetsAFreshId() = runTest {
        val datasource = gymDatasource(driver, appScope)

        datasource.addExercise(
            GymExerciseDraft.ofStored(
                name = "Deadlift",
                comment = null,
                days = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
                setsCount = 3,
                repsPerSet = 5,
                weight = 120.0,
            ),
        )

        val added = storedExercises().last()
        assertEquals(8L, added.id)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), added.days)
    }
}

/** The schema as version 1 shipped it — `GymExerciseEntity.sq` before the days moved out. */
private object VersionOneSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE gymExerciseEntity (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    comment TEXT,
                    dayOfWeek INTEGER NOT NULL,
                    setsCount INTEGER NOT NULL,
                    repsPerSet INTEGER NOT NULL,
                    weight REAL,
                    completedSets INTEGER NOT NULL DEFAULT 0,
                    completedOn TEXT
                )
            """.trimIndent(),
            parameters = 0,
        )
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}
