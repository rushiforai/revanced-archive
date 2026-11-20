package app.revanced.patches.youtube.utils.missingresources

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.youtube.general.toolbar.attributeResolverFingerprint
import app.revanced.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.youtube.utils.patch.PatchList.ADD_MISSING_RESOURCES
import app.revanced.patches.youtube.utils.settings.ResourceUtils.addPreference
import app.revanced.patches.youtube.utils.settings.settingsPatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import app.revanced.util.fingerprint.methodOrThrow

private val addMissingResourcesBytecodePatch = bytecodePatch {
    execute {
        // Hook navigation bar getDrawable to fix a crash when a resource is not found
        // When resource id is 0x0, replace it with a transparent image
        navigationBarGetDrawableFingerprint.methodOrThrow().apply {
            addInstructionsWithLabels(
                0,
                """
                if-nez p1, :original
                const p1, 0x7f080144 # @drawable/button_color_transparent_background
                """,
                ExternalLabel("original", getInstruction(0))
            )
        }

        // Hook attribute resolver to fix a crash when a resource is not found in the toolbar
        attributeResolverFingerprint.methodOrThrow().apply {
            addInstructionsWithLabels(
                0,
                """
                if-nez p1, :original
                const/4 v0, 0x0
                return-object v0
                """,
                ExternalLabel("original", getInstruction(0))
            )
        }
    }
}

