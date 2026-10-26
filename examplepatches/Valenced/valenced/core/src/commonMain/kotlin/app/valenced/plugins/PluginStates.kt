package app.valenced.plugins

import app.valenced.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import okio.BufferedSource
import okio.IOException
import okio.Path
import kotlin.concurrent.Volatile

/**
 * Lifecycle flags. Flags with bits can be persisted.
 */
enum class PluginFlags(val bit: Int = 0, val jsName: String, val persistAfterDisable: Boolean = false) {
    /** The plugin is enabled. */
    ENABLED(1 shl 0, "enabled"),

    /** The plugin is explicitly enabled by the user. Any optional disablement should not disable this plugin */
    REQUIRED_BY_USER(1 shl 1, "requiredByUser"),

    /** The plugin requires a host reload to apply changes. */
    PENDING_RELOAD(jsName = "pendingReload", persistAfterDisable = true),

    /** The plugin was enabled *after* the initial load (e.g. user-toggled at runtime). */
    STARTED_LATE(jsName = "startedLate"),
}

fun pluginFlagsFromBitmask(mask: Int): Set<PluginFlags> =
    PluginFlags.entries.filterTo(LinkedHashSet()) { (mask and it.bit) != 0 }

fun Iterable<PluginFlags>.toBitmask(): Int {
    var m = 0
    for (f in this) m = m or f.bit
    return m
}

fun Set<PluginFlags>.toJSPayload(): Map<String, Boolean> {
    val map = PluginFlags.entries.associate { it.jsName to (it in this) }
    // Add backwards compatibility here if needed
    return map
}

fun pluginFlagsFromJSPayload(payload: Map<String, Any?>): Set<PluginFlags> {
    val set = PluginFlags.entries.filter { payload[it.jsName] == true }.toSet()
    // Add backwards compatibility here if needed
    return set
}

/** Matches `[a-zA-Z0-9._-]`, with no consecutive dots or leading dot. Colons are internal-only. */
private val SLOT_ID_REGEX = Regex("[a-zA-Z0-9._-]+")

/** Extensions of files that sit beside a slot file but are not slots themselves. */
private val SLOT_SIDECAR_SUFFIXES = setOf("tmp", "bak", "corrupt")

fun isInternalSlotId(id: String) = id.startsWith(':')

fun requireValidSlotId(id: String) {
    if (isInternalSlotId(id)) return
    require(SLOT_ID_REGEX.matches(id) && ".." !in id && !id.startsWith(".")) {
        "Invalid slot ID: $id"
    }
}

/**
 * A named set of plugin flags.
 *
 * Persistent slots are saved as a file under `states/slots/`.
 * Ephemeral slots get discarded once the session ends.
 */
