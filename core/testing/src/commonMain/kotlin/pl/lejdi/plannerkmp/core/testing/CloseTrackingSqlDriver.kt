package pl.lejdi.plannerkmp.core.testing

import app.cash.sqldelight.db.SqlDriver

/**
 * Passes everything through, and remembers whether it was closed.
 *
 * Interface delegation covers every other member, so this stays the one fact it is here to record.
 * Asserting on a *closed* driver's behaviour instead is not portable: a closed JDBC connection
 * throws, while the native driver's reaction is its own business.
 */
class CloseTrackingSqlDriver(private val delegate: SqlDriver) : SqlDriver by delegate {

    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
        delegate.close()
    }
}
