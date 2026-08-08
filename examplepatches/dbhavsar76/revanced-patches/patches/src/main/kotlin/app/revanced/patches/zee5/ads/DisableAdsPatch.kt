package app.revanced.patches.zee5.ads

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.firstMethodOrNull
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

/**
 * Disables video advertising in the ZEE5 Android app.
 *
 * ZEE5 serves ads down two independent paths, both driven by Google IMA:
 *
 *  - **Client-side (CSAI)** — `com.zee.mediaplayer.ads.VmapBuilder` assembles the
 *    VMAP playlist locally and hands it to ExoPlayer as a `data:text/xml` URI.
 *  - **Server-side (DAI)** — `ImaServerSideAdInsertionUriBuilder`, where ads are
 *    stitched into the stream itself and normally cannot be removed on-device.
 *
 * Both paths are selected inside `MediaUtilsKt.toMediaItem()`, and both are gated
 * on a nullable `AdConfig`:
 *
 * ```
 * if (adConfig != null && adConfig.getDaiAssetId()?.isNotEmpty()) return toImaServerSideMediaItem(...)
 * MediaItem.Builder().setUri(mediaConfig.url).setAdsConfiguration(adsConfiguration(adConfig))
 * ```
 *
 * Upstream of that, `PlaybackViewModel.toMediaConfig()` nulls the whole `AdConfig`
 * when `PlaybackBridge.canDisableAds()` is true. With no `AdConfig` there is no ad
 * tag URL *and* no DAI asset id, so the DAI branch is never entered and playback
 * falls through to the plain content URL. One boolean therefore defeats both —
 * confirmed empirically on both on-demand content and live channels.
 *
 * The stock implementation is:
 *
 * ```
 * canDisableAds() = authHelper.isSubscribed() && !enableAdsForSubscribed(remoteConfig)
 * ```
 *
 * Forcing it true puts the app into a state it already ships and exercises for
 * every paying subscriber, which is why this is far safer than nulling the config
 * ourselves — no untested code path, no NPE risk. It governs only whether an
 * `AdConfig` is attached to playback; entitlement, DRM licensing and which content
 * is playable are enforced elsewhere and are untouched.
 *
 * Verified against 5.82.7 (versionCode 203230931), Android TV build.
 */
@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes pre-roll, mid-roll and post-roll video ads, on both " +
        "on-demand content and live channels.",
) {
    // Deliberately version-less: the hook is matched structurally rather than by
    // offset, so it should survive point releases. Narrow this if a future
    // version ever needs a different approach.
    compatibleWith("com.graymatrix.did")

    apply {
        // Matched by shape, not by class name: the Android TV build implements
        // PlaybackBridge in com.zee5.androidtv.playback.DefaultPlaybackBridge, but
        // the mobile build uses a different class, so pinning the name would make
        // this patch TV-only. `implementation != null` skips the abstract
        // declaration on the PlaybackBridge interface itself — that one has no body
        // to patch, and matching it would fail at addInstructions.
        val canDisableAds = firstMethodOrNull {
            name == "canDisableAds" &&
                returnType == "Z" &&
                parameterTypes.isEmpty() &&
                implementation != null
        } ?: throw PatchException(
            "Could not find an implementation of PlaybackBridge.canDisableAds(). " +
                "ZEE5 may have renamed, inlined or restructured it — re-run recon " +
                "against this version and check that PlaybackViewModel still nulls " +
                "AdConfig on the strength of a single boolean.",
        )

        // Prepending an unconditional `return true` leaves the original body in
        // place as dead code, which the dex verifier is fine with. v0 is safe
        // here: the stock method is `.locals 1`, so v0 exists, and clobbering it
        // cannot matter when the very next instruction returns.
        canDisableAds.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
