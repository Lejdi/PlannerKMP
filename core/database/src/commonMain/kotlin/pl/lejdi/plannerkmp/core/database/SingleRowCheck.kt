package pl.lejdi.plannerkmp.core.database

/**
 * Asserts that a mutation affected exactly one row, throwing otherwise so the
 * enclosing [safeQuery] turns it into an
 * [pl.lejdi.plannerkmp.core.common.AppResult.Failure].
 *
 * [rowsAffected] must come from the mutator's own `QueryResult<Long>` return
 * value, never from a separate `SELECT changes()` query: `changes()` is
 * per-connection SQLite state, and NativeSqliteDriver reads it on a reader
 * connection that never saw the write, so it always reports 0 on iOS.
 */
fun checkSingleRowAffected(rowsAffected: Long, operation: String) {
    check(rowsAffected == 1L) { "Expected 1 row affected by $operation, got $rowsAffected" }
}
