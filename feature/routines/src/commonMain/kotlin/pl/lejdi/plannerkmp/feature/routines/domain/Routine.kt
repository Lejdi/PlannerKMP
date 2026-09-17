package pl.lejdi.plannerkmp.feature.routines.domain

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.ValidationField

/** The inputs of the routine editor, for [DomainError.Validation] to point at. */
enum class RoutineField : ValidationField { Name }

/** An unsaved routine, and the only way to build a valid one. */
@ConsistentCopyVisibility
data class RoutineDraft private constructor(
    val name: String,
    val description: String?,
) {
    companion object {
        /**
         * A draft of something already stored, whose invariants were checked when it was created.
         *
         * `internal`, and used only by this module's own tests and fakes to seed storage without
         * going through validation they are not testing.
         */
        internal fun ofStored(name: String, description: String?) =
            RoutineDraft(name = name, description = description)

        fun create(name: String, description: String?): AppResult<RoutineDraft> {
            if (name.isBlank()) {
                return AppResult.Failure(DomainError.Validation(RoutineField.Name))
            }
            return AppResult.Success(
                RoutineDraft(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }
}

/**
 * A routine that exists in storage; [id] is real, never a `0L` stand-in for "unsaved".
 *
 * [completedOn] is the last date this routine was ticked — `null` if it never has been. It is
 * deliberately *not* a boolean: "done" is a question about today, and today is not a property of
 * the row. [TodayRoutine] is where that question gets answered, against a live clock.
 *
 * `internal` constructor for the same reason [RoutineDraft]'s is private: otherwise "the only way
 * to build a valid one" would hold for the draft and not for the type every other layer handles.
 */
@ConsistentCopyVisibility
data class Routine internal constructor(
    val id: Long,
    val name: String,
    val description: String?,
    val completedOn: LocalDate?,
)

/**
 * A routine plus the answer to the only question this screen asks of it.
 *
 * The flag is computed rather than stored, so it is correct the moment the date changes without
 * anything having written to the table — see [ObserveRoutinesForToday].
 */
data class TodayRoutine(
    val routine: Routine,
    val isDoneToday: Boolean,
) {
    /** Forwarded because the list's key and every event about a row need it. */
    val id: Long get() = routine.id
}
