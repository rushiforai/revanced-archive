package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableFundingChoicesPatch = bytecodePatch(
    name = "Disable Funding Choices",
    description = "Prevents Google Funding Choices and UMP from requesting advertising consent messages.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        fundingChoicesUrlFingerprint.method.replaceInstruction(
            2,
            "const-string v2, \"https://127.0.0.1/\"",
        )
    }
}
