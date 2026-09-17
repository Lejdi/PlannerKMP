package pl.lejdi.plannerkmp.core.common

import android.util.Log

private class LogcatLogger : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String, cause: Throwable?) {
        Log.w(tag, message, cause)
    }

    override fun error(tag: String, message: String, cause: Throwable?) {
        Log.e(tag, message, cause)
    }
}

actual fun platformLogger(): Logger = LogcatLogger()
