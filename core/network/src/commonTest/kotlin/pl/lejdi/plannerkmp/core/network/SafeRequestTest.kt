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
import pl.lejdi.plannerkmp.core.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class SamplePayload(val value: String)

class SafeRequestTest {

    private val logger = RecordingLogger()

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

        val result = client.safeRequest<SamplePayload>(logger, "sample") { url("https://example.test/sample") }

        assertEquals(AppResult.Success(SamplePayload("ok")), result)
    }

    @Test
    fun safeRequestMapsHttpErrorToFailure() = runTest {
        val client = HttpClient(
            MockEngine { respondError(HttpStatusCode.InternalServerError) },
        ) {
            install(ContentNegotiation) { json() }
        }

        val result = client.safeRequest<SamplePayload>(logger, "sample") { url("https://example.test/sample") }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeRequestMapsThrownExceptionToFailure() = runTest {
        val client = HttpClient(
            MockEngine { throw IllegalStateException("network down") },
        ) {
            install(ContentNegotiation) { json() }
        }

        val result = client.safeRequest<SamplePayload>(logger, "sample") { url("https://example.test/sample") }

        assertTrue(result is AppResult.Failure)
    }

    /**
     * The reason [safeRequest] takes a logger at all, and the exact counterpart of the assertion
     * `safeQuery` has: this is the one place a transport exception stops being a throwable and
     * becomes a value, so anything not logged here is gone for good.
     */
    @Test
    fun safeRequestLogsTheCauseItSwallows() = runTest {
        val thrown = IllegalStateException("connection reset")
        val client = HttpClient(MockEngine { throw thrown }) {
            install(ContentNegotiation) { json() }
        }

        client.safeRequest<SamplePayload>(logger, "fetch sample") { url("https://example.test/sample") }

        val logged = logger.errors.single()
        val cause = logged.cause
        assertContains(logged.message, "fetch sample", message = "the log has to name the failing call")
        assertTrue(cause is IllegalStateException, "the throwable itself must reach the log")
        assertEquals(thrown.message, cause.message)
    }

    @Test
    fun safeRequestLogsAnUnsuccessfulStatus() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }) {
            install(ContentNegotiation) { json() }
        }

        client.safeRequest<SamplePayload>(logger, "fetch sample") { url("https://example.test/sample") }

        assertContains(logger.errors.single().message, "500")
    }

    @Test
    fun safeRequestLogsNothingOnSuccess() = runTest {
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

        client.safeRequest<SamplePayload>(logger, "fetch sample") { url("https://example.test/sample") }

        assertTrue(logger.errors.isEmpty(), "a successful request must not log an error")
    }
}
