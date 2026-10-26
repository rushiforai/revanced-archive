package app.valenced.core

import platform.Foundation.NSLog

internal actual fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val suffix = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
    NSLog("[%s] %s: %s%s", tag, level.name, message, suffix)
}
