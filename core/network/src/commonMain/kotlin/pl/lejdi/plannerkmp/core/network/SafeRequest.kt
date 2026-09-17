package pl.lejdi.plannerkmp.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.common.Logger

@PublishedApi
internal const val NETWORK_TAG: String = "Network"

/**
 * Runs [block] as an HTTP request and maps the outcome to [AppResult] instead
 * of letting Ktor's exceptions escape to callers.
 *
 * [logger] and [operation] are required for the same reason
 * [pl.lejdi.plannerkmp.core.database.safeQuery]'s are: this is the single funnel through which a
 * transport exception stops being a throwable and becomes a value, so a failure not logged here is
 * a failure nobody can ever diagnose. The user sees "couldn't reach the server"; without this the
 * `SSLHandshakeException` that explains it reaches no one. [operation] names the call, since a
 * socket error's own message never says which request produced it.
 */
suspend inline fun <reified T> HttpClient.safeRequest(
    logger: Logger,
    operation: String,
    crossinline block: HttpRequestBuilder.() -> Unit,
): AppResult<T> = try {
    val response: HttpResponse = request { block() }
    if (response.status.isSuccess()) {
        AppResult.Success(response.body())
    } else {
        logger.error(NETWORK_TAG, "$operation failed with HTTP ${response.status.value}")
        AppResult.Failure(DomainError.Network("HTTP ${response.status.value}"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    logger.error(NETWORK_TAG, "$operation failed", e)
    AppResult.Failure(DomainError.Network(e.message ?: "Unknown network error", e))
}
