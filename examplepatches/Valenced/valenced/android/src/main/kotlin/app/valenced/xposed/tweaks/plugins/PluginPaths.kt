package app.valenced.xposed.tweaks.plugins

import app.valenced.plugins.PluginPaths
import app.valenced.plugins.PluginStatesStore
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

/** Core paths on the real filesystem. Tests build their own on a `FakeFileSystem`. */
internal fun pluginPaths(dataDir: String) = PluginPaths(FileSystem.SYSTEM, dataDir.toPath())

internal fun externalPluginsRoot(dataDir: String): File = pluginPaths(dataDir).dist.toFile()

/** Per-slot plugin storage root for the active slot. */
internal fun pluginStorageRoot(dataDir: String): File =
    pluginPaths(dataDir).storageFor(PluginStatesStore.activeSlotId).toFile()

/** A plugin's storage in every slot. */
internal fun pluginStorageDirsOfAllSlots(dataDir: String, pluginId: String): List<File> =
    pluginPaths(dataDir).storageDirsOfAllSlots(pluginId).map { it.toFile() }