@Suppress("unused")
val addMissingResourcesPatch = resourcePatch(
    ADD_MISSING_RESOURCES.title,
    ADD_MISSING_RESOURCES.summary,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(
        settingsPatch,
        addMissingResourcesBytecodePatch,
    )

    execute {

        // region set some aliases
        document("res/values/drawables.xml").use { document ->
            val rootNode = document.documentElement

            mapOf(
                // Server-side Cairo navigation icons introduced after YouTube 17.34.36.
                "yt_fill_home_cairo_black_24" to "@drawable/yt_fill_home_black_24",
                "yt_outline_home_cairo_black_24" to "@drawable/yt_outline_home_black_24",
                "yt_fill_subscriptions_cairo_black_24" to "@drawable/yt_fill_subscriptions_black_24",
                "yt_outline_subscriptions_cairo_black_24" to "@drawable/yt_outline_subscriptions_black_24",
                "yt_fill_youtube_shorts_cairo_black_24" to "@drawable/yt_fill_youtube_shorts_black_24",
                "yt_outline_youtube_shorts_cairo_black_24" to "@drawable/yt_outline_youtube_shorts_black_24",
                "yt_fill_library_cairo_black_24" to "@drawable/yt_fill_library_black_24",
                "yt_outline_library_cairo_black_24" to "@drawable/yt_outline_library_black_24",
                "yt_fill_bell_cairo_black_24" to "@drawable/yt_fill_bell_black_24",
                "yt_outline_bell_cairo_black_24" to "@drawable/yt_outline_bell_black_24",

                // Modern Shorts top-bar Cairo icon names. Keep 17.34.36's
                // original white legacy Shorts glyphs instead of the 20.xx look.
                "yt_fill_search_cairo_black_24" to "@drawable/yt_fill_search_black_24",
                "yt_outline_search_cairo_black_24" to "@drawable/yt_outline_search_black_24",
                "yt_outline_overflow_vertical_cairo_black_24" to "@drawable/yt_outline_overflow_vertical_black_24",
                "yt_fill_chromecast_cairo_black_24" to "@drawable/yt_fill_chromecast_black_24",
                "yt_outline_chromecast_cairo_black_24" to "@drawable/yt_outline_chromecast_black_24",
                "yt_fill_compass_cairo_black_24" to "@drawable/yt_fill_compass_black_24",
                "yt_outline_compass_cairo_black_24" to "@drawable/yt_outline_compass_black_24",
                "yt_outline_gear_cairo_black_24" to "@drawable/yt_outline_gear_black_24",
                "yt_outline_share_cairo_black_24" to "@drawable/yt_outline_share_black_24",
                "yt_outline_arrow_left_cairo_black_24" to "@drawable/yt_outline_arrow_left_black_24",

                // Modern Shorts action button names requested by server-side layouts.
                // YouTube 17.34.36 already has the original white shadowed buttons, so
                // keep the legacy look and only add missing aliases.
                "youtube_shorts_like_outline_32dp" to "@drawable/ic_right_like_off_shadowed",
                "youtube_shorts_like_fill_32dp" to "@drawable/ic_right_like_on_shadowed",
                "youtube_shorts_thumbs_up_outline_28dp" to "@drawable/ic_right_like_off_shadowed",
                "youtube_shorts_thumbs_up_fill_28dp" to "@drawable/ic_right_like_on_shadowed",
                "youtube_shorts_dislike_outline_32dp" to "@drawable/ic_right_dislike_off_shadowed",
                "youtube_shorts_dislike_fill_32dp" to "@drawable/ic_right_dislike_on_shadowed",
                "youtube_shorts_thumbs_down_outline_28dp" to "@drawable/ic_right_dislike_off_shadowed",
                "youtube_shorts_thumbs_down_fill_28dp" to "@drawable/ic_right_dislike_on_shadowed",
                "youtube_shorts_comment_outline_28dp" to "@drawable/ic_right_comment_shadowed",
                "youtube_shorts_comment_outline_32dp" to "@drawable/ic_right_comment_shadowed",
                "youtube_shorts_share_outline_28dp" to "@drawable/ic_right_share_shadowed",
                "youtube_shorts_share_outline_32dp" to "@drawable/ic_right_share_shadowed",
                "youtube_shorts_remix_outline_28dp" to "@drawable/ic_remix_filled_white_shadowed",
                "youtube_shorts_remix_outline_32dp" to "@drawable/ic_remix_filled_white_shadowed",
                "youtube_shorts_save_outline_28dp" to "@drawable/yt_outline_bookmark_black_24",
                "youtube_shorts_save_outline_32dp" to "@drawable/yt_outline_bookmark_black_24",
                "youtube_shorts_save_fill_28dp" to "@drawable/yt_fill_bookmark_black_24",
                "youtube_shorts_save_fill_32dp" to "@drawable/yt_fill_bookmark_black_24",
                "youtube_shorts_save_fill_selected_32dp" to "@drawable/yt_fill_bookmark_black_24",
                "youtube_shorts_save_fill_unselected_32dp" to "@drawable/yt_outline_bookmark_black_24",
                "youtube_shorts_original_sound_16dp" to "@drawable/quantum_ic_music_note_white_24",
                "youtube_shorts_pivot_fab" to "@drawable/ic_youtube_shorts_24",

                // Other small Shorts resources introduced after 17.34.36.
                "ic_youtube_shorts_24_cairo" to "@drawable/ic_youtube_shorts_24",

                // Comments
                "yt_outline_thumb_up_cairo_black_24" to "@drawable/yt_outline_thumb_up_black_24",
                "yt_outline_thumb_down_cairo_black_24" to "@drawable/yt_outline_thumb_down_black_24",
                "yt_fill_thumb_up_cairo_black_24" to "@drawable/yt_fill_thumb_up_black_24",
                "yt_fill_thumb_down_cairo_black_24" to "@drawable/yt_fill_thumb_down_black_24",
                "yt_fill_spark_cairo_black_24" to "@drawable/yt_fill_sparkle_white_24",

            ).forEach { (key, value) ->
                val newElement = document.createElement("drawable")
                newElement.setAttribute("name", key)
                newElement.textContent = value
                rootNode.appendChild(newElement)
            }
        }

        // endregion

        // region add resources

        copyResources(
            "addmissingresources",
            ResourceGroup("drawable", "ic_waveform_elements.xml")
        )

        // endregion

        addPreference(ADD_MISSING_RESOURCES)

    }
}
