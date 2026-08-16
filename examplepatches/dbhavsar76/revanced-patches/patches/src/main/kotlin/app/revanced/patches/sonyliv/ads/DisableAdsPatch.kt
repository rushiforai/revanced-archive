package app.revanced.patches.sonyliv.ads

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.firstMethodOrNull
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The content metadata model the ad gate is keyed on. Lives in
 * `com.sonyliv.network.model.core`, which R8 leaves alone in every build seen so
 * far — unlike the player view model and utility classes around it, which are
 * renamed even between two consecutive TV releases.
 */
private const val ASSET_METADATA = "Lcom/sonyliv/network/model/core/AssetMetadata;"

/**
 * The `/videourl` response model, from the shared `sony-player-module`. `@Keep` +
 * Gson-annotated, so the class name and its *fields* survive minification.
 *
 * Its Kotlin **getters do not** — `getDaiAssetKey()` is really `h()` in both TV
 * builds, and jadx only shows the pretty name because it reconstructs it from
 * `@Metadata`. Matching on the field is what makes this stable.
 */
private const val CONTENT_DETAILS =
    "Lcom/sonyplayer/network/payload/videourl/response/ContentDetails;"

/** Backing field of the DAI accessor. Kept by Gson (`@SerializedName("dai_asset_key")`). */
private const val DAI_ASSET_KEY_FIELD = "daiAssetKey"

/**
 * Remote-config key read on the first line of the ad gate. The signature alone
 * (static, boolean, one AssetMetadata parameter) matches roughly a dozen methods
 * per build; this string is what narrows it to exactly one.
 */
private const val GDPR_COUNTRY_KEY = "gdpr_country"

/** True if this method body contains `const-string`/`const-string/jumbo` of [value]. */
private fun Method.hasStringConstant(value: String) =
    implementation?.instructions?.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        reference is StringReference && reference.string == value
    } == true

/**
 * True if this method looks like a plain accessor for [fieldName] — it touches that
 * field and is short enough to be nothing but `iget-object` + `return-object`.
 *
 * The size guard is what keeps this off `toString`/`equals`/`copy`, which also read
 * the field. R8 happens to strip those from `ContentDetails` in both TV builds, but
 * relying on that would make the match fragile the moment one of them is kept.
 */
private fun Method.isAccessorOf(fieldName: String): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false

    if (instructions.size > 4) return false

    return instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        reference is FieldReference && reference.name == fieldName
    }
}

/**
 * Disables video advertising in the SonyLIV Android TV app.
 *
 * SonyLIV runs four independent ad paths, and which one is used is decided by the
 * server in the `/videourl` response rather than by the client:
 *
 *  - **CSAI** — Google IMA v3, ad tag URL assembled on-device from remote config
 *    keyed by the user's *ad cluster*.
 *  - **Google DAI** — `com.sonyplayer.ads.DAIAdsManager`, entered only when the
 *    response carries a non-empty `daiAssetKey`.
 *  - **AWS MediaTailor SSAI** — used for live; ads are stitched into the manifest
 *    upstream and the `videoURL` the server hands over *is* the stitched stream.
 *  - **Display/banner** — negligible on TV (large on mobile).
 *
 * The first two are client-gated and are what this patch removes. The third is
 * not defeatable on-device; see the limitation note below.
 *
 * Two hooks, both required — they cover different paths and neither subsumes the
 * other.
 */
@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes pre-roll, mid-roll and post-roll video ads on Android TV builds. " +
        "Live channels carrying server-stitched (MediaTailor) ads are not affected.",
) {
    // Deliberately version-less. Both hooks are matched by shape and string content
    // rather than by name or offset, which is what lets one patch cover TV 6.25.1
    // (Android 5.1+) and 6.27.3 (Android 7.0+) — builds where R8 renamed the player
    // view model (m3 -> f3) and the utility class holding the gate (kz.k -> c10.m).
    compatibleWith("com.sonyliv")

    apply {
        // --- Hook 1: the ad gate -----------------------------------------------
        //
        // `static boolean isAdEnabled(AssetMetadata)`. Stock, it already returns
        // false in three shipping cases: the user is in a GDPR country, the
        // `isAllAdsDisabled` remote kill switch is set, or the user is a subscriber
        // whose account ad cluster is not in the content's adClusterId list. So
        // false is a state the app builds, ships and exercises — not one invented
        // here.
        //
        // Worth noting for testing: a signed-out user has an empty subscription
        // list, which falls through to `return true`. Ads are on for guests, so an
        // A/B test against an unpatched control is meaningful without an account.
        //
        // Matched by shape plus the `gdpr_country` constant. Neither the class nor
        // the method name is usable — both are minified, and differently in each
        // TV release.
        val isAdEnabled = firstMethodOrNull {
            AccessFlags.STATIC.isSet(accessFlags) &&
                returnType == "Z" &&
                parameterTypes.singleOrNull()?.toString() == ASSET_METADATA &&
                implementation != null &&
                hasStringConstant(GDPR_COUNTRY_KEY)
        } ?: throw PatchException(
            "Could not find the ad gate: no static boolean method taking a single " +
                "$ASSET_METADATA and referencing \"$GDPR_COUNTRY_KEY\". This patch " +
                "currently covers the Android TV builds only — the mobile build " +
                "implements the same decision in SLPlayerUtility and needs its own " +
                "hook. If this is a TV build, re-run recon: SonyLIV may have moved " +
                "the GDPR check out of the gate.",
        )

        // v0 is safe regardless of the method's local count: if it has locals, v0 is
        // one of them; if it has none, v0 aliases the AssetMetadata parameter, and
        // clobbering that cannot matter when the next instruction returns.
        isAdEnabled.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // --- Hook 2: Google DAI -------------------------------------------------
        //
        // Hook 1 does *not* cover DAI. The DAIAdsManager is constructed purely on
        // `playerData.getDaiAssetKey()` being non-empty, with no reference to the ad
        // gate — so server-side insertion would survive hook 1 alone.
        //
        // Emptying the key drops playback onto the branch that plays
        // `contentDetails.getVideoURL()` directly, which is the plain content URL.
        //
        // The accessor returns the *empty string*, not null. The field is declared
        // `@NotNull` in the app's own model, so handing back null violates the
        // contract Kotlin callers are compiled against — an earlier revision did
        // exactly that and produced a burst of NullPointerExceptions off the
        // playback path on a signed-out TV session. Every site that consumes this
        // value gates on `length() > 0` / `TextUtils.isEmpty`, so "" reads as
        // "no DAI asset" while staying non-null.
        val getDaiAssetKey = firstMethodOrNull {
            definingClass == CONTENT_DETAILS &&
                returnType == "Ljava/lang/String;" &&
                parameterTypes.isEmpty() &&
                isAccessorOf(DAI_ASSET_KEY_FIELD)
        } ?: throw PatchException(
            "Could not find the accessor for $CONTENT_DETAILS.$DAI_ASSET_KEY_FIELD. " +
                "The shared sony-player-module response model has changed shape — " +
                "re-run recon and check how the DAI branch is now selected in the " +
                "player.",
        )

        // Stock getter is `iget-object v0` / `return-object v0`, so v0 is free.
        getDaiAssetKey.addInstructions(
            0,
            """
                const-string v0, ""
                return-object v0
            """,
        )
    }
}
