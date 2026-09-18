package pl.lejdi.plannerkmp.feature.gym.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.ValidationField

/** The inputs of the exercise form, for [DomainError.Validation] to point at. */
enum class GymField : ValidationField { Name, Sets, Reps, Weight }

/** An unsaved exercise, and the only way to build a valid one. */
@ConsistentCopyVisibility
data class GymExerciseDraft private constructor(
    val name: String,
    val comment: String?,
    val dayOfWeek: DayOfWeek,
    val setsCount: Int,
    val repsPerSet: Int,
    val weight: Double?,
) {
    companion object {
        /**
         * Enough series to tick without the row becoming a wall of checkboxes.
         *
         * This bound is a UI constraint expressed in the domain on purpose: the list draws one box
         * per series, so a fat-fingered 500 is not merely odd data, it is a screen that cannot be
         * used. The screen reads the same constant rather than carrying a second copy of it.
         */
        const val MAX_SETS = 20
        const val MAX_REPS = 1000
        const val MAX_WEIGHT = 1000.0

        /**
         * A draft of something already stored, whose invariants were checked when it was created.
         *
         * `internal`, and used only by this module's own tests and fakes to seed storage without
         * going through validation they are not testing.
         */
        internal fun ofStored(
            name: String,
            comment: String?,
            dayOfWeek: DayOfWeek,
            setsCount: Int,
            repsPerSet: Int,
            weight: Double?,
        ) = GymExerciseDraft(
            name = name,
            comment = comment,
            dayOfWeek = dayOfWeek,
            setsCount = setsCount,
            repsPerSet = repsPerSet,
            weight = weight,
        )

        /**
         * The counts and the weight arrive nullable because the form holds them as text for as
         * long as the user is typing in them: "not a number" and "out of range" are the same
         * failure to the user, and so the same field.
         *
         * Every offending input is collected before returning, so one Save marks all of them —
         * see [DomainError.Validation] for why a validator that stops at the first failure costs
         * the user a round trip per bad field.
         */
        fun create(
            name: String,
            comment: String?,
            dayOfWeek: DayOfWeek,
            setsCount: Int?,
            repsPerSet: Int?,
            weight: Double?,
        ): AppResult<GymExerciseDraft> {
            val validSets = setsCount?.takeIf { it in 1..MAX_SETS }
            val validReps = repsPerSet?.takeIf { it in 1..MAX_REPS }
            val invalid = buildSet<ValidationField> {
                if (name.isBlank()) add(GymField.Name)
                if (validSets == null) add(GymField.Sets)
                if (validReps == null) add(GymField.Reps)
                if (!isWeightValid(weight)) add(GymField.Weight)
            }
            if (invalid.isNotEmpty()) return AppResult.Failure(DomainError.Validation(invalid))
            return AppResult.Success(
                GymExerciseDraft(
                    name = name.trim(),
                    comment = comment?.trim()?.takeIf { it.isNotEmpty() },
                    dayOfWeek = dayOfWeek,
                    // Non-null here: an absent or out-of-range count is already in `invalid` above,
                    // and that branch has returned.
                    setsCount = requireNotNull(validSets),
                    repsPerSet = requireNotNull(validReps),
                    weight = weight,
                ),
            )
        }

        /**
         * A null weight is bodyweight, not a mistake.
         *
         * The range does the work of a finiteness check too: NaN compares false against
         * everything, and neither infinity falls inside it. So a non-finite weight is rejected
         * here rather than reaching storage, where it becomes a row nothing can render and a
         * column no comparison orders.
         */
        private fun isWeightValid(weight: Double?): Boolean =
            weight == null || weight in 0.0..MAX_WEIGHT
    }
}

/**
 * An exercise that exists in storage; [id] is real, never a `0L` stand-in for "unsaved".
 *
 * [dayOfWeek] is what the exercise belongs to, rather than a date: the plan repeats every week, so
 * there is no row per date and no history to accumulate.
 *
 * [completedSets] is how many series were ticked *on [completedOn]* — the last date anything was
 * ticked here, `null` if nothing ever was. Neither is a boolean and neither is a row per day:
 * "done" is a question about today, and today is not a property of the row. [DayExercise] is where
 * that question gets answered, against a live clock.
 *
 * `internal` constructor for the same reason [GymExerciseDraft]'s is private: otherwise "the only
 * way to build a valid one" would hold for the draft and not for the type every other layer
 * handles.
 */
@ConsistentCopyVisibility
data class GymExercise internal constructor(
    val id: Long,
    val name: String,
    val comment: String?,
    val dayOfWeek: DayOfWeek,
    val setsCount: Int,
    val repsPerSet: Int,
    val weight: Double?,
    val completedSets: Int,
    val completedOn: LocalDate?,
)
