package pl.lejdi.plannerkmp.core.database

/**
 * Thrown by [checkSingleRowAffected] when a mutation matched nothing, so [safeQuery] can map it to
 * [pl.lejdi.plannerkmp.core.common.DomainError.NotFound] rather than to the generic database
 * failure every other exception becomes.
 */
class RowNotFoundException(operation: String) :
    Exception("$operation affected no rows: the row no longer exists")

/**
 * Asserts that a mutation affected exactly one row, throwing otherwise so the
 * enclosing [safeQuery] turns it into an
 * [pl.lejdi.plannerkmp.core.common.AppResult.Failure].
 *
 * Zero rows and "the driver blew up" are deliberately different throwables: the first means the
 * record is gone and no retry will bring it back, which is something a screen can act on, and the
 * second is a malfunction. Collapsing them left the UI unable to tell the two apart.
 *
 * [rowsAffected] must come from the mutator's own `QueryResult<Long>` return
 * value, never from a separate `SELECT changes()` query: `changes()` is
 * per-connection SQLite state, and NativeSqliteDriver reads it on a reader
 * connection that never saw the write, so it always reports 0 on iOS.
 */
fun checkSingleRowAffected(rowsAffected: Long, operation: String) {
    if (rowsAffected == 0L) throw RowNotFoundException(operation)
    check(rowsAffected == 1L) { "Expected 1 row affected by $operation, got $rowsAffected" }
}
