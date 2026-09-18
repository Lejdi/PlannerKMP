package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** A stored exercise, with everything defaulted so a test names only what it is about. */
internal fun exercise(
    id: Long = 1L,
    name: String = "Bench press",
    comment: String? = null,
    dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    setsCount: Int = 4,
    repsPerSet: Int = 8,
    weight: Double? = 60.0,
    completedSets: Int = 0,
    completedOn: LocalDate? = null,
) = GymExercise(
    id = id,
    name = name,
    comment = comment,
    dayOfWeek = dayOfWeek,
    setsCount = setsCount,
    repsPerSet = repsPerSet,
    weight = weight,
    completedSets = completedSets,
    completedOn = completedOn,
)

/** The same, wrapped with a `doneSets` a use-case test can hand straight to [ToggleExerciseSet]. */
internal fun dayExercise(
    id: Long = 1L,
    setsCount: Int = 4,
    doneSets: Int = 0,
) = DayExercise(
    exercise = exercise(id = id, setsCount = setsCount),
    doneSets = doneSets,
)
