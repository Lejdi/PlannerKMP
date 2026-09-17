package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.Logger
import pl.lejdi.plannerkmp.core.testing.FakeTodayProvider
import pl.lejdi.plannerkmp.core.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksCleanupInitializerTest {

    private val today = LocalDate(2026, 9, 8)
    private val yesterday = today.minus(1, DateTimeUnit.DAY)
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private fun initializer(
        datasource: FakeTasksDatasource,
        store: FakeCleanupDateStore,
        logger: Logger,
        todayProvider: FakeTodayProvider = FakeTodayProvider(today),
    ) = TasksCleanupInitializer(
        UpdateTasksDates(datasource, store),
        todayProvider,
        logger,
    )

    @Test
    fun runsTheCleanupAtStartup() = runTest {
        val stale = oneTimeTask(id = 1, date = yesterday)
        val datasource = FakeTasksDatasource(initialTasks = listOf(stale))
        val store = FakeCleanupDateStore(initialDate = yesterday)

        // backgroundScope, because the initializer now collects for the lifetime of the app and
        // initialize() deliberately never returns.
        backgroundScope.launch { initializer(datasource, store, RecordingLogger()).initialize() }
        runCurrent()

        assertTrue(datasource.tasks.isEmpty(), "the overdue one-time task should have been dropped")
        assertEquals(today, store.lastCleanupDate)
    }

    /**
     * The reason the initializer collects instead of running once.
     *
     * "Once per app start" was never the requirement — "once per day" was. A phone that never
     * restarts the process, which is the normal case, used to go days without a cleanup, leaving
     * expired tasks sitting on the dashboard.
     */
    @Test
    fun runsAgainWhenTheDateRollsOver() = runTest {
        val expiringTomorrow = oneTimeTask(id = 1, date = today)
        val datasource = FakeTasksDatasource(initialTasks = listOf(expiringTomorrow))
        val store = FakeCleanupDateStore(initialDate = yesterday)
        val todayProvider = FakeTodayProvider(today)

        backgroundScope.launch {
            initializer(datasource, store, RecordingLogger(), todayProvider).initialize()
        }
        runCurrent()
        assertEquals(1, datasource.tasks.size, "nothing is stale yet on the day it is due")

        todayProvider.setToday(tomorrow)
        runCurrent()

        assertTrue(datasource.tasks.isEmpty(), "the task expired overnight and should be gone")
        assertEquals(tomorrow, store.lastCleanupDate)
    }

    /**
     * The cleanup is maintenance the user did not ask for and cannot act on. It used to raise a
     * snackbar on the dashboard; now it goes to the log, and a failure must not escape into the
     * startup scope.
     */
    @Test
    fun reportsAFailureToTheLogRatherThanThrowing() = runTest {
        val datasource = FakeTasksDatasource()
        val store = FakeCleanupDateStore(initialDate = yesterday)
        // The *write* fails: a failed read is deliberately survivable now, so it logs nothing.
        store.failNextWrite = true
        val logger = RecordingLogger()

        backgroundScope.launch { initializer(datasource, store, logger).initialize() }
        runCurrent()

        assertEquals(1, logger.errors.size)
        assertTrue(logger.errors.single().message.contains("cleanup"))
    }

    /** A failure on one day must not stop the collector, or the app loses every later cleanup. */
    @Test
    fun keepsRunningAfterAFailedPass() = runTest {
        val datasource = FakeTasksDatasource()
        val store = FakeCleanupDateStore(initialDate = yesterday)
        // The *write* fails: a failed read is deliberately survivable now, so it logs nothing.
        store.failNextWrite = true
        val todayProvider = FakeTodayProvider(today)
        val logger = RecordingLogger()

        backgroundScope.launch {
            initializer(datasource, store, logger, todayProvider).initialize()
        }
        runCurrent()
        assertEquals(1, logger.errors.size)

        todayProvider.setToday(tomorrow)
        runCurrent()

        assertEquals(tomorrow, store.lastCleanupDate, "the next day's pass still ran")
    }
}
