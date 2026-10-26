package app.valenced.xposed.tweaks.plugins.repos

import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.PluginManifest
import app.valenced.plugins.PluginSystemError
import app.valenced.plugins.Version
import app.valenced.plugins.repos.INTERNAL_REPO_URL
import app.valenced.plugins.repos.REPO_CHANNEL_LATEST
import app.valenced.plugins.repos.RepoStore
import app.valenced.plugins.repos.SourcesStore
import app.valenced.plugins.repos.UserRepo
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.plugins.*
import app.valenced.xposed.tweaks.plugins.internal.internalPlugins

internal sealed class RepoState(val value: String) {
    object Ready : RepoState("ready")

    object Refreshing : RepoState("refreshing")

    class Error(val errorMessage: String) : RepoState("error")
}

/**
 * Repository management `valenced.plugins.repos.*` bridge methods.
 *
 * Auto-updates are triggered JS-side. JS can choose to refresh all repos, then check for updates and install them.
 * The native side only provides the repository list and cached indexes.
 */
val pluginRepos by tweak {
    val dataDir = appInfo.dataDir
    RepoStore.ensureLoaded(pluginPaths(dataDir))
    SourcesStore.ensureLoaded(pluginPaths(dataDir))

    fun emitRepoState(url: String, state: RepoState) = emitPluginEvent(
        PluginEvents.REPO_STATE_UPDATE,
        buildMap {
            put("url", url)
            put("state", state.value)
            (state as? RepoState.Error)?.let { put("error", it.errorMessage) }
        },
    )

    /**
     * `valenced.plugins.repos.list() -> Repo[]`
     *
     * The internal repository is always first, followed by user repositories in priority order.
     * Display metadata comes from cached indexes.
     */
    pluginSystemAsyncMethod("valenced.plugins.repos.list") {
        buildList {
            add(internalRepoJSPayload())
            for ((url, enabled) in RepoStore.list()) {
                val index = RepoStore.cachedIndex(url)
                add(
                    mapOf(
                        "url" to url,
                        "enabled" to enabled,
                        "internal" to false,
                        "name" to index?.name,
                        "description" to index?.description,
                        "icon" to index?.icon,
                    )
                )
            }
        }
    }

    /**
     * `valenced.plugins.repos.set(config: Array<{ url, enabled? }>) -> null`
     *
     * Replaces the whole user repository list. Array order is priority order.
     */
    pluginSystemAsyncMethod("valenced.plugins.repos.set") { args ->
        val config = args.firstOrNull() as? List<*>
            ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Expected a config array of { url, enabled }")

        val entries = config.map { entry ->
            val map = entry as? Map<*, *>
                ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Expected { url, enabled } objects")
            val url = map["url"] as? String
                ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Repository entry is missing 'url'")
            val enabled = map["enabled"] as? Boolean ?: true
            UserRepo(url = url, enabled = enabled)
        }

        RepoStore.set(entries)
        null
    }

    /**
     * `valenced.plugins.repos.refresh(url) -> Repo`
     *
     * Refreshes a repository's cached index. Failures won't modify existing cache and fails the bridge call.
     */
    pluginSystemAsyncMethod("valenced.plugins.repos.refresh") { args ->
        val url = args.firstOrNull() as? String
            ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Expected a repository URL")
        if (url == INTERNAL_REPO_URL) throw PluginSystemError(
            PluginErrorCodes.NOT_ALLOWED,
            "The internal repository cannot be refreshed"
        )

        emitRepoState(url, RepoState.Refreshing)
        val index = try {
            refreshRepo(url)
        } catch (e: Throwable) {
            emitRepoState(url, RepoState.Error(e.message ?: "Failed to refresh repository"))
            throw e
        }
        emitRepoState(url, RepoState.Ready)

        val repo = RepoStore.list().first { it.url == url }
        mapOf(
            "url" to url,
            "enabled" to repo.enabled,
            "internal" to false,
            "name" to index.name,
            "description" to index.description,
            "icon" to index.icon,
        )
    }

    /**
     * `valenced.plugins.repos.listPlugins(url) -> RepoPluginListing[]`
     *
     * Lists one repository's plugins from its cached index. Internal plugins are served with no artifacts.
     */
    pluginSystemAsyncMethod("valenced.plugins.repos.listPlugins") { args ->
        val url = args.firstOrNull() as? String
            ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Expected a repository URL")

        if (url == INTERNAL_REPO_URL) return@pluginSystemAsyncMethod internalRepoPluginsJSPayload()

        if (RepoStore.list().none { it.url == url }) throw PluginSystemError(
            PluginErrorCodes.NOT_FOUND,
            "Unknown repository: '$url'"
        )
        val index = RepoStore.cachedIndex(url)
            ?: throw PluginSystemError(PluginErrorCodes.NOT_FOUND, "No cached index for '$url'; refresh it first")

        index.plugins.map { (id, plugin) ->
            mapOf(
                "id" to id,
                "name" to plugin.name,
                "description" to plugin.description,
                "author" to plugin.author,
                "contributors" to plugin.contributors,
                "icon" to plugin.icon,
                "channels" to plugin.channels,
                "order" to plugin.versions.keys.newestFirst(),
                "versions" to plugin.versions.mapValues { (_, v) ->
                    mapOf(
                        "url" to v.url,
                        "sha256" to v.sha256,
                        "size" to v.size,
                        "dependencies" to v.dependencies.mapValues { (_, dep) ->
                            mapOf(
                                "version" to (dep.version ?: "*"),
                                "optional" to dep.optional,
                            )
                        },
                    )
                },
            )
        }
    }
}

/** Sorts versions newest first, unparsable ones last. */
private fun Collection<String>.newestFirst(): List<String> =
    sortedWith(compareByDescending(nullsFirst()) { runCatching { Version.parse(it) }.getOrNull() })

internal fun internalRepoJSPayload(): Map<String, Any?> = mapOf(
    "url" to INTERNAL_REPO_URL,
    "enabled" to true,
    "internal" to true,
    "name" to "Valenced",
    "description" to "Plugins built into Valenced.",
    "icon" to null,
)

/**
 * The internal repository's plugin listing.
 *
 * Nothing is downloadable, entries exist so browsing and resolution can treat internals the same.
 */
internal fun internalRepoPluginsJSPayload(): List<Map<String, Any?>> {
    fun entry(manifest: PluginManifest): Map<String, Any?> {
        val version = manifest.version.toString()
        return mapOf(
            "id" to manifest.id,
            "name" to manifest.name,
            "description" to manifest.description,
            "author" to manifest.author,
            "contributors" to manifest.contributors,
            "icon" to manifest.icon,
            "channels" to mapOf(REPO_CHANNEL_LATEST to version),
            "order" to listOf(version),
            "versions" to mapOf(
                version to mapOf(
                    "url" to null,
                    "sha256" to null,
                    "size" to 0,
                    "dependencies" to manifest.dependencies.mapValues { (_, dep) ->
                        mapOf(
                            "version" to dep.version.toString(),
                            "optional" to dep.optional,
                        )
                    },
                ),
            ),
        )
    }

    return internalPlugins.map { entry(it.manifest) }
}
