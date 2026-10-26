@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.core.ValencedJson
import app.valenced.plugins.repos.PlanTarget
import app.valenced.plugins.repos.ResolveRequest
import app.valenced.plugins.repos.SourcesStore
import app.valenced.plugins.repos.resolveInstall
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class WireVersionCandidate(
    val breaks: List<String>,
)

@Serializable
internal data class WirePlanDependent(
    val id: String,
    val optional: Boolean,
    val range: String,
)

@Serializable
internal data class WirePlannedAction(
    val id: String,
    val version: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val repo: String,
    val channel: String,
    val hold: Boolean? = null,
    val replaces: String? = null,
    val dependents: List<WirePlanDependent> = emptyList(),
    val candidates: Map<String, Map<String, WireVersionCandidate>> = emptyMap(),
)

@Serializable
internal data class WireInstallPlan(
    val actions: List<WirePlannedAction>,
    val warnings: List<JsonObject>,
)

@Serializable
internal data class ResolveOptions(
    val targets: Map<String, TargetOptions> = emptyMap(),
    val repos: List<String>? = null,
    val skipMissingOptionals: Boolean = false,
)

@Serializable
internal data class TargetOptions(
    val repo: String? = null,
    val version: String? = null,
    val channel: String? = null,
)

@CName("ValencedCore_ResolveInstall")
fun resolveInstallRequest(
    pluginId: CPointer<ByteVar>?,
    optionsJson: CPointer<ByteVar>?,
    installedJson: CPointer<ByteVar>?,
    installedDepsJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPaths
    val id = pluginId?.toKString() ?: error("missing plugin id")
    val installed = decodeVersions(installedJson?.toKString())
    val installedDeps = decodeRanges(installedDepsJson?.toKString(), "dependency")
    val options = optionsJson?.toKString()?.let {
        ValencedJson.decodeFromString(ResolveOptions.serializer(), it)
    } ?: ResolveOptions(emptyMap(), null, false)

    val repos = indexedRepos(options.repos?.toSet())
    val targets = options.targets.mapValues { (_, v) ->
        PlanTarget(
            repo = v.repo,
            version = v.version,
            channel = v.channel,
        )
    }
    val plan = resolveInstall(
        ResolveRequest(id, targets, options.skipMissingOptionals),
        repos,
        installed,
        SourcesStore.all(),
        installedDeps,
    )
    ValencedJson.encodeToString(
        WireInstallPlan.serializer(),
        WireInstallPlan(
            actions = plan.actions.map { action ->
                WirePlannedAction(
                    id = action.id,
                    version = action.version.toString(),
                    url = action.url,
                    sha256 = action.sha256,
                    size = action.size,
                    repo = action.repo,
                    channel = action.channel,
                    hold = action.hold,
                    replaces = action.replaces?.toString(),
                    dependents = action.dependents.map { (depId, dep) ->
                        WirePlanDependent(id = depId, optional = dep.optional, range = dep.range.toString())
                    },
                    candidates = action.candidates.mapValues { (_, versions) ->
                        versions.mapValues { (_, cand) ->
                            WireVersionCandidate(breaks = cand.breaks)
                        }
                    },
                )
            },
            warnings = plan.warnings.map { it.toJson() },
        ),
    )
}
