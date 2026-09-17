package pl.lejdi.plannerkmp.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds

interface TodayProvider {
    /** Today, right now. For a one-shot read — a save, a validation, a single cleanup decision. */
    fun today(): LocalDate

    /**
     * Today, and again every time it changes.
     *
     * Anything that *renders* or *schedules against* a date has to use this rather than [today].
     * Reading the date once is correct only until midnight, and the reactive data layer does not
     * rescue it: a SQLDelight query flow re-emits when its table changes, so on a quiet night
     * nothing re-emits at all and the dashboard keeps drawing yesterday's window under today's
     * heading until the user happens to write something.
     */
    fun todayFlow(): Flow<LocalDate>
}

/**
 * @param scope the application-lifetime scope the shared midnight timer runs on. Taking it as a
 *   parameter is what lets one timer serve every collector — see [todayFlow].
 * @param clock the source of the current instant. Injected purely so the rollover below can be
 *   tested: reading [Clock.System] directly left the `while (true)` loop, the backwards-clock guard
 *   and the [distinctUntilChanged] unreachable from any test, because virtual time cannot move the
 *   real clock the delay was computed from.
 */
class SystemTodayProvider(
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : TodayProvider {

    override fun today(): LocalDate =
        clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /**
     * Emits immediately, then sleeps until the next local midnight and emits again.
     *
     * The delay is recomputed from the clock on every pass rather than fixed at 24h, so a timezone
     * change, a DST boundary, or a device that slept across midnight all resolve on the next
     * emission instead of accumulating drift. [distinctUntilChanged] covers waking a hair early,
     * and the lower bound stops a backwards clock jump from spinning the loop.
     */
    private val ticker: Flow<LocalDate> = flow {
        while (true) {
            val timeZone = TimeZone.currentSystemDefault()
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            emit(today)
            val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            delay((nextMidnight - now).coerceAtLeast(1.seconds))
        }
    }.distinctUntilChanged()

    /**
     * One timer, however many collectors.
     *
     * The flow above is cold, so every collector used to run its own `delay`-until-midnight loop —
     * the dashboard's and the cleanup's, at minimum, each holding a coroutine parked for up to 24
     * hours to compute the same date.
     *
     * `replayExpirationMillis = 0` is the part that matters for correctness: with an ordinary
     * `replay = 1` cache, a collector arriving after the last one left would be handed the date
     * from whenever the timer last ran, which on a device that slept overnight is yesterday. At
     * zero the cache is dropped as soon as the last subscriber goes, so a new subscriber always
     * restarts the upstream and reads the clock afresh.
     */
    private val shared: Flow<LocalDate> = ticker.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        replay = 1,
    )

    override fun todayFlow(): Flow<LocalDate> = shared
}
