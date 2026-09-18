package pl.lejdi.plannerkmp.feature.gym

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.serialization.Serializable

/**
 * Serializable, and keyed by id rather than by a whole exercise.
 *
 * Carrying the entity would mean the back stack could not be written to saved state (so rotation
 * would lose it), and that the edit screen worked from a snapshot taken at navigation time.
 */
sealed interface GymNavKey : NavKey {

    /** The weekday pager. */
    @Serializable
    data object Week : GymNavKey

    /**
     * The form for a new exercise, on the weekday the user added from.
     *
     * Separate from [ExerciseEdit] rather than one key with a nullable id *and* a weekday, because
     * that key can only be built by supplying a value one of its two uses never reads — and a
     * field that is "ignored here" is one a later change reads by mistake. Two keys each carry
     * exactly what their destination needs.
     *
     * The weekday travels as a value with kotlinx-datetime's own serializer rather than as a
     * hand-rolled ISO `Int`, which would be two call sites that have to agree about the encoding.
     */
    @Serializable
    data class ExerciseAdd(
        @Serializable(with = DayOfWeekSerializer::class)
        val dayOfWeek: DayOfWeek,
    ) : GymNavKey

    /**
     * The form for an exercise that exists. No weekday: it comes from the row when the form seeds,
     * which is also the only thing that could be right after the exercise has been moved.
     */
    @Serializable
    data class ExerciseEdit(val exerciseId: Long) : GymNavKey
}
