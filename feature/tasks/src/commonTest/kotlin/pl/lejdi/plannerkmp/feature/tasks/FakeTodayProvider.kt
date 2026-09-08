package pl.lejdi.plannerkmp.feature.tasks

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.TodayProvider

class FakeTodayProvider(private var date: LocalDate) : TodayProvider {
    override fun today(): LocalDate = date
    fun setToday(newDate: LocalDate) { date = newDate }
}
