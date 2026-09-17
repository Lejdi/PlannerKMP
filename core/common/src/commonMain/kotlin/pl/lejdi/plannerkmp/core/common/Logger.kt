package pl.lejdi.plannerkmp.core.common

/**
 * The one place a swallowed throwable can still be seen.
 *
 * Every [DomainError] carries a [DomainError.cause], and every one of them is produced by a `catch`
 * that turns an exception into an [AppResult]. Without a sink for that cause, mapping an exception
 * and discarding it are the same thing: "saving failed" reaches the user and the
 * `SQLiteConstraintException` that explains it reaches nobody.
 *
 * An interface bound in Koin rather than a global: a test can assert on what was logged, and a
 * release build can route the same calls to a crash reporter without touching a call site.
 */
interface Logger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String, cause: Throwable? = null)
    fun error(tag: String, message: String, cause: Throwable? = null)
}

/** The platform's own log sink — logcat on Android, stdout (which Xcode shows) on iOS. */
expect fun platformLogger(): Logger
