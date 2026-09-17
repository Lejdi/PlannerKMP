package pl.lejdi.plannerkmp.core.testing

import pl.lejdi.plannerkmp.core.common.Logger

/** One call to a [Logger], kept so a test can assert on what was reported and why. */
data class LogEntry(val tag: String, val message: String, val cause: Throwable? = null)

/**
 * Records everything, so a test can assert that a swallowed throwable was at least reported.
 *
 * Written out three times before this — twice recording `Pair<String, Throwable?>` and once
 * recording a bare `String`, so the same assertion read differently depending on which module you
 * were in.
 */
class RecordingLogger : Logger {
    val debugs = mutableListOf<LogEntry>()
    val warnings = mutableListOf<LogEntry>()
    val errors = mutableListOf<LogEntry>()

    override fun debug(tag: String, message: String) {
        debugs += LogEntry(tag, message)
    }

    override fun warn(tag: String, message: String, cause: Throwable?) {
        warnings += LogEntry(tag, message, cause)
    }

    override fun error(tag: String, message: String, cause: Throwable?) {
        errors += LogEntry(tag, message, cause)
    }
}

/**
 * Drops everything, for a test that does not care what was logged.
 *
 * Lives here rather than in `core:common`'s main source, where it was a production class that only
 * tests ever constructed — the same mistake already corrected for the in-memory cache.
 */
class NoOpLogger : Logger {
    override fun debug(tag: String, message: String) = Unit
    override fun warn(tag: String, message: String, cause: Throwable?) = Unit
    override fun error(tag: String, message: String, cause: Throwable?) = Unit
}
