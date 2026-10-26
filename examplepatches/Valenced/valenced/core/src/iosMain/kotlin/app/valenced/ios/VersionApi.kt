@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.valenced.ios

import app.valenced.plugins.Version
import app.valenced.plugins.VersionRange
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.toKString

@CName("ValencedCore_VersionParse")
fun versionParse(
    value: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    Version.parse(value?.toKString() ?: error("Version string must not be empty")).toString()
}

@CName("ValencedCore_VersionCompare")
fun versionCompare(a: CPointer<ByteVar>?, b: CPointer<ByteVar>?): Int = try {
    Version.parse(a?.toKString() ?: error("missing left version"))
        .compareTo(Version.parse(b?.toKString() ?: error("missing right version")))
} catch (t: Throwable) {
    Int.MIN_VALUE
}

@CName("ValencedCore_VersionRangeParse")
fun versionRangeParse(
    value: CPointer<ByteVar>?,
    errorOut: CPointer<CPointerVarOf<CPointer<ByteVar>>>?,
): CPointer<ByteVar>? = guard(errorOut) {
    VersionRange.parse(value?.toKString() ?: error("Version range must not be empty")).toString()
}

@CName("ValencedCore_VersionRangeSatisfies")
fun versionRangeSatisfies(range: CPointer<ByteVar>?, version: CPointer<ByteVar>?): Int = try {
    if (VersionRange.parse(range?.toKString() ?: error("missing range"))
            .satisfies(Version.parse(version?.toKString() ?: error("missing version")))
    ) 1 else 0
} catch (t: Throwable) {
    Int.MIN_VALUE
}
