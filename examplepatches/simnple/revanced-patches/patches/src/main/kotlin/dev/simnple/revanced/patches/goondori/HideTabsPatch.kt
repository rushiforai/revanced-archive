package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.patch.resourcePatch

private const val HERMES_BUNDLE_PATH = "assets/index.android.bundle"
private const val EXPECTED_BUNDLE_SIZE = 17_432_292
private const val GET_BY_ID_OPCODE = 69
private const val MORE_STRING_ID = 45_397
private const val MORE_PROPERTY_CACHE_INDEX = 10

private val expectedBundleHeader = byteArrayOf(
    0xc6.toByte(), 0x1f, 0xbc.toByte(), 0x03, 0xc1.toByte(), 0x03, 0x19, 0x1f,
    0x62, 0x00, 0x00, 0x00,
    0xd0.toByte(), 0x3a, 0xd1.toByte(), 0x28, 0xd5.toByte(), 0xcd.toByte(), 0x88.toByte(), 0xff.toByte(),
    0x27, 0xfc.toByte(), 0x83.toByte(), 0xf0.toByte(), 0xca.toByte(), 0x18, 0x68, 0xa0.toByte(),
    0x4a, 0xaf.toByte(), 0xdb.toByte(), 0xb7.toByte(),
)

private enum class GoondoriTab(
    val displayName: String,
    val stringId: Int,
    val propertyCacheIndex: Int,
    val operandOffsets: IntArray,
) {
    VACATION(
        displayName = "Vacation",
        stringId = 46_325,
        propertyCacheIndex = 3,
        operandOffsets = intArrayOf(0x64bc06, 0x64bc5c, 0x64bcb2, 0x64bd08, 0x64bd5e, 0x64bdb4, 0x64be0a),
    ),
    COMMUNITY(
        displayName = "Community",
        stringId = 38_751,
        propertyCacheIndex = 4,
        operandOffsets = intArrayOf(0x64bc10, 0x64bc66, 0x64bcbc, 0x64bd68, 0x64bdbe, 0x64be14),
    ),
    CONTENT(
        displayName = "Content",
        stringId = 36_440,
        propertyCacheIndex = 5,
        operandOffsets = intArrayOf(0x64bc1a, 0x64bc70, 0x64bcc6, 0x64bd72, 0x64bdc8, 0x64be1e),
    ),
    STORE(
        displayName = "Store",
        stringId = 37_727,
        propertyCacheIndex = 8,
        operandOffsets = intArrayOf(0x64bc24, 0x64bc7a, 0x64bcd0, 0x64bd7c, 0x64bdd2, 0x64be28),
    ),
}

private fun ResourcePatchContext.hideTab(tab: GoondoriTab) {
    val bundleFile = get(HERMES_BUNDLE_PATH)
    val bundle = bundleFile.readBytes()

    check(bundle.size == EXPECTED_BUNDLE_SIZE) {
        "Unsupported Goondori Hermes bundle size: ${bundle.size}"
    }
    check(bundle.copyOfRange(0, expectedBundleHeader.size).contentEquals(expectedBundleHeader)) {
        "Unsupported Goondori Hermes bundle header or source hash"
    }

    tab.operandOffsets.forEach { operandOffset ->
        check((bundle[operandOffset - 4].toInt() and 0xff) == GET_BY_ID_OPCODE) {
            "${tab.displayName} tab fingerprint has an unexpected opcode at 0x${(operandOffset - 4).toString(16)}"
        }
        check((bundle[operandOffset - 2].toInt() and 0xff) == 5) {
            "${tab.displayName} tab fingerprint has an unexpected object register at 0x${(operandOffset - 2).toString(16)}"
        }
        check((bundle[operandOffset - 1].toInt() and 0xff) == tab.propertyCacheIndex) {
            "${tab.displayName} tab fingerprint has an unexpected property cache index at 0x${(operandOffset - 1).toString(16)}"
        }

        val currentStringId =
            (bundle[operandOffset].toInt() and 0xff) or
                ((bundle[operandOffset + 1].toInt() and 0xff) shl 8)
        check(currentStringId == tab.stringId) {
            "${tab.displayName} tab fingerprint has an unexpected string ID at 0x${operandOffset.toString(16)}"
        }
    }

    tab.operandOffsets.forEach { operandOffset ->
        bundle[operandOffset - 1] = MORE_PROPERTY_CACHE_INDEX.toByte()
        bundle[operandOffset] = (MORE_STRING_ID and 0xff).toByte()
        bundle[operandOffset + 1] = (MORE_STRING_ID ushr 8).toByte()
    }

    bundleFile.writeBytes(bundle)
}

@Suppress("unused")
val hideVacationTabPatch = resourcePatch(
    name = "Hide Vacation Tab",
    description = "Hides the Vacation tab from Goondori's bottom navigation.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        hideTab(GoondoriTab.VACATION)
    }
}

@Suppress("unused")
val hideCommunityTabPatch = resourcePatch(
    name = "Hide Community Tab",
    description = "Hides the Community tab from Goondori's bottom navigation.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        hideTab(GoondoriTab.COMMUNITY)
    }
}

@Suppress("unused")
val hideContentTabPatch = resourcePatch(
    name = "Hide Content Tab",
    description = "Hides the Content tab from Goondori's bottom navigation.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        hideTab(GoondoriTab.CONTENT)
    }
}

@Suppress("unused")
val hideStoreTabPatch = resourcePatch(
    name = "Hide Store Tab",
    description = "Hides the Store tab from Goondori's bottom navigation.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        hideTab(GoondoriTab.STORE)
    }
}
