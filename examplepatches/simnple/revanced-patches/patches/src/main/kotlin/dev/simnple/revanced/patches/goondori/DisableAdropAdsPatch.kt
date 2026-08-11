package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableAdropAdsPatch = bytecodePatch(
    name = "Disable Adrop Ads",
    description = "Prevents the Adrop advertising SDK from initializing and receiving a user ID.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        listOf(
            adropInitializeFingerprint,
            adropSetUidFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstruction(0, "return-void")
        }
    }
}
