package pl.lejdi.plannerkmp.feature.grocery.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.ValidationField

/** The inputs of the grocery editor, for [DomainError.Validation] to point at. */
enum class GroceryField : ValidationField { Name }

/** An unsaved grocery item, and the only way to build a valid one. */
@ConsistentCopyVisibility
data class GroceryItemDraft private constructor(
    val name: String,
    val description: String?,
) {
    companion object {
        /**
         * A draft of something already stored, whose invariants were checked when it was created.
         *
         * Undo re-adds a completed item and needs this; it used to call the constructor directly,
         * which is how "the only way to build a valid one" had a live counter-example in the same
         * feature.
         */
        internal fun ofStored(name: String, description: String?) =
            GroceryItemDraft(name = name, description = description)

        fun create(name: String, description: String?): AppResult<GroceryItemDraft> {
            if (name.isBlank()) {
                return AppResult.Failure(DomainError.Validation(GroceryField.Name))
            }
            return AppResult.Success(
                GroceryItemDraft(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }
}

/**
 * A grocery item that exists in storage; [id] is real, never a `0L` stand-in for "unsaved".
 *
 * `internal` constructor for the same reason [GroceryItemDraft]'s is private: otherwise "the only
 * way to build a valid one" held for the draft and not for the type every other layer actually
 * handles, which took a public constructor and a public `copy`. This feature still builds them —
 * [withId] and the datasource's row mapping — and so do its own tests.
 */
@ConsistentCopyVisibility
data class GroceryItem internal constructor(
    val id: Long,
    val name: String,
    val description: String?,
)

fun GroceryItemDraft.withId(id: Long): GroceryItem = GroceryItem(
    id = id,
    name = name,
    description = description,
)
