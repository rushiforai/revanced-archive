package app.valenced.plugins.repos

import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginSystemError
import app.valenced.plugins.Version
import app.valenced.plugins.external.ExternalManifest
import app.valenced.plugins.external.StagedPlugin
import app.valenced.plugins.external.applyStagedPlugin
import app.valenced.plugins.external.discardStagedPlugin
import app.valenced.plugins.external.stagePluginZip
import app.valenced.plugins.pluginLog
import kotlinx.coroutines.sync.Mutex
import okio.Path

private const val REPO_INSTALL_TMP_PREFIX = ".repo-install-"

/**
 * Queue up concurrent `installFromRepo` calls. Each execution re-validates at dequeue time,
 * so overlapping plans' already-satisfied actions no-op and the end state equals sequential calls.
 */
val repoInstallMutex = Mutex()

class RepoInstallAction(
    val id: String,
    val version: Version,
    val url: String,
    val sha256: String,
    val size: Long,
    /** Repository provenance recorded on success. */
    val repo: String,
    /** Channel followed for future update checks. */
    val channel: String,
    /** `true` holds updates, `false` resumes them, `null` keeps the current value. */
    val hold: Boolean?,
)

/** One artifact the host has to fetch. Core never opens a socket. */
class ArtifactRequest(val action: RepoInstallAction) {
    val url: String get() = action.url
    val size: Long get() = action.size
    val sha256: String get() = action.sha256
}

/** A fetched artifact, handed back to core for verification and apply. */
class DownloadedArtifact(val action: RepoInstallAction, val bytes: ByteArray)

/** An artifact that now lives in `dist/`. */
class AppliedInstall(
    val action: RepoInstallAction,
    val dir: Path,
    /** The applied manifest, so the host can resolve intra-plan dependencies without re-reading `dist/`. */
    val manifest: ExternalManifest,
    /**
     * Applied on disk only. Loading it now would either replace running code or link a stale
     * dependency, so it waits for a reload.
     */
    val deferred: Boolean,
)

fun parseRepoInstallAction(raw: Any?): RepoInstallAction {
    val map = raw as? Map<*, *>
        ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Expected a plan action object")

    fun string(key: String) = map[key] as? String
        ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Plan action is missing '$key'")
    return RepoInstallAction(
        id = string("id"),
        version = Version.parse(string("version")),
        url = string("url"),
        sha256 = string("sha256"),
        size = (map["size"] as? Number)?.toLong()
            ?: throw PluginSystemError(PluginErrorCodes.INVALID_ARGUMENT, "Plan action is missing 'size'"),
        repo = string("repo"),
        channel = (map["channel"] as? String) ?: REPO_CHANNEL_LATEST,
        hold = map["hold"] as? Boolean,
    )
}

/** The artifacts [actions] needs, in plan order. The host downloads them and calls [installSubmit]. */
fun installBegin(actions: List<RepoInstallAction>): List<ArtifactRequest> = actions.map(::ArtifactRequest)

/** Fails unless the bytes are exactly what the plan promised. */
fun verifyArtifact(action: RepoInstallAction, bytes: ByteArray) {
    if (bytes.size.toLong() != action.size) throw PluginSystemError(
        PluginErrorCodes.INSTALL_VERIFY_FAILED,
        "Artifact for ${action.id}@${action.version} is ${bytes.size} bytes; the plan says ${action.size}",
    )

    val digest = sha256Hex(bytes)
    if (digest != action.sha256) throw PluginSystemError(
        PluginErrorCodes.INSTALL_VERIFY_FAILED,
        "Artifact digest mismatch for ${action.id}@${action.version} (got $digest)",
    )
}

/**
 * Verifies, extracts and applies downloaded artifacts.
 *
 * - Every artifact's size and SHA-256 are verified against the plan before `dist/` is touched.
 * - Each artifact's `manifest.json` is then parsed and validated. A manifest whose ID or version differs
 *   from the plan aborts the whole plan with `dist/` untouched.
 * - The on-disk swap ([applyStagedPlugin]) never disturbs running code, all updates require a reload:
 *   - [isUpdate] IDs (already installed) are applied on disk only, see [AppliedInstall.deferred].
 *   - New IDs whose required deps are all live can load immediately, dependencies before dependents
 *     so class-loader chaining finds them.
 *   - New IDs requiring a pending dep (in-plan or [isPendingReload]) are deferred too,
 *     loading them against the running old dependency would link stale code.
 */
