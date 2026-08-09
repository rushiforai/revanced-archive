package app.revanced.patches.zee5.ads

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.firstMethodOrNull
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

/**
 * The player module's `AdConfig` lives here in every build seen so far. The class
 * name itself is minified in the mobile build (`config/a`), but the package is not,
 * and neither are its getters — which is what makes the mobile strategy below
 * viable. The filter also keeps us off the IMA SDK, which has its own unrelated
 * `getAdTagUrl()`.
 */
private const val AD_CONFIG_PACKAGE = "Lcom/zee/mediaplayer/config/"

/**
 * Disables video advertising in the ZEE5 Android app.
 *
 * ZEE5 serves ads down two independent paths, both driven by Google IMA:
 *
 *  - **Client-side (CSAI)** — the VMAP playlist is assembled locally and handed to
 *    ExoPlayer as a `data:text/xml` URI.
 *  - **Server-side (DAI)** — `ImaServerSideAdInsertionUriBuilder`, where ads are
 *    stitched into the stream itself and normally cannot be removed on-device.
 *
 * Both are selected in the player module's `toMediaItem()`, and both hang off a
 * single `AdConfig`:
 *
 * ```
 * if (adConfig != null && adConfig.getDaiAssetId()?.isNotEmpty()) return toImaServerSideMediaItem(...)
 * builder.setAdsConfiguration(adConfig?.getAdTagUrl()?.let { AdsConfiguration(it) })
 * ```
 *
 * Defeat that one object and both paths go with it — playback falls through to the
 * plain content URL. Confirmed empirically on the TV build for on-demand *and*
 * live channels.
 *
 * Two builds, two ways in. The patch tries them in order and applies whichever
 * fits, so a single patch covers both rather than making the user pick.
 */
@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes pre-roll, mid-roll and post-roll video ads, on both " +
        "on-demand content and live channels.",
) {
    // Deliberately version-less: both hooks are matched structurally rather than by
    // offset, so they should survive point releases.
    compatibleWith("com.graymatrix.did")

    apply {
        // --- Strategy 1: Android TV builds ------------------------------------
        //
        // `PlaybackViewModel.toMediaConfig()` discards the whole AdConfig when
        // `PlaybackBridge.canDisableAds()` is true. Stock, that is
        // `isSubscribed() && !enableAdsForSubscribed(remoteConfig)` — so forcing it
        // true puts the app into a state it already ships and exercises for every
        // paying subscriber. Preferred where available precisely because it is a
        // path the app already takes, not one we invented.
        //
        // Matched by shape, not class name: the TV build implements the bridge in
        // com.zee5.androidtv.playback.DefaultPlaybackBridge. `implementation != null`
        // skips the abstract declaration on the interface itself, which has no body
        // to patch.
        val canDisableAds = firstMethodOrNull {
            name == "canDisableAds" &&
                returnType == "Z" &&
                parameterTypes.isEmpty() &&
                implementation != null
        }

        if (canDisableAds != null) {
            // Prepending an unconditional `return true` leaves the original body as
            // dead code, which the dex verifier accepts. v0 is safe: the stock
            // method is `.locals 1`, and clobbering it cannot matter when the very
            // next instruction returns.
            canDisableAds.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )

            return@apply
        }

        // --- Strategy 2: mobile builds ----------------------------------------
        //
        // The mobile build has no such bridge — R8 minifies the player module, and
        // more importantly the AdConfig is *always* constructed there; ads are
        // turned off by leaving its fields empty rather than by nulling the object.
        // So there is nothing to force true.
        //
        // Instead, empty it at the source: null both getters. From toMediaItem's
        // point of view that is indistinguishable from a null AdConfig — no ad tag
        // URL, so no client-side ads; no DAI asset id, so the server-side branch is
        // never entered.
        //
        // Safe by the app's own declaration: both getters are annotated @Nullable,
        // and every caller null-checks them.
        val getters = listOf("getAdTagUrl", "getDaiAssetId")

        val patched = getters.mapNotNull { getter ->
            firstMethodOrNull {
                name == getter &&
                    returnType == "Ljava/lang/String;" &&
                    parameterTypes.isEmpty() &&
                    implementation != null &&
                    // Without this we would also match the IMA SDK's own
                    // getAdTagUrl() and patch the wrong class entirely.
                    definingClass.startsWith(AD_CONFIG_PACKAGE)
            }?.also { method ->
                // Stock getters are `.locals 1` (iget-object v0 / return-object v0),
                // so v0 is always available here.
                method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return-object v0
                    """,
                )
            }
        }

        if (patched.size != getters.size) {
            throw PatchException(
                "Found neither PlaybackBridge.canDisableAds() nor both AdConfig ad " +
                    "getters in $AD_CONFIG_PACKAGE (matched ${patched.size} of " +
                    "${getters.size}). ZEE5 may have restructured playback — re-run " +
                    "recon and check that toMediaItem() still gates both the DAI and " +
                    "client-side branches on a single AdConfig.",
            )
        }
    }
}
