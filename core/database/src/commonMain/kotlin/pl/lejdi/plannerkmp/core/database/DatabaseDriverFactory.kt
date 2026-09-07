package pl.lejdi.plannerkmp.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

// No constructor declared here: Android needs a Context, iOS doesn't, so each
// platform's Koin module supplies the actual instance itself.
expect class DatabaseDriverFactory {
    fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, databaseName: String): SqlDriver
}
