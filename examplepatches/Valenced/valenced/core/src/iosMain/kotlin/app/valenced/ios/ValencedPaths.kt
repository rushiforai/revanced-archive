@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.plugins.PluginPaths
import app.valenced.plugins.repos.RepoStore
import app.valenced.plugins.repos.SourcesStore
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal val pluginPaths: PluginPaths by lazy {
    val valencedDir = valencedDirectory()
    val paths = PluginPaths(FileSystem.SYSTEM, valencedDir, rootOverride = valencedDir / "plugins")
    RepoStore.ensureLoaded(paths)
    SourcesStore.ensureLoaded(paths)
    paths
}

private fun valencedDirectory() = run {
    val documents = (NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .lastOrNull() as? NSURL)?.path ?: error("No Documents directory available")
    "$documents/valenced".toPath()
}

internal fun pluginPath(url: CPointer<ByteVar>?, label: String): String {
    pluginPaths
    return url?.toKString() ?: error("missing $label")
}
