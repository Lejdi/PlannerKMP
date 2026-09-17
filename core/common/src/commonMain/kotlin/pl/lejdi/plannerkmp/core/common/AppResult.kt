package pl.lejdi.plannerkmp.core.common

/**
 * Outcome of a datasource/network/cache operation, carried up through
 * usecases to the ViewModel layer instead of throwing.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}

/**
 * An interface rather than a sealed hierarchy: `core:common` cannot know every error a feature
 * needs, and squeezing feature-specific failures into [Unknown] loses the ability to react to
 * them. Features declare their own implementations; the four below are the shared ones.
 *
 * [message] is for logs and developers. It routinely carries raw driver text ("UNIQUE constraint
 * failed: ..."), so the UI layer must map the error to its own user-facing copy rather than
 * showing this verbatim.
 */
interface DomainError {
    val message: String
    val cause: Throwable?

    data class Network(override val message: String, override val cause: Throwable? = null) : DomainError
    data class Database(override val message: String, override val cause: Throwable? = null) : DomainError

    /**
     * The row the operation addressed is not there.
     *
     * Distinct from [Database] because it is the one storage failure that is not a malfunction and
     * that a retry cannot fix: the record is gone and will stay gone. Folding it into [Database]
     * left a screen editing a deleted task showing "save failed" on every attempt, with no way to
     * tell the user why and no way out.
     */
    data class NotFound(override val message: String, override val cause: Throwable? = null) : DomainError

    /**
     * One or more domain invariants broken by input.
     *
     * [fields] carries *every* offending input, not just the first one found. A validator that stops
     * at the first failure makes the user fix the name, submit, learn the interval is also wrong,
     * and submit again — one round trip per bad field, when the domain knew about all of them at
     * once. The presentation layer attaches each failure to the control that produced it instead of
     * showing a single screen-level error.
     *
     * There is deliberately no per-field message. Each failure used to carry one, written in English
     * in the domain layer, and no caller ever read it: both screens ask [validationFields] which of
     * their own inputs to mark and take the wording from their own Compose resources. So every
     * rule's copy existed twice — once unlocalized where it could never be shown, once where it
     * could — with nothing keeping the two in step. The field *is* the message; a screen that needs
     * to tell two reasons for one input apart declares two fields.
     *
     * [fields] is never empty.
     */
    data class Validation(
        val fields: Set<ValidationField>,
        override val cause: Throwable? = null,
    ) : DomainError {

        init {
            require(fields.isNotEmpty()) { "a Validation failure must name at least one field" }
        }

        constructor(field: ValidationField, cause: Throwable? = null) : this(setOf(field), cause)

        /** For the log only; the UI resolves [fields] to its own wording. */
        override val message: String get() = fields.joinToString(prefix = "invalid input: ")
    }

    data class Unknown(override val message: String, override val cause: Throwable? = null) : DomainError
}

/**
 * Which input a [DomainError.Validation] is about.
 *
 * Open for the same reason [DomainError] itself is: `core:common` cannot know the fields of a form
 * it has never seen. A closed enum here would mean every feature that gains an input edits the
 * shared module, and would leave every other feature pattern-matching on field names that belong to
 * somebody else. Each feature declares its own enum implementing this.
 */
interface ValidationField

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/**
 * Chains an operation that can itself fail, so callers stop writing
 * `if (x is Failure) return x; val y = (x as Success).data`.
 *
 * Inline, so `transform` may suspend when the caller is a suspend function.
 */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (DomainError) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Failure -> onFailure(error)
}

inline fun <T> AppResult<T>.onFailure(action: (DomainError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * The inputs of type [F] this error blames, or an empty set when it blames none.
 *
 * [ValidationField] is an open interface so that a feature can declare its own fields, and the
 * price of that openness used to be paid at every call site as
 * `(error as? DomainError.Validation)?.field as? TaskField` — two casts to ask one question, with
 * the accumulated failures unreachable behind the second. This asks it once, and returns every
 * field rather than only the first, so a screen can mark all its bad inputs from one submission.
 *
 * Fields belonging to another feature are dropped rather than crashing: a caller that receives one
 * has nothing to attach it to, and should fall back to a screen-level message.
 */
inline fun <reified F : ValidationField> DomainError.validationFields(): Set<F> =
    (this as? DomainError.Validation)
        ?.fields
        ?.filterIsInstance<F>()
        ?.toSet()
        .orEmpty()
