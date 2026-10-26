@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.core.ValencedJson
import app.valenced.core.logger
import app.valenced.plugins.repos.RepoIndex
import app.valenced.plugins.repos.RepoStore
import app.valenced.plugins.repos.UserRepo
import app.valenced.plugins.repos.parseRepoIndex
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString
import kotlinx.serialization.builtins.ListSerializer

@CName("ValencedCore_RepoIndexParse")
fun repoIndexParse(
    json: CPointer<ByteVar>?,
    repoUrl: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    val parsed = parseRepoIndex(
        json = json?.toKString() ?: error("missing index JSON"),
        repoUrl = repoUrl?.toKString() ?: "",
        log = logger("RepoIndex"),
    )
    ValencedJson.encodeToString(RepoIndex.serializer(), parsed)
}

@CName("ValencedCore_RepoStoreList")
fun repoStoreList(errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): CPointer<ByteVar>? = guard(errorOut) {
    pluginPaths
    ValencedJson.encodeToString(ListSerializer(UserRepo.serializer()), RepoStore.list())
}

@CName("ValencedCore_RepoStoreSet")
fun repoStoreSet(
    entriesJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    pluginPaths
    val entries = ValencedJson.decodeFromString(
        ListSerializer(UserRepo.serializer()),
        entriesJson?.toKString() ?: error("missing repos JSON"),
    )
    RepoStore.set(entries)
}

@CName("ValencedCore_RepoStoreCacheDir")
fun repoStoreCacheDir(
    url: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPath(url, "repo URL").let { RepoStore.cacheDirFor(it).toString() }
}

@CName("ValencedCore_RepoStoreCachedIndexFile")
fun repoStoreCachedIndexFile(
    url: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPath(url, "repo URL").let { RepoStore.cachedIndexFile(it).toString() }
}

@CName("ValencedCore_RepoStoreCachedETagFile")
fun repoStoreCachedETagFile(
    url: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?
): CPointer<ByteVar>? = guard(errorOut) {
    pluginPath(url, "repo URL").let { RepoStore.cachedETagFile(it).toString() }
}
