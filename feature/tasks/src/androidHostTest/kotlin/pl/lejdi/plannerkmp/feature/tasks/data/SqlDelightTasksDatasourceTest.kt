package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.InMemoryKeyValueCache
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTasksDatasourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var datasource: SqlDelightTasksDatasource

    private val sampleTask = Task(
        id = 0,
        name = "Water plants",
        description = "Every houseplant",
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 6, 1),
        hour = LocalTime(9, 30),
        daysInterval = 7,
        asap = false,
    )

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TasksDatabase.Schema.create(driver)
        val queries = TasksDatabase(
            driver,
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        ).taskEntityQueries
        datasource = SqlDelightTasksDatasource(queries, InMemoryKeyValueCache())
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addThenGetAllRoundTripsEveryField() = runTest {
        datasource.addTask(sampleTask)

        val result = datasource.getAllTasks()

        assertTrue(result is AppResult.Success)
        val stored = result.data.single()
        assertEquals(sampleTask.name, stored.name)
        assertEquals(sampleTask.description, stored.description)
        assertEquals(sampleTask.startDate, stored.startDate)
        assertEquals(sampleTask.endDate, stored.endDate)
        assertEquals(sampleTask.hour, stored.hour)
        assertEquals(sampleTask.daysInterval, stored.daysInterval)
        assertEquals(sampleTask.asap, stored.asap)
    }

    @Test
    fun editUpdatesStoredFields() = runTest {
        datasource.addTask(sampleTask)
        val added = (datasource.getAllTasks() as AppResult.Success).data.single()

        datasource.editTask(added.copy(name = "Water plants twice", daysInterval = 3))

        val stored = (datasource.getAllTasks() as AppResult.Success).data.single()
        assertEquals("Water plants twice", stored.name)
        assertEquals(3, stored.daysInterval)
    }

    @Test
    fun deleteRemovesTheTask() = runTest {
        datasource.addTask(sampleTask)
        val added = (datasource.getAllTasks() as AppResult.Success).data.single()

        datasource.deleteTask(added.id)

        assertEquals(emptyList(), (datasource.getAllTasks() as AppResult.Success).data)
    }

    @Test
    fun deleteOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.deleteTask(999)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun lastCleanupDateIsNullUntilSet() = runTest {
        val result = datasource.getLastCleanupDate()

        assertTrue(result is AppResult.Success)
        assertNull(result.data)
    }

    @Test
    fun setThenGetLastCleanupDateRoundTrips() = runTest {
        datasource.setLastCleanupDate(LocalDate(2026, 9, 8))

        val result = datasource.getLastCleanupDate()

        assertEquals(AppResult.Success(LocalDate(2026, 9, 8)), result)
    }
}
