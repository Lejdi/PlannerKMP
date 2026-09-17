package pl.lejdi.plannerkmp.core.testing

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * An in-memory driver for [schema], on whichever driver the running target actually ships.
 *
 * The point of the `expect` is platform coverage, not convenience. Every datasource test used to
 * build a `JdbcSqliteDriver` in `androidHostTest`, which `iosSimulatorArm64Test` never runs — so
 * the data layer was verified on a driver that ships nowhere, and `NativeSqliteDriver` was compiled
 * and never executed. That matters here specifically: the `rowsAffected` contract every mutation's
 * success depends on is per-driver, and the native one reports it differently enough that
 * `checkSingleRowAffected` carries a comment about it.
 *
 * The schema is created as part of building the driver, because the two platforms disagree about
 * who does it — the native driver takes the schema and applies it, the JDBC one does not.
 */
expect fun inMemorySqlDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver
