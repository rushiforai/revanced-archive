package app.valenced.plugins.repos

import kotlinx.serialization.Serializable

/**
 * Where an installed plugin came from, and how it should be updated.
 *
 * - `repo == null`: Sideloaded. No repo updates.
 * - `repo != null`: Installed from that repository. Updates are fetched from it.
 */
@Serializable
data class PluginSource(
    val repo: String? = null,
    /** Hash of the artifact installed. */
    val hash: String? = null,
    /** The channel followed for updates (`latest` unless the user opted into another). */
    val channel: String = REPO_CHANNEL_LATEST,
    /** Keep the installed version, skip update checks, and refuse to update it to satisfy something else. */
    val held: Boolean = false,
)
