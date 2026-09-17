package pl.lejdi.plannerkmp.core.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Networking configuration supplied by the app, so a library module does not have to guess at build
 * types. Logging is off by default: at INFO Ktor writes every request URL to the log, which is not
 * something a release build should do.
 */
data class NetworkConfig(
    val enableLogging: Boolean = false,
    val requestTimeout: Duration = 30.seconds,
    val connectTimeout: Duration = 15.seconds,
)
