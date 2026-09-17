package pl.lejdi.plannerkmp.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.TodayProvider

/**
 * A [TodayProvider] whose date the test moves.
 *
 * Lives here rather than in a feature's test source set: it fakes a `core:common` type, and while
 * it sat inside `:feature:tasks` neither `core:common`'s own tests nor any second feature could
 * reach it without copying it.
 */
class FakeTodayProvider(initialToday: LocalDate) : TodayProvider {

    private val state = MutableStateFlow(initialToday)

    override fun today(): LocalDate = state.value

    override fun todayFlow(): Flow<LocalDate> = state.asStateFlow()

    /** Rolls the date over, as local midnight does in production. */
    fun setToday(date: LocalDate) {
        state.value = date
    }
}
