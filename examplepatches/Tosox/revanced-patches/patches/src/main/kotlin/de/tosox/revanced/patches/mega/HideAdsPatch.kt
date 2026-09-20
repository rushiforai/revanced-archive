package de.tosox.revanced.patches.mega

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import de.tosox.revanced.util.returnEarly

internal val BytecodePatchContext.scheduleRefreshAdsFingerprint by gettingFirstMethodDeclaratively {
    definingClass("/AdsViewModel;")
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes()
    instructions(
        allOf(
            Opcode.NEW_INSTANCE(),
            type("/AdsViewModel\$scheduleRefreshAds\$1;")
        )
    )
}

@Suppress("unused")
val hideAdsPatch = bytecodePatch(
    name = "Hide Ads",
    description = "Hides ads across the app",
) {
    // Tested with 16.12(262370820)(dcdf0a7f27)
    compatibleWith("mega.privacy.android.app")

    apply {
        // Without the refresh job no ad request is ever built, so the ads container stays empty
        scheduleRefreshAdsFingerprint.returnEarly()
    }
}
