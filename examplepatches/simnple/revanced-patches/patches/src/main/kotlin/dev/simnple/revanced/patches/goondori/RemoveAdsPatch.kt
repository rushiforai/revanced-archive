package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes ads and their empty layout spaces, preserves the dashboard's ad-free spacing, and blocks Adrop identifiers and metrics without changing Goondori Premium entitlement.",
) {
    compatibleWith("com.goondori"("5.6.0"))
    dependsOn(removeAdLayoutsPatch)

    apply {
        listOf(
            adropInitializeFingerprint,
            adropSetUidFingerprint,
            adropSetPropertyFingerprint,
            adropLogEventFingerprint,
            adropSendEventFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
    }
}
