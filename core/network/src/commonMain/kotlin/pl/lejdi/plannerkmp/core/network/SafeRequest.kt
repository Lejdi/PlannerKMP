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

/**
 * Runs [block] as an HTTP request and maps the outcome to [AppResult] instead
 * of letting Ktor's exceptions escape to callers.
 */
suspend inline fun <reified T> HttpClient.safeRequest(
    crossinline block: HttpRequestBuilder.() -> Unit,
): AppResult<T> = try {
    val response: HttpResponse = request { block() }
    if (response.status.isSuccess()) {
        AppResult.Success(response.body())
    } else {
        AppResult.Failure(DomainError.Network("HTTP ${response.status.value}"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(DomainError.Network(e.message ?: "Unknown network error", e))
}
