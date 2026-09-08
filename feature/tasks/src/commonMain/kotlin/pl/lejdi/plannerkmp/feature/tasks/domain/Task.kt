package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class Task(
    val id: Long,
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hour: LocalTime?,
    val daysInterval: Int,
    val asap: Boolean,
)

enum class TaskType { Asap, OneTime, Periodic }

val Task.type: TaskType
    get() = when {
        asap -> TaskType.Asap
        daysInterval > 0 -> TaskType.Periodic
        else -> TaskType.OneTime
    }
