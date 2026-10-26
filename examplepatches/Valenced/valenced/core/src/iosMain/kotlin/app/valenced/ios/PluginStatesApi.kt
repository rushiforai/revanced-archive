@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.core.ValencedJson
import app.valenced.plugins.PluginFlags
import app.valenced.plugins.PluginStatesStore
import app.valenced.plugins.pluginFlagsFromJSPayload
import app.valenced.plugins.toJSPayload
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private const val WIRE_FLAG_ENABLED = 1 shl 0
private const val WIRE_FLAG_REQUIRED_BY_USER = 1 shl 1
private const val WIRE_FLAG_PENDING_RELOAD = 1 shl 2
private const val WIRE_FLAG_STARTED_LATE = 1 shl 3

private fun pluginFlagsToWireMask(flags: Set<PluginFlags>): Int {
    var mask = 0
    if (PluginFlags.ENABLED in flags) mask = mask or WIRE_FLAG_ENABLED
    if (PluginFlags.REQUIRED_BY_USER in flags) mask = mask or WIRE_FLAG_REQUIRED_BY_USER
    if (PluginFlags.PENDING_RELOAD in flags) mask = mask or WIRE_FLAG_PENDING_RELOAD
    if (PluginFlags.STARTED_LATE in flags) mask = mask or WIRE_FLAG_STARTED_LATE
    return mask
}

private fun pluginFlagsFromWireMask(mask: Int): Set<PluginFlags> = buildSet {
    if (mask and WIRE_FLAG_ENABLED != 0) add(PluginFlags.ENABLED)
    if (mask and WIRE_FLAG_REQUIRED_BY_USER != 0) add(PluginFlags.REQUIRED_BY_USER)
    if (mask and WIRE_FLAG_PENDING_RELOAD != 0) add(PluginFlags.PENDING_RELOAD)
    if (mask and WIRE_FLAG_STARTED_LATE != 0) add(PluginFlags.STARTED_LATE)
}

@Serializable
internal data class WireSlotInfo(val active: String, val oneShot: String? = null, val slots: List<String>)

private val wirePluginStateSerializer = MapSerializer(String.serializer(), Boolean.serializer())
private val wireSlotStatesSerializer =
    MapSerializer(String.serializer(), MapSerializer(String.serializer(), wirePluginStateSerializer))

@CName("ValencedCore_PluginStatesGetSlots")
fun pluginStatesGetSlots(errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): CPointer<ByteVar>? = guard(errorOut) {
    PluginStatesStore.ensureLoaded(pluginPaths)
    ValencedJson.encodeToString(
        WireSlotInfo.serializer(),
        WireSlotInfo(
            active = PluginStatesStore.activeSlotId,
            oneShot = PluginStatesStore.oneShotSlotId,
            slots = PluginStatesStore.persistentSlotIds(pluginPaths),
        ),
    )
}

@CName("ValencedCore_PluginStatesRead")
fun pluginStatesRead(errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): CPointer<ByteVar>? = guard(errorOut) {
    PluginStatesStore.ensureLoaded(pluginPaths)
    ValencedJson.encodeToString(wireSlotStatesSerializer, PluginStatesStore.toJSPayload())
}

@CName("ValencedCore_PluginStatesSetActiveSlot")
fun pluginStatesSetActiveSlot(
    slot: CPointer<ByteVar>?,
    oneShot: Int,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    PluginStatesStore.setActiveSlot(
        pluginPaths,
        slot?.toKString() ?: error("missing slot id"),
        oneShot != 0,
    )
}

@CName("ValencedCore_PluginStatesUpdate")
fun pluginStatesUpdate(
    slot: CPointer<ByteVar>?,
    pluginId: CPointer<ByteVar>?,
    stateJson: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    val payload = ValencedJson.decodeFromString(
        wirePluginStateSerializer,
        stateJson?.toKString() ?: error("missing plugin state"),
    )
    val flags = pluginFlagsFromJSPayload(payload)
    val id = pluginId?.toKString() ?: error("missing plugin id")
    PluginStatesStore.ensureLoaded(pluginPaths)
    val stateSlot = PluginStatesStore.slot(slot?.toKString() ?: error("missing slot id"))
    stateSlot?.write(id, flags)
    ValencedJson.encodeToString(wirePluginStateSerializer, (stateSlot?.entryFlags(id) ?: flags).toJSPayload())
}

@CName("ValencedCore_PluginStatesActiveFlags")
fun pluginStatesActiveFlags(pluginId: CPointer<ByteVar>?, errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): Int =
    guardWireInt(errorOut) {
        PluginStatesStore.ensureLoaded(pluginPaths)
        val id = pluginId?.toKString() ?: error("missing plugin id")
        PluginStatesStore.active.entryFlags(id)?.let(::pluginFlagsToWireMask) ?: -1
    }

@CName("ValencedCore_PluginStatesBootFlags")
fun pluginStatesBootFlags(pluginId: CPointer<ByteVar>?, errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): Int =
    guardWireInt(errorOut) {
        PluginStatesStore.ensureLoaded(pluginPaths)
        val id = pluginId?.toKString() ?: error("missing plugin id")
        PluginStatesStore.boot.entryFlags(id)?.let(::pluginFlagsToWireMask) ?: -1
    }

@CName("ValencedCore_PluginStatesSetActiveFlags")
fun pluginStatesSetActiveFlags(
    pluginId: CPointer<ByteVar>?,
    mask: Int,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): Int = guardInt(errorOut) {
    PluginStatesStore.ensureLoaded(pluginPaths)
    val id = pluginId?.toKString() ?: error("missing plugin id")
    PluginStatesStore.active.write(id, pluginFlagsFromWireMask(mask))
}

@CName("ValencedCore_PluginStatesRemoveAll")
fun pluginStatesRemoveAll(pluginId: CPointer<ByteVar>?, errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): Int =
    guardInt(errorOut) {
        PluginStatesStore.removePluginFromAllSlots(
            pluginPaths,
            pluginId?.toKString() ?: error("missing plugin id"),
        )
    }

@CName("ValencedCore_PluginStatesDefaultsBoot")
fun pluginStatesDefaultsBoot(): Int = try {
    PluginStatesStore.ensureLoaded(pluginPaths)
    if (PluginStatesStore.isDefaultsBoot) 1 else 0
} catch (t: Throwable) {
    0
}

@CName("ValencedCore_PluginStatesBootSlot")
fun pluginStatesBootSlot(errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?): CPointer<ByteVar>? = guard(errorOut) {
    PluginStatesStore.ensureLoaded(pluginPaths)
    PluginStatesStore.bootSlotId
}
