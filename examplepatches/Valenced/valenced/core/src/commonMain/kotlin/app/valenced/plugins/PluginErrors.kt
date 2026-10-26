package app.valenced.plugins

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Error codes crossing the bridge. Append-only once shipped: renaming or removing one breaks translations keyed on it.
 */
object PluginErrorCodes {
    const val MANIFEST_INVALID = "MANIFEST_INVALID"
    const val DEPENDENCY_MISSING = "DEPENDENCY_MISSING"
    const val DEPENDENCY_UNSATISFIED = "DEPENDENCY_UNSATISFIED"
    const val DEPENDENCY_FAILED = "DEPENDENCY_FAILED"
    const val DEPENDENCY_CYCLE = "DEPENDENCY_CYCLE"
    const val LOAD_FAILED = "LOAD_FAILED"

    /* The plugin threw. */
    const val PLUGIN_ERROR = "PLUGIN_ERROR"

    const val INSTALL_INVALID_ZIP = "INSTALL_INVALID_ZIP"
    const val INSTALL_VERIFY_FAILED = "INSTALL_VERIFY_FAILED"
    const val INSTALL_MISMATCH = "INSTALL_MISMATCH"
    const val INSTALL_FAILED = "INSTALL_FAILED"

    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"

    /** Disallowed actions on essential plugins, internal plugins, reserved repository URLs. */
    const val NOT_ALLOWED = "NOT_ALLOWED"

    const val NOT_FOUND = "NOT_FOUND"
    const val RESOLVE_FAILED = "RESOLVE_FAILED"

    /** Can't enable, required dependencies are missing or out of range. */
    const val DEPENDENCIES_UNSATISFIED = "DEPENDENCIES_UNSATISFIED"

    /** The plugin could not undo changes, so we can't start it again unless a reload is performed. */
    const val RELOAD_REQUIRED = "RELOAD_REQUIRED"

    const val STORAGE_FAILED = "STORAGE_FAILED"

    /** Anything that isn't a [PluginSystemError]. */
    const val UNKNOWN = "UNKNOWN"
}

/** Loader failure carrying a [PluginErrorCodes] code, converted to a [PluginError] at the bridge boundary. */
open class PluginSystemError(
    val code: String,
    message: String,
    cause: Throwable? = null,
    val details: JsonObject? = null,
) : Exception(message, cause)

/**
 * Wire shape of an error: `{ code, message, stack, details? }`.
 *
 * `stack` is always present (as `null` when unknown); `details` only appears when there is something to say.
 */
@Serializable
data class PluginError @OptIn(ExperimentalSerializationApi::class) constructor(
    val code: String,
    val message: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stack: String? = null,
    val details: JsonObject? = null,
)

/** One unsatisfied required dependency, reported by `setEnabled` under `details.problems`. */
@Serializable
data class DependencyProblemPayload(
    val id: String,
    /** The declared range. */
    val required: String,
    /** Installed version, or `null` when the dependency is missing. */
    val installed: String?,
    val enabled: Boolean,
)

fun Throwable.toPluginError(fallbackCode: String): PluginError = PluginError(
    code = (this as? PluginSystemError)?.code ?: fallbackCode,
    message = message ?: toString(),
    stack = stackTraceToString(),
    details = (this as? PluginSystemError)?.details,
)
