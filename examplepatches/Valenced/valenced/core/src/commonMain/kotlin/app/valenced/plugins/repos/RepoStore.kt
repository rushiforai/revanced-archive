package app.valenced.plugins.repos

import app.valenced.core.*
import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginSystemError
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Path
import kotlin.concurrent.Volatile

/**
 * Reserved identity of the built-in internal repository serving internal plugins. It is not persisted, and not removable.
 */
const val INTERNAL_REPO_URL = "valenced://internal"

@Serializable
data class UserRepo(
    /** Absolute HTTP(s) URL of the repository root, and the repository's identity. */
    val url: String,
    val enabled: Boolean = true,
)

/** repos.json. */
@Serializable
private data class ReposConfig(
    val format: Int = REPOS_CONFIG_FORMAT,
    val repos: List<UserRepo> = emptyList(),
)

private const val REPOS_CONFIG_FORMAT = 1

/**
 * Persistence for the user's repository list and per-repository index caches.
 *
 * Directory layout:
 * ```
 * files/valenced/plugins/repos/
 *   repos.json              # ordered [{ url, enabled }], order is priority
 *   cache/<sha256(url)>/    # per-repo cache
 *     index.json            # last successfully fetched and validated index
 *     etag                  # E-Tag of the cached index, if the server sent one
 * ```
 */
object RepoStore {
    private const val REPOS_FILE = "repos.json"
    private const val CACHE_DIR = "cache"
    private const val INDEX_FILE = "index.json"
    private const val ETAG_FILE = "etag"

    val log: Logger = logger("pluginRepos")

    private val lock = Lock()

    @Volatile
    private var paths: PluginPaths? = null

    @Volatile
    private var repos: List<UserRepo>? = null

    private val loaded: PluginPaths get() = paths ?: error("RepoStore not loaded")

    fun ensureLoaded(paths: PluginPaths): List<UserRepo> {
        repos?.let { return it }
        return lock.withLock {
            repos ?: run {
                paths.fs.createDirectories(paths.repos)
                this.paths = paths
                val loaded = loadFromFileOrNull(paths, paths.repos / REPOS_FILE) ?: emptyList()
                repos = loaded
                loaded
            }
        }
    }

    fun list(): List<UserRepo> = repos ?: error("RepoStore not loaded")

    /**
     * Replaces the whole repository config. Array order is priority order.
     * Caches of removed repositories are dropped.
     */
    fun set(entries: List<UserRepo>) = lock.withLock {
        val paths = loaded

        val seen = mutableSetOf<String>()
        for ((url) in entries) {
            if (!url.startsWith("https://") && !url.startsWith("http://")) throw PluginSystemError(
                PluginErrorCodes.INVALID_ARGUMENT,
                "Repository URL must be absolute: '$url'",
            )

            if (url == INTERNAL_REPO_URL) throw PluginSystemError(
                PluginErrorCodes.NOT_ALLOWED,
                "Repository URL is reserved: '$url'",
            )

            if (!seen.add(url)) throw PluginSystemError(
                PluginErrorCodes.INVALID_ARGUMENT,
                "Duplicate repository URL: '$url'",
            )
        }

        val previous = repos.orEmpty()
        save(paths, paths.repos / REPOS_FILE, entries)
        repos = entries

        // Drop caches of removed repos and null the provenance of plugins pinned to them.
        // Installations are treated as sideloaded from now on.
        val removedUrls = previous.map { it.url } - entries.map { it.url }.toSet()
        for (removed in removedUrls) runCatching { paths.fs.deleteRecursively(cacheDirFor(removed)) }
        SourcesStore.forgetRepos(removedUrls)
    }

    /** Repository cache directory, keyed by the SHA-256 of the repository URL. */
    fun cacheDirFor(url: String): Path = loaded.repos / CACHE_DIR / sha256Hex(url)

    fun cachedIndexFile(url: String): Path = cacheDirFor(url) / INDEX_FILE

    fun cachedETagFile(url: String): Path = cacheDirFor(url) / ETAG_FILE

    /** The last successfully fetched index of [url], parsed + sanitized, or `null` if none/invalid. */
    fun cachedIndex(url: String): RepoIndex? {
        val text = loaded.fs.readUtf8OrNull(cachedIndexFile(url)) ?: return null
        return try {
            parseRepoIndex(text, url, log)
        } catch (e: Exception) {
            log.e("Failed to read cached index for '$url': ${e.message}")
            null
        }
    }

    /** Replaces the cached index of [url] with [text], which must already have parsed. */
    fun writeCachedIndex(url: String, text: String, etag: String?) {
        val fs = loaded.fs
        fs.createDirectories(cacheDirFor(url))
        fs.atomicWriteUtf8(cachedIndexFile(url), text)
        if (etag != null) fs.atomicWriteUtf8(cachedETagFile(url), etag)
        else runCatching { fs.delete(cachedETagFile(url)) }
    }

    /** The E-Tag of the cached index, or `null` when either file is missing. */
    fun cachedETag(url: String): String? {
        val fs = loaded.fs
        if (fs.metadataOrNull(cachedIndexFile(url))?.isRegularFile != true) return null
        return fs.readUtf8OrNull(cachedETagFile(url))
    }

    /** Test: drops the loaded config so [ensureLoaded] reads from disk again. */
    fun resetForTests() = lock.withLock {
        paths = null
        repos = null
    }

    private fun loadFromFileOrNull(paths: PluginPaths, file: Path): List<UserRepo>? {
        val text = paths.fs.readUtf8OrNull(file)?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val config = ValencedJson.decodeFromString<ReposConfig>(text)
            require(config.format == REPOS_CONFIG_FORMAT) {
                "Unsupported repos config format ${config.format} (supported: $REPOS_CONFIG_FORMAT)"
            }
            config.repos
        } catch (e: Exception) {
            log.e("Failed to read repos config: ${e.message}")
            quarantine(paths, file)
            null
        }
    }

    private fun save(paths: PluginPaths, file: Path, entries: List<UserRepo>) {
        try {
            paths.fs.atomicWriteUtf8(file, ValencedJson.encodeToString(ReposConfig(repos = entries)))
        } catch (t: Throwable) {
            throw PluginSystemError(PluginErrorCodes.STORAGE_FAILED, "Failed to save repos config", t)
        }
    }
}

/** Moves an unreadable document aside so the next write starts clean and the old one is still recoverable. */
internal fun quarantine(paths: PluginPaths, file: Path) {
    runCatching {
        paths.fs.atomicMove(file, file.parent!! / "${file.name}.corrupt")
    }.onFailure {
        runCatching { paths.fs.delete(file) }
    }
}

/** Hex SHA-256, used for cache directory names and artifact verification. */
fun sha256Hex(value: String): String = value.encodeUtf8().sha256().hex()

fun sha256Hex(bytes: ByteArray): String = bytes.toByteString().sha256().hex()
