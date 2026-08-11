package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableAdropMetricsPatch = bytecodePatch(
    name = "Disable Adrop Metrics",
    description = "Stops Adrop user properties and analytics events from crossing the React Native bridge.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        listOf(
            adropSetPropertyFingerprint,
            adropLogEventFingerprint,
            adropSendEventFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
    }
}
