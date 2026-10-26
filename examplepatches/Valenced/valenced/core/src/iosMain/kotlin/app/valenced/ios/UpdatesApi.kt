@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.core.ValencedJson
import app.valenced.plugins.repos.SourcesStore
import app.valenced.plugins.repos.findUpdates
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class WireIncludedAction(
    val id: String,
    val version: String,
    val replaces: String? = null,
    val size: Long,
)

@Serializable
internal data class WireUpdate(
    val id: String,
    val installed: String,
    val available: String,
    val channel: String,
    val repo: String,
    val size: Long,
    val includes: List<WireIncludedAction>,
    val warnings: List<JsonObject>,
    val blocker: JsonObject? = null,
)

@CName("ValencedCore_FindUpdates")
fun findPluginUpdates(
    installedJson: CPointer<ByteVar>?,
    pendingJson: CPointer<ByteVar>?,
    installedDepsJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPaths
    val installed = decodeVersions(installedJson?.toKString(), tolerant = true)
    val pending = decodeVersions(pendingJson?.toKString(), tolerant = true)
    val installedDeps = decodeRanges(installedDepsJson?.toKString(), "dependency")
    val updates = findUpdates(indexedRepos(), installed, pending, SourcesStore.all(), installedDeps)
    ValencedJson.encodeToString(
        ListSerializer(WireUpdate.serializer()),
        updates.map { update ->
            WireUpdate(
                id = update.id,
                installed = update.installed.toString(),
                available = update.available.toString(),
                channel = update.channel,
                repo = update.repo,
                size = update.size,
                includes = update.includes.map { inc ->
                    WireIncludedAction(
                        id = inc.id,
                        version = inc.version.toString(),
                        replaces = inc.replaces?.toString(),
                        size = inc.size,
                    )
                },
                warnings = update.warnings.map { it.toJson() },
                blocker = update.blocker?.toJson(),
            )
        },
    )
}
