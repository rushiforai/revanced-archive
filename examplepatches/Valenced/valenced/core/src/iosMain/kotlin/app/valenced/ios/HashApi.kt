@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.plugins.repos.sha256Hex
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.readBytes

@CName("ValencedCore_Sha256Hex")
fun sha256HexBytes(
    data: CPointer<ByteVar>?,
    length: ULong,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    if (length > Int.MAX_VALUE.toULong()) error("artifact too large to hash")
    sha256Hex(data?.readBytes(length.toInt()) ?: error("missing data to hash"))
}
