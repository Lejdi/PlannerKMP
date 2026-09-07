package pl.lejdi.plannerkmp.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import pl.lejdi.plannerkmp.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class SamplePayload(val value: String)

class SafeRequestTest {

    @Test
    fun safeRequestMapsSuccessfulJsonResponse() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"value":"ok"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json() }
        }

        val result = client.safeRequest<SamplePayload> { url("https://example.test/sample") }

        assertEquals(AppResult.Success(SamplePayload("ok")), result)
    }

    @Test
    fun safeRequestMapsHttpErrorToFailure() = runTest {
        val client = HttpClient(
            MockEngine { respondError(HttpStatusCode.InternalServerError) },
        ) {
            install(ContentNegotiation) { json() }
        }

        val result = client.safeRequest<SamplePayload> { url("https://example.test/sample") }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeRequestMapsThrownExceptionToFailure() = runTest {
        val client = HttpClient(
            MockEngine { throw IllegalStateException("network down") },
        ) {
            install(ContentNegotiation) { json() }
        }

        val result = client.safeRequest<SamplePayload> { url("https://example.test/sample") }

        assertTrue(result is AppResult.Failure)
    }
}
