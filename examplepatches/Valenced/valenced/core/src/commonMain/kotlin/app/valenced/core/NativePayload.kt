package app.valenced.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull

/**
 * Converts a payload DTO to the `Map`/`List`/primitive tree the React bridge takes.
 *
 * Core builds payloads as `@Serializable` DTOs so iOS can hand JS a string, while Android keeps the map shape
 * `Arguments.makeNativeObject` already understands.
 */
inline fun <reified T> T.toNativePayload(): Any? = ValencedJson.encodeToJsonElement(this).toNative()

/** Same as [toNativePayload], for a payload that is always an object. */
inline fun <reified T> T.toNativeMap(): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return toNativePayload() as Map<String, Any?>
}

fun JsonElement.toNative(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { (_, value) -> value.toNative() }
    is JsonArray -> map { it.toNative() }
    is JsonPrimitive -> when {
        isString -> content
        else -> booleanOrNull
            ?: longOrNull?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
            ?: content.toDoubleOrNull()
    }
}
