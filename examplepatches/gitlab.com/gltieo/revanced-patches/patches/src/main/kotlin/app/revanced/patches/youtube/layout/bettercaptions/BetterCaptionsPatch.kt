package app.revanced.patches.youtube.layout.bettercaptions

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod.Companion.toMutable
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstClassDef
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import app.revanced.patches.shared.misc.settings.preference.NonInteractivePreference
import app.revanced.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import app.revanced.patches.youtube.misc.extension.sharedExtensionPatch
import app.revanced.patches.youtube.misc.playertype.playerTypeHookPatch
import app.revanced.patches.youtube.misc.settings.PreferenceScreen
import app.revanced.patches.youtube.misc.settings.settingsPatch
import app.revanced.patches.youtube.shared.getLayoutConstructorMethodMatch
import app.revanced.patches.youtube.video.information.videoInformationPatch
import app.revanced.patches.youtube.video.information.videoTimeHook
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/youtube/bettercaptions/BetterCaptionsOverlay;"
/**
 * The words this patch shows, added to the app itself.
 *
 * They cannot go through the shared pool of strings every other patch uses. That pool is
 * one file read off the classpath, and this bundle is meant to be picked alongside
 * ReVanced's own, whose copy of the file is found first and knows nothing of these. The
 * preferences would then name strings that were never added and the app would fail to
 * build its resources.
 */
private val betterCaptionsStrings = mapOf(
    "revanced_better_captions_screen_title" to "Better captions",
    "revanced_better_captions_screen_summary"
            to "How captions look and where they sit, and a second language under them",
    "revanced_better_captions_enabled_title" to "Better captions",
    "revanced_better_captions_enabled_summary_on" to "Captions are drawn as arranged below",
    "revanced_better_captions_enabled_summary_off" to "YouTube draws its own captions",
    "revanced_better_captions_preview_title" to "Position and style",
    "revanced_better_captions_preview_first"
            to "caption in first language, long enough\nto take the two lines it keeps",
    "revanced_better_captions_preview_second"
            to "caption in second language, long enough\nto take the two lines it keeps",
    "revanced_better_captions_color_title" to "Color",
)

internal val betterCaptionsStringsPatch = resourcePatch {
    apply {
        document("res/values/strings.xml").use { document ->
            val resources = document.documentElement

            val existing = buildSet {
                val children = resources.childNodes
                for (index in 0 until children.length) {
                    val node = children.item(index)
                    if (node is Element && node.tagName == "string") add(node.getAttribute("name"))
                }
            }

            betterCaptionsStrings.forEach { (name, text) ->
                if (name in existing) return@forEach

                resources.appendChild(
                    document.createElement("string").apply {
                        setAttribute("name", name)
                        textContent = text
                    },
                )
            }
        }
    }
}

private const val EXTENSION_MENU_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/youtube/bettercaptions/BetterCaptionsMenu;"

