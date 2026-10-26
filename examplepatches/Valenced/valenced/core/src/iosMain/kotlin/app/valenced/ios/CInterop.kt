@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import platform.posix.free
import platform.posix.strdup

internal fun String.toC(): CPointer<ByteVar>? = strdup(this)

internal fun guard(
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
    block: () -> String,
): CPointer<ByteVar>? = try {
    block().toC()
} catch (t: Throwable) {
    errorOut?.pointed?.value = (t.message ?: t.toString()).toC()
    null
}

internal fun guardInt(
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
    block: () -> Unit,
): Int = try {
    block()
    0
} catch (t: Throwable) {
    errorOut?.pointed?.value = (t.message ?: t.toString()).toC()
    -1
}

internal fun guardWireInt(
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
    block: () -> Int,
): Int = try {
    block()
} catch (t: Throwable) {
    errorOut?.pointed?.value = (t.message ?: t.toString()).toC()
    -1
}

@CName("ValencedCore_FreeString")
fun freeString(value: CPointer<ByteVar>?) {
    if (value != null) free(value)
}
