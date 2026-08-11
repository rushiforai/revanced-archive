package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableFirebaseLoggingPatch = bytecodePatch(
    name = "Disable Firebase Logging",
    description = "Stops Firebase and Google DataTransport telemetry from sending through firebaselogging.googleapis.com.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        firebaseLoggingSendFingerprint.method.apply {
            addInstruction(0, "return-object v0")
            addInstruction(0, "move-result-object v0")
            addInstruction(
                0,
                "invoke-static {v0, v1}, Lcom/google/android/datatransport/runtime/backends/BackendResponse;->ok(J)Lcom/google/android/datatransport/runtime/backends/BackendResponse;",
            )
            addInstruction(0, "const-wide/16 v0, 0x0")
        }
    }
}
