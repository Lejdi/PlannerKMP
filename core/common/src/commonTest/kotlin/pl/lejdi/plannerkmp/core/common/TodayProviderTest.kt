package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A clock the test moves. Virtual time cannot move a real one, which is the whole problem. */
private class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now

    operator fun plusAssign(duration: Duration) {
        now += duration
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayProviderTest {

    private val timeZone = TimeZone.currentSystemDefault()

    /** 23:59:00 local on 2026-09-08, one minute short of the rollover. */
    private fun justBeforeMidnight(): MutableClock =
        MutableClock(LocalDate(2026, 9, 9).atStartOfDayIn(timeZone) - 1.minutes)

    @Test
    fun todayMatchesTheFirstEmissionOfTheFlow() = runTest {
        val clock = justBeforeMidnight()
        val provider = SystemTodayProvider(TestScope(testScheduler), clock)

        assertEquals(LocalDate(2026, 9, 8), provider.today())
        assertEquals(provider.today(), provider.todayFlow().first())
    }

    /**
     * The midnight rollover itself — the only interesting behaviour in the class, and previously
     * unreachable from any test.
     *
     * `SystemTodayProvider` read `Clock.System` directly, so neither the second pass of the
     * `while (true)` loop nor the `distinctUntilChanged` could be driven: `advanceTimeBy` moves
     * *virtual* time, while the delay was computed from the real clock. The old test for this
     * compared two reads of the same real date and would have passed with the sharing config
     * deleted.
     */
    @Test
    fun theDateRollsOverAtLocalMidnight() = runTest {
        val clock = justBeforeMidnight()
        val provider = SystemTodayProvider(TestScope(testScheduler), clock)

        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch { provider.todayFlow().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 8)), seen)

        // The provider parked until the next local midnight; move both clocks past it together.
        clock += 1.minutes
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(listOf(LocalDate(2026, 9, 8), LocalDate(2026, 9, 9)), seen)
    }

    /**
     * Waking without the date having changed must not re-emit.
     *
     * The loop recomputes its delay from the clock on every pass, so a device that wakes a hair
     * early — or whose park expires against a clock that has not moved as far as expected — comes
     * round with the same date. `distinctUntilChanged` is what stops that reaching collectors as a
     * spurious "the date changed", which for the dashboard means a full recompute of the window and
     * for the cleanup an extra pass.
     */
    @Test
    fun wakingWithoutTheDateChangingEmitsNothing() = runTest {
        val clock = justBeforeMidnight()
        val provider = SystemTodayProvider(TestScope(testScheduler), clock)

        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch { provider.todayFlow().collect { seen += it } }
        runCurrent()

        // Virtual time passes the park, but the clock has not moved, so it is still the 8th.
        advanceTimeBy(5.minutes)
        runCurrent()

        assertEquals(listOf(LocalDate(2026, 9, 8)), seen, "the same date must not be emitted twice")

        // And once the clock does move, the rollover still arrives.
        clock += 5.minutes
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(listOf(LocalDate(2026, 9, 8), LocalDate(2026, 9, 9)), seen)
    }

    /**
     * One timer, however many collectors.
     *
     * The flow behind this is a cold `while (true) { emit; delay-until-midnight }`, so before it was
     * shared every collector ran its own copy — the dashboard's and the daily cleanup's at minimum,
     * each holding a coroutine parked for up to 24 hours to compute the same date. Sharing is
     * observable as the two collectors seeing the same flow instance and both being served.
     */
    @Test
    fun everyCollectorIsServedFromOneSharedFlow() = runTest {
        val scope = TestScope(testScheduler)
        val provider = SystemTodayProvider(scope, justBeforeMidnight())

        assertTrue(
            provider.todayFlow() === provider.todayFlow(),
            "each call handed back a fresh cold flow, so each collector would run its own timer",
        )

        val seen = mutableListOf<LocalDate>()
        val first = backgroundScope.launch { provider.todayFlow().collect { seen += it } }
        val second = backgroundScope.launch { provider.todayFlow().collect { seen += it } }
        runCurrent()

        assertEquals(2, seen.size, "both collectors should have been served the current date")
        assertEquals(seen[0], seen[1])
        first.cancel()
        second.cancel()
    }

    /**
     * The replay cache must not outlive its subscribers.
     *
     * With a plain `replay = 1`, a collector arriving after the last one left is handed the date
     * from whenever the timer last ran — which on a device that slept overnight is yesterday, the
     * exact bug `todayFlow` exists to prevent. `replayExpirationMillis = 0` drops the cache as soon
     * as the last subscriber goes, so a new subscriber restarts the upstream and reads the clock
     * afresh. Only a moving clock can tell the two apart; the previous version of this test read the
     * real one twice, so both reads saw the same date and it passed either way.
     */
    @Test
    fun aLateSubscriberIsNotHandedAStaleReplayedDate() = runTest {
        val clock = justBeforeMidnight()
        val provider = SystemTodayProvider(TestScope(testScheduler), clock)

        val firstRead = provider.todayFlow().first()
        assertEquals(LocalDate(2026, 9, 8), firstRead)

        // Nobody is subscribed across this gap — the device slept through midnight.
        clock += 30.hours
        advanceTimeBy(30.hours)

        val secondRead = provider.todayFlow().first()

        assertEquals(LocalDate(2026, 9, 10), secondRead, "the late subscriber read the clock afresh")
        assertEquals(provider.today(), secondRead)
    }
}
