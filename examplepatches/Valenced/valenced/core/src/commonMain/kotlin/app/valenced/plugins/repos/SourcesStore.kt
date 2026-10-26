package app.valenced.plugins.repos

import app.valenced.core.Lock
import app.valenced.core.ValencedJson
import app.valenced.core.atomicWriteUtf8
import app.valenced.core.readUtf8OrNull
import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginSystemError
import kotlin.concurrent.Volatile
import kotlinx.serialization.Serializable
import okio.Path

/** sources.json. */
@Serializable
private data class SourcesConfig(
    val format: Int = SOURCES_CONFIG_FORMAT,
    val sources: Map<String, PluginSource> = emptyMap(),
)

private const val SOURCES_CONFIG_FORMAT = 1

/** Persistence for plugin provenance keyed by plugin ID, `files/valenced/plugins/repos/sources.json`. */
object SourcesStore {
    private const val SOURCES_FILE = "sources.json"

    private val lock = Lock()

    @Volatile
    private var paths: PluginPaths? = null

    @Volatile
    private var sources: Map<String, PluginSource>? = null

    fun ensureLoaded(paths: PluginPaths): Map<String, PluginSource> {
        sources?.let { return it }
        return lock.withLock {
            sources ?: run {
                paths.fs.createDirectories(paths.repos)
                this.paths = paths
                val loaded = loadFromFileOrNull(paths, paths.repos / SOURCES_FILE) ?: emptyMap()
                sources = loaded
                loaded
            }
        }
    }

    fun all(): Map<String, PluginSource> = sources ?: error("SourcesStore not loaded")

    operator fun get(pluginId: String): PluginSource? = all()[pluginId]

    fun set(pluginId: String, source: PluginSource) = lock.withLock {
        persist(all() + (pluginId to source))
    }

    /**
     * Records where [pluginId] was just installed from, keeping every other configuration intact.
     *
     * [held] = `null` keeps the current value.
     */
    fun record(pluginId: String, repo: String?, channel: String, hash: String?, held: Boolean? = null) = lock.withLock {
        val existing = all()[pluginId] ?: PluginSource()
        set(
            pluginId,
            existing.copy(repo = repo, channel = channel, hash = hash, held = held ?: existing.held),
        )
    }

    /** Holds [pluginId] at its installed version, or resumes following its channel. Returns the new record. */
    fun setHeld(pluginId: String, held: Boolean): PluginSource = lock.withLock {
        val updated = (all()[pluginId] ?: PluginSource()).copy(held = held)
        set(pluginId, updated)
        updated
    }

    fun remove(pluginId: String) = lock.withLock {
        val current = all()
        if (pluginId in current) persist(current - pluginId)
    }

    /** Removes provenance and clears the repo field for every plugin pinned to one of [repoUrls]. */
    fun forgetRepos(repoUrls: Collection<String>) = lock.withLock {
        if (repoUrls.isNotEmpty()) {
            val current = all()
            val updated = current.mapValues { (_, source) ->
                if (source.repo in repoUrls) source.copy(repo = null) else source
            }
            if (updated != current) persist(updated)
        }
    }

    /** Test: drops the loaded config so [ensureLoaded] reads from disk again. */
    fun resetForTests() = lock.withLock {
        paths = null
        sources = null
    }

    private fun persist(updated: Map<String, PluginSource>) {
        val paths = paths ?: error("SourcesStore not loaded")
        try {
            paths.fs.atomicWriteUtf8(
                paths.repos / SOURCES_FILE,
                ValencedJson.encodeToString(SourcesConfig(sources = updated)),
            )
        } catch (t: Throwable) {
            throw PluginSystemError(PluginErrorCodes.STORAGE_FAILED, "Failed to save plugin sources", t)
        }
        sources = updated
    }

    private fun loadFromFileOrNull(paths: PluginPaths, file: Path): Map<String, PluginSource>? {
        val text = paths.fs.readUtf8OrNull(file)?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val config = ValencedJson.decodeFromString<SourcesConfig>(text)
            require(config.format == SOURCES_CONFIG_FORMAT) {
                "Unsupported sources config format ${config.format} (supported: $SOURCES_CONFIG_FORMAT)"
            }
            config.sources
        } catch (e: Exception) {
            RepoStore.log.e("Failed to read plugin sources: ${e.message}")
            quarantine(paths, file)
            null
        }
    }
}
