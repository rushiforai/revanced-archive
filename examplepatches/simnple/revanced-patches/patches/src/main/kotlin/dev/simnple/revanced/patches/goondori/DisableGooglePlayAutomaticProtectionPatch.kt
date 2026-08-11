package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableGooglePlayAutomaticProtectionPatch = bytecodePatch(
    name = "Disable Google Play Automatic Protection",
    description = "Makes Google Play PairIP accept Goondori installations from outside Google Play.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        pairipLocalInstallerCheckFingerprint.method.apply {
            addInstruction(0, "return v0")
            addInstruction(0, "const/4 v0, 0x1")
        }
    }
}