@Suppress("unused")
val betterCaptionsPatch = bytecodePatch(
    name = "Better captions",
    description = "Adds an option to show two subtitle lines at the same time, " +
            "the spoken language and a translation, for language learning.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        addResourcesPatch,
        // Adds the words this patch needs, which cannot come from the shared pool.
        betterCaptionsStringsPatch,
        resourceMappingPatch,
        // Supplies the video id, the playback position and the playback speed.
        videoInformationPatch,
        // Supplies whether the regular player is on screen.
        playerTypeHookPatch,
    )

    compatibleWith(
        "com.google.android.youtube"(
            "20.14.43",
            "20.21.37",
            "20.26.46",
            "20.31.42",
            "20.37.48",
            "20.40.45",
        ),
    )

    // This patch's own code, kept out of the extension every ReVanced bundle carries.
    // That one is a single file on the classpath: with two bundles picked only one copy
    // is found, and a patch whose code rides along in it is then not in the app at all.
    extendWith("extensions/bettercaptions.rve")

    apply {
        PreferenceScreen.PLAYER.addPreferences(
            PreferenceScreenPreference(
                key = "revanced_better_captions_screen",
                // Sorted by title, the switch that turns the whole thing on could land
                // anywhere and the preview with it, so the order written here is kept.
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("revanced_better_captions_enabled"),
                    NonInteractivePreference(
                        key = "revanced_better_captions_preview",
                        // The preview says what it is by showing it; a summary line
                        // above it would only repeat the picture.
                        summaryKey = null,
                        tag = "app.revanced.extension.youtube.bettercaptions.ui.CaptionPreviewPreference",
                        selectable = false,
                    ),
                ),
            ),
        )

        // Hand over the view group covering the player, right after the app casts it
        // to a FrameLayout. The subtitle lines are added as a child of it, which puts
        // them above the video and below nothing else.
        getLayoutConstructorMethodMatch().immutableClassDef.controlsOverlayMethodMatch.let {
            val checkCastIndex = it[-1]

            it.method.apply {
                val frameLayoutRegister =
                    getInstruction<OneRegisterInstruction>(checkCastIndex).registerA

                addInstruction(
                    checkCastIndex + 1,
                    "invoke-static {v$frameLayoutRegister}, " +
                            "$EXTENSION_CLASS_DESCRIPTOR->initialize(Landroid/view/ViewGroup;)V",
                )
            }
        }

        announceTheAppsOwnCaptions()
        followTheAppsCaptionState()
        handOverTheSubtitleTracks()

        // Report the playback position. This fires about once per second,
        // so the extension estimates the position in between.
        videoTimeHook(EXTENSION_CLASS_DESCRIPTOR, "setVideoTime")

        // The captions menu built out of a ListView, which the player asks for instead
        // of the sheet below on some videos. Its view is handed over once the app has
        // finished with it.
        captionsListMenuViewMethodMatch.method.apply {
            val returnIndex = indexOfFirstInstructionReversedOrThrow(Opcode.RETURN_OBJECT)
            val viewRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA

            addInstruction(
                returnIndex,
                "invoke-static { v$viewRegister }, " +
                        "$EXTENSION_MENU_CLASS_DESCRIPTOR->onCaptionsListCreated(Landroid/view/View;)V",
            )
        }

        // Every element renderer bottom sheet passes through here, the captions list
        // among them. Which sheet it is can only be told from what it renders, so the
        // extension decides whether to add its rows.
        elementBottomSheetFragmentMethod.immutableClassDef.bottomSheetCreateViewMethodMatch.let {
            it.method.apply {
                val returnIndex = indexOfFirstInstructionReversedOrThrow(Opcode.RETURN_OBJECT)
                val viewRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA

                addInstruction(
                    returnIndex,
                    "invoke-static { v$viewRegister }, " +
                            "$EXTENSION_MENU_CLASS_DESCRIPTOR->onBottomSheetCreated(Landroid/view/View;)V",
                )
            }
        }
    }
}

/**
 * Says when the video starts and stops showing captions.
 *
 * The patch draws captions of its own, but only where the app would have drawn its own:
 * a video watched with captions off is watched with no captions at all, and takes no room
 * for them either.
 */
private fun BytecodePatchContext.followTheAppsCaptionState() {
    captionsButtonStateMethod.let { state ->
        val owner = firstClassDef { type == state.definingClass }
        val original = owner.methods.first {
            it.name == state.name && it.parameterTypes == state.parameterTypes
        }
        val hooked = original.toMutable()

        hooked.addInstruction(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->onCaptionsEnabled(Z)V",
        )

        owner.methods.remove(original)
        owner.methods.add(hooked)
    }
}

/**
 * Hands the extension the object the app picks caption tracks with.
 *
 * The captions menu the patch draws replaces the app's own list of tracks, so turning
 * captions on there has to reach the app as well: its own captions button reads from it,
 * and the next video starts the way it was left.
 */
private fun BytecodePatchContext.handOverTheSubtitleTracks() {
    subtitlesManagerMethod.let { manager ->
        val owner = firstClassDef { type == manager.definingClass }
        val original = owner.methods.first {
            it.name == manager.name && it.parameterTypes == manager.parameterTypes
        }
        val hooked = original.toMutable()

        hooked.addInstruction(
            0,
            "invoke-static { p0 }, " +
                    "$EXTENSION_MENU_CLASS_DESCRIPTOR->rememberSubtitleTracks(Ljava/lang/Object;)V",
        )

        owner.methods.remove(original)
        owner.methods.add(hooked)
    }
}

/**
 * Has the view YouTube draws its own captions into say so when it is made.
 *
 * Hunting for it in the view tree by the name of its class was not good enough: it is
 * built when the first caption of a video arrives rather than with the player, and a
 * search that runs before then finds nothing, while one that runs after costs a walk of
 * the whole window on every frame. Being told once is exact and free.
 */
private fun BytecodePatchContext.announceTheAppsOwnCaptions() {
    val captionView = firstClassDef { type == CAPTION_VIEW_CLASS }

    val constructors = captionView.methods.filter { it.name == "<init>" }
    check(constructors.isNotEmpty()) { "The caption view has no constructor to hook" }

    constructors.forEach { constructor ->
        val hooked = constructor.toMutable()
        val returnIndex = hooked.indexOfFirstInstructionReversedOrThrow(Opcode.RETURN_VOID)

        hooked.addInstruction(
            returnIndex,
            "invoke-static { p0 }, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->onOriginalCaptionsCreated(Landroid/view/View;)V",
        )

        captionView.methods.remove(constructor)
        captionView.methods.add(hooked)
    }
}
