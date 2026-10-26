package app.valenced.xposed.tweaks.plugins

import app.valenced.core.toNativeMap
import app.valenced.plugins.PluginError
import app.valenced.plugins.PluginErrorCodes
import app.valenced.plugins.toJSPayload
import app.valenced.plugins.toPluginError
import app.valenced.xposed.api.HostScope
import app.valenced.xposed.api.registerNativeAsyncMethod
import app.valenced.xposed.api.registerNativeMethod
import app.valenced.xposed.tweaks.bridge.ValencedBridgeRegistry.toNativeObject

/** The error DTO in the `Map` shape the React bridge takes. */
internal fun PluginError.toJSPayload(): Map<String, Any?> = toNativeMap()

// We ensure JS always receives a "successful bridge call" with `{ result | error }`, so enough details can be given.

internal fun Any?.toJSPayload(): Map<String, Any?> = mapOf("result" to this?.toNativeObject())

internal fun Throwable.toJSPayload(): Map<String, Any?> {
    // Arg delegates and require() throw IllegalArgumentException, this is likely JS' fault.
    val fallback = if (this is IllegalArgumentException) PluginErrorCodes.INVALID_ARGUMENT else PluginErrorCodes.UNKNOWN
    return mapOf("error" to toPluginError(fallback).toJSPayload())
}

internal fun HostScope.pluginSystemMethod(name: String, handler: (args: List<Any?>) -> Any?) =
    registerNativeMethod(name) { args ->
        try {
            handler(args).toJSPayload()
        } catch (e: Throwable) {
            e.toJSPayload()
        }
    }

internal fun HostScope.pluginSystemAsyncMethod(name: String, handler: suspend (args: List<Any?>) -> Any?) =
    registerNativeAsyncMethod(name) { args ->
        try {
            handler(args).toJSPayload()
        } catch (e: Throwable) {
            e.toJSPayload()
        }
    }
