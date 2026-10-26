@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.core.ValencedJson
import app.valenced.plugins.repos.PluginSource
import app.valenced.plugins.repos.SourcesStore
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@CName("ValencedCore_SourcesStoreAll")
fun sourcesStoreAll(errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): CPointer<ByteVar>? = guard(errorOut) {
    pluginPaths
    ValencedJson.encodeToString(
        MapSerializer(String.serializer(), PluginSource.serializer()),
        SourcesStore.all(),
    )
}

@CName("ValencedCore_SourcesStoreSet")
fun sourcesStoreSet(
    pluginId: CPointer<ByteVar>?,
    sourceJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    pluginPaths
    val id = pluginId?.toKString() ?: error("missing plugin id")
    val source =
        sourceJson?.toKString()?.let { ValencedJson.decodeFromString(PluginSource.serializer(), it) } ?: PluginSource()
    SourcesStore.set(id, source)
}

@CName("ValencedCore_SourcesStoreRemove")
fun sourcesStoreRemove(
    pluginId: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    pluginPaths
    SourcesStore.remove(pluginId?.toKString() ?: error("missing plugin id"))
}

@CName("ValencedCore_SourcesStoreForgetRepos")
fun sourcesStoreForgetRepos(
    repoUrlsJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    pluginPaths
    val urls = ValencedJson.decodeFromString(
        ListSerializer(String.serializer()),
        repoUrlsJson?.toKString() ?: error("missing repo URLs JSON"),
    )
    SourcesStore.forgetRepos(urls)
}

@CName("ValencedCore_SourcesStoreSetHeld")
fun sourcesStoreSetHeld(
    pluginId: CPointer<ByteVar>?,
    held: Int,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPaths
    val id = pluginId?.toKString() ?: error("missing plugin id")
    val updated = SourcesStore.setHeld(id, held != 0)
    ValencedJson.encodeToString(PluginSource.serializer(), updated)
}