class PluginStateSlot(
    val id: String,
    /** Whether writes reach disk. */
    val persistent: Boolean,
    private val paths: PluginPaths?,
    private val file: Path?,
    initial: Map<String, Set<PluginFlags>> = emptyMap(),
    /** Runs after every entry change, so the caller never has to remember to persist. */
    private val onChanged: () -> Unit = {},
) {
    private val lock = Lock()

    private val flows = HashMap<String, MutableStateFlow<Set<PluginFlags>>>()

    /** What each entry was last written as via [write]. */
    private val recorded = HashMap<String, Set<PluginFlags>>()

    init {
        for ((pluginId, flags) in initial) {
            flows[pluginId] = MutableStateFlow(flags)
            recorded[pluginId] = flags
        }
    }

    /**
     * Live flags of a plugin in this slot.
     *
     * If no flags are saved for this plugin, a new empty flow is created without calling [onChanged] for persistence.
     */
    fun flagsOf(pluginId: String): MutableStateFlow<Set<PluginFlags>> = lock.withLock {
        flows.getOrPut(pluginId) { MutableStateFlow(entryFlags(pluginId) ?: emptySet()) }
    }

    /** Flags of the plugin's entry, or `null` when it has none. */
    fun entryFlags(pluginId: String): Set<PluginFlags>? = lock.withLock {
        if (pluginId in recorded) flows[pluginId]?.value else null
    }

    fun hasPlugin(pluginId: String): Boolean = lock.withLock { pluginId in recorded }

    fun isPluginEnabled(pluginId: String): Boolean = lock.withLock {
        PluginFlags.ENABLED in (entryFlags(pluginId) ?: return@withLock false)
    }

    /**
     * Writes an entry, and pushes to the live flow, so a running plugin sees it.
     * @return Whether anything has changed.
     */
    fun write(pluginId: String, flags: Set<PluginFlags>): Boolean {
        val changed = lock.withLock {
            if (pluginId in recorded && recorded[pluginId] == flags) return@withLock false
            recorded[pluginId] = flags
            flows.getOrPut(pluginId) { MutableStateFlow(flags) }.value = flags
            true
        }
        if (!changed) return false
        onChanged()
        return true
    }

    fun remove(pluginId: String) {
        lock.withLock {
            recorded.remove(pluginId)
            flows.remove(pluginId)
        }
        onChanged()
    }

    fun snapshot(): Map<String, Set<PluginFlags>> = lock.withLock {
        recorded.keys.associateWith { flows[it]?.value ?: emptySet() }
    }

    /** Persists the entries. No-op for an ephemeral slots. */
    fun save() {
        if (!persistent) return
        val target = file ?: return
        val fs = paths?.fs ?: return
        val entries = snapshot()

        try {
            fs.atomicWrite(target) { out ->
                out.writeInt(CURRENT_VERSION)
                out.writeInt(entries.size)
                for ((pluginId, flags) in entries) {
                    out.writeModifiedUtf8(pluginId)
                    out.writeInt(flags.toBitmask())
                }
            }
        } catch (t: Throwable) {
            throw IOException("Failed to save plugin states slot '$id'", t)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1

        /** Reads a slot file, or returns an empty persistent slot when it is absent or corrupt. */
        fun load(
            id: String,
            paths: PluginPaths,
            file: Path,
            log: Logger,
            onChanged: () -> Unit,
        ): PluginStateSlot {
            val entries = readEntriesOrNull(paths, file, log) ?: emptyMap()
            return PluginStateSlot(id, true, paths, file, entries, onChanged)
        }

        private fun readEntriesOrNull(
            paths: PluginPaths,
            file: Path,
            log: Logger,
        ): Map<String, Set<PluginFlags>>? {
            val fs = paths.fs
            val size = fs.metadataOrNull(file)?.takeIf { it.isRegularFile }?.size ?: return null
            if (size <= 0L) return null

            try {
                return fs.read(file) {
                    when (val version = readInt()) {
                        1 -> readV1(this, log)
                        else -> throw UnsupportedPluginStatesVersionException(version)
                    }
                }
            } catch (e: UnsupportedPluginStatesVersionException) {
                log.e(e.message ?: "Unsupported plugin states version")
            } catch (e: Exception) {
                log.e("Plugin states corrupt: ${e.message}")
            }

            // Kept for a bug report, overwritten by the next corrupt read.
            runCatching {
                fs.atomicMove(file, file.parent!! / "${file.name}.corrupt")
            }.onFailure {
                log.e("Failed to rename corrupt states file: ${it.message}")
                runCatching { fs.delete(file) }
            }

            return null
        }

        private fun readV1(source: BufferedSource, log: Logger): Map<String, Set<PluginFlags>> {
            val n = source.readInt()
            val entries = LinkedHashMap<String, Set<PluginFlags>>(n)
            repeat(n) {
                val pluginId = source.readModifiedUtf8()
                entries[pluginId] = pluginFlagsFromBitmask(source.readInt())
            }
            log.i("Loaded plugin states for ${entries.size} plugins")
            return entries
        }
    }
}

private class UnsupportedPluginStatesVersionException(version: Int) :
    Throwable("Unsupported plugin states version: $version")

object PluginStatesStore {
    /** Default slot. */
    const val PRIMARY_SLOT = "primary"

    /** Runs every plugin on its defaults for one boot (recovery). */
    const val DEFAULTS_SLOT = ":defaults"

    internal val log: Logger = logger("pluginStates")

    private val lock = Lock()

    private val slots = HashMap<String, PluginStateSlot>()

    /** The slot the user chose. The UI reads and edits it, and plugin storage keys on it. */
    @Volatile
    var activeSlotId: String = PRIMARY_SLOT
        private set

    /** The one-shot slot this boot consumed, or `null`. */
    @Volatile
    var oneShotSlotId: String? = null
        private set

    /** The slot this boot runs on. Immutable after [ensureLoaded]. */
    @Volatile
    var bootSlotId: String = PRIMARY_SLOT
        private set

    @Volatile
    private var loaded = false

    @Volatile
    private var isBatchSaving = false

    /** Whether this boot runs on defaults instead of the slot the user chose. */
    val isDefaultsBoot: Boolean get() = bootSlotId == DEFAULTS_SLOT

    /** The slot the user chose. */
    val active: PluginStateSlot get() = slots.getValue(activeSlotId)

    /** The slot this boot runs on. Same as [active] on a normal boot. */
    val boot: PluginStateSlot get() = slots.getValue(bootSlotId)

    fun slot(slotId: String): PluginStateSlot? = slots[slotId]

    private fun readSlotId(paths: PluginPaths, file: Path): String? =
        paths.fs.readUtf8OrNull(file)?.trim()?.takeIf { it.isNotEmpty() }

    fun ensureLoaded(paths: PluginPaths) {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            paths.fs.createDirectories(paths.slots)

            activeSlotId = readSlotId(paths, paths.activeSlotFile)
                ?.takeIf { runCatching { requireValidSlotId(it) }.isSuccess }
                ?: PRIMARY_SLOT

            // Consumed before any plugin loads, so a boot crash can't trap the user in it.
            oneShotSlotId = readSlotId(paths, paths.nextSlotFile)?.also {
                runCatching { paths.fs.delete(paths.nextSlotFile) }
                log.i("Booting one-shot into slot '$it'")
            }

            bootSlotId = oneShotSlotId ?: activeSlotId

            slots[activeSlotId] = loadSlot(paths, activeSlotId)
            if (bootSlotId != activeSlotId) slots[bootSlotId] = loadSlot(paths, bootSlotId)

            loaded = true
        }
    }

    private fun makeInternalSlot(slotId: String) = when (slotId) {
        ":defaults" -> PluginStateSlot(slotId, persistent = false, paths = null, file = null)
        else -> throw IllegalArgumentException("No such internal slot: $slotId")
    }

    private fun loadSlot(paths: PluginPaths, slotId: String): PluginStateSlot =
        if (isInternalSlotId(slotId)) makeInternalSlot(slotId)
        else PluginStateSlot.load(slotId, paths, paths.slots / slotId, log, ::writeNow)

    /** Persistent slots on disk, with [PRIMARY_SLOT] always present. */
    fun persistentSlotIds(paths: PluginPaths): List<String> {
        val onDisk = paths.fs.listFileNames(paths.slots).filter {
            // Write scratch files ([atomicWrite]) and quarantined reads live next to the slots they belong to.
            it.substringAfterLast('.', "") !in SLOT_SIDECAR_SUFFIXES &&
                    runCatching { requireValidSlotId(it) }.isSuccess
        }
        return (listOf(PRIMARY_SLOT) + onDisk).distinct()
    }

    /**
     * Picks the slot for the next boot.
     *
     * @param oneShot Consumed and applied on the next boot only.
     */
    fun setActiveSlot(paths: PluginPaths, slotId: String, oneShot: Boolean) {
        requireValidSlotId(slotId)

        paths.fs.atomicWriteUtf8(if (oneShot) paths.nextSlotFile else paths.activeSlotFile, slotId)
    }

    /**
     * Drops the plugin's entry from every slot, loaded or not.
     *
     * Uninstall is cross-slot: an entry left in a slot the user is not in would resurrect the plugin's setup
     * on reinstall.
     */
    fun removePluginFromAllSlots(paths: PluginPaths, pluginId: String) {
        ensureLoaded(paths)
        batchSave { for (slot in slots.values) slot.remove(pluginId) }

        for (slotId in persistentSlotIds(paths)) {
            if (slotId in slots) continue
            val file = paths.slots / slotId
            if (paths.fs.metadataOrNull(file)?.isRegularFile != true) continue

            // Loaded detached: this slot is not this boot's, so its writes must not go through [writeNow].
            val slot = PluginStateSlot.load(slotId, paths, file, log) {}
            if (!slot.hasPlugin(pluginId)) continue
            slot.remove(pluginId)
            runCatching { slot.save() }.onFailure {
                log.e("Failed to drop '$pluginId' from slot '$slotId': ${it.message}")
            }
        }
    }

    /** Test: drops the loaded slots so [ensureLoaded] reads from disk again. */
    fun resetForTests() {
        lock.withLock {
            slots.clear()
            activeSlotId = PRIMARY_SLOT
            oneShotSlotId = null
            bootSlotId = PRIMARY_SLOT
            loaded = false
        }
    }

    fun writeNow() {
        if (isBatchSaving) return
        for (slot in slots.values) {
            if (!slot.persistent) continue
            try {
                slot.save()
            } catch (e: Exception) {
                log.e("Failed to save plugin states slot '${slot.id}': ${e.message}")
            }
        }
    }

    fun <T> batchSave(block: () -> T): T {
        isBatchSaving = true
        try {
            return block()
        } finally {
            isBatchSaving = false
            writeNow()
        }
    }

    fun toJSPayload(): Map<String, Map<String, Map<String, Boolean>>> = slots.mapValues { (_, slot) ->
        slot.snapshot().mapValues { it.value.toJSPayload() }
    }
}