fun installSubmit(
    paths: PluginPaths,
    downloads: List<DownloadedArtifact>,
    isUpdate: (String) -> Boolean,
    isPendingReload: (String) -> Boolean,
): List<AppliedInstall> {
    if (downloads.isEmpty()) return emptyList()

    val staged = mutableListOf<Pair<RepoInstallAction, StagedPlugin>>()
    try {
        for ((i, download) in downloads.withIndex()) {
            val action = download.action
            verifyArtifact(action, download.bytes)

            val plugin = stagePluginZip(paths, "$REPO_INSTALL_TMP_PREFIX$i") { it.write(download.bytes) }
            staged += action to plugin

            // The manifest is authoritative, any mismatch with the plan aborts the whole plan.
            if (plugin.manifest.id != action.id || Version.parse(plugin.manifest.version) != action.version) {
                throw PluginSystemError(
                    PluginErrorCodes.INSTALL_MISMATCH,
                    "Artifact manifest (${plugin.manifest.id}@${plugin.manifest.version}) does not match " +
                            "the plan (${action.id}@${action.version}); aborting the install plan",
                )
            }
        }

        val applied = mutableListOf<AppliedInstall>()
        val pendingIds = mutableSetOf<String>()
        for ((action, plugin) in orderStagedByDependencies(staged)) {
            val dir = applyStagedPlugin(paths, plugin)
            runCatching {
                SourcesStore.record(action.id, action.repo, action.channel, action.sha256, action.hold)
            }.onFailure { pluginLog.e("Failed to record plugin source for ${action.id}", it) }

            val deferred = isUpdate(action.id) || plugin.manifest.dependencies.any { (depId, dep) ->
                !dep.optional && (depId in pendingIds || isPendingReload(depId))
            }
            if (deferred) {
                pendingIds += action.id
                pluginLog.i("Applied ${action.id}@${action.version} from ${action.repo} (pending reload)")
            } else {
                pluginLog.i("Installed ${action.id}@${action.version} from ${action.repo}")
            }
            applied += AppliedInstall(action, dir, plugin.manifest, deferred)
        }
        return applied
    } finally {
        for ((_, plugin) in staged) discardStagedPlugin(paths, plugin)
    }
}

/**
 * Orders [ids] so that a dependency in the set precedes its dependents. A dependency cycle keeps the
 * relative input order of the plugins left over.
 *
 * Shared by [installSubmit] and the host bridge, which applies staged plugins in this order.
 */
fun orderPluginIdsByDependencies(
    ids: List<String>,
    dependenciesOf: (String) -> Collection<String>,
): List<String> {
    val remaining = LinkedHashMap<String, Collection<String>>()
    for (id in ids) remaining[id] = dependenciesOf(id)

    val ordered = mutableListOf<String>()
    while (remaining.isNotEmpty()) {
        val ready = remaining.filter { (id, deps) -> deps.none { it != id && it in remaining } }
        if (ready.isEmpty()) {
            ordered += remaining.keys // Cycle, use input order.
            break
        }
        for (id in ready.keys) {
            ordered += id
            remaining.remove(id)
        }
    }
    return ordered
}

/**
 * Orders staged plugins so dependencies apply before dependents.
 * A dep cycle will apply by input order and the post-apply load can report whatever fails.
 */
private fun orderStagedByDependencies(
    staged: List<Pair<RepoInstallAction, StagedPlugin>>,
): List<Pair<RepoInstallAction, StagedPlugin>> {
    val byId = staged.associateBy { it.first.id }
    return orderPluginIdsByDependencies(staged.map { it.first.id }) { id ->
        byId.getValue(id).second.manifest.dependencies.keys
    }.map { byId.getValue(it) }
}
