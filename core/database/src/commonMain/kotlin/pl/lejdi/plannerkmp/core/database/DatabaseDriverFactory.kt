package pl.lejdi.plannerkmp.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * An interface rather than an `expect class`: the platform implementations need different
 * constructors (Android needs a Context, iOS needs nothing), which an expect class cannot express,
 * and an interface additionally lets a test substitute an in-memory driver for the whole graph.
 */
interface DatabaseDriverFactory {
    fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, databaseName: String): SqlDriver
}
