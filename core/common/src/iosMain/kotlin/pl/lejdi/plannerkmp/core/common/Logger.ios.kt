package pl.lejdi.plannerkmp.core.common

// println rather than NSLog: on Kotlin/Native it reaches the same Xcode console without requiring
// the ExperimentalForeignApi opt-in that NSLog's C varargs signature would drag in.
private class PrintlnLogger : Logger {
    override fun debug(tag: String, message: String) = write("D", tag, message, null)

    override fun warn(tag: String, message: String, cause: Throwable?) = write("W", tag, message, cause)

    override fun error(tag: String, message: String, cause: Throwable?) = write("E", tag, message, cause)

    private fun write(level: String, tag: String, message: String, cause: Throwable?) {
        println("$level/$tag: $message")
        cause?.let { println(it.stackTraceToString()) }
    }
}

actual fun platformLogger(): Logger = PrintlnLogger()
