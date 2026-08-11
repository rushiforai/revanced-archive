package dev.simnple.revanced.patches.goondori

import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

private const val LOOPBACK_URL = "https://127.0.0.1/"

@Suppress("unused")
val disableRevenueCatPatch = bytecodePatch(
    name = "Disable RevenueCat",
    description = "Prevents RevenueCat subscription, entitlement, purchase-status, and paywall requests.",
) {
    compatibleWith("com.goondori"("5.6.0"))

    apply {
        revenueCatPurchasesFactoryUrlFingerprint.method.replaceInstruction(
            16,
            "const-string v9, \"$LOOPBACK_URL\"",
        )
        revenueCatAppConfigUrlFingerprint.method.replaceInstruction(
            22,
            "const-string v0, \"$LOOPBACK_URL\"",
        )
        revenueCatRemoteConfigUrlFingerprint.method.replaceInstruction(
            7,
            "const-string v2, \"$LOOPBACK_URL\"",
        )
    }
}
