package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate

data class DashboardDay(val date: LocalDate, val tasks: List<Task>)
