package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableFirebaseAnalyticsPatch = bytecodePatch(
    name = "Disable Firebase Analytics",
    description = "Stops Goondori from logging Firebase events and attaching account or military metadata to Firebase Analytics.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        listOf(
            firebaseLogEventFingerprint,
            firebaseSetUserIdFingerprint,
            firebaseSetUserPropertyFingerprint,
            firebaseSetDefaultEventParametersFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
    }
}
