package app.valenced.core

enum class LogLevel { Verbose, Debug, Info, Warn, Error }

// Android writes to logcat, iOS to NSLog.
internal expect fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)

class Logger(private val namespace: String?) {
    private fun fmt(msg: String) = if (namespace != null) "$namespace - $msg" else msg

    fun v(msg: String, t: Throwable? = null) = writeLog(LogLevel.Verbose, TAG, fmt(msg), t)
    fun d(msg: String, t: Throwable? = null) = writeLog(LogLevel.Debug, TAG, fmt(msg), t)
    fun i(msg: String, t: Throwable? = null) = writeLog(LogLevel.Info, TAG, fmt(msg), t)
    fun w(msg: String, t: Throwable? = null) = writeLog(LogLevel.Warn, TAG, fmt(msg), t)
    fun e(msg: String, t: Throwable? = null) = writeLog(LogLevel.Error, TAG, fmt(msg), t)

    companion object {
        const val TAG = "Valenced"
    }
}

/** Returns a [Logger] tagged with [namespace]. */
fun logger(namespace: String): Logger = Logger(namespace)
