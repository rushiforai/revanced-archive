package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableFirebaseRemoteConfigPatch = bytecodePatch(
    name = "Disable Firebase Remote Config",
    description = "Prevents Firebase Remote Config from fetching server-controlled settings.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        firebaseRemoteConfigUrlFingerprint.method.replaceInstruction(
            0,
            "const-string v0, \"https://127.0.0.1/\"",
        )
    }
}
