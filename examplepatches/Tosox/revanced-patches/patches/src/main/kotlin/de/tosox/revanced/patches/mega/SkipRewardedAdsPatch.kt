package de.tosox.revanced.patches.mega

import app.revanced.patcher.*
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

internal val BytecodePatchContext.rewardedAdGateFingerprint by gettingFirstMethodDeclaratively {
    definingClass("/RewardedAdGateHandler;")
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("Lkotlin/jvm/functions/Function0;")
}

@Suppress("unused")
val skipRewardedAdsPatch = bytecodePatch(
    name = "Skip Rewarded Ads",
    description = "Skips the rewarded ad gate and runs the action it gates right away",
) {
    // Tested with 16.12(262370820)(dcdf0a7f27)
    compatibleWith("mega.privacy.android.app")

    apply {
        // Invoke the gated action directly, leaving the gate consultation below unreachable
        rewardedAdGateFingerprint.addInstructions(
            0,
            """
                invoke-interface { p1 }, Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;
                return-void
            """
        )
    }
}
