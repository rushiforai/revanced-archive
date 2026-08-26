package app.revanced.patches.youtube.layout.bettercaptions

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.iface.Method
import app.revanced.patches.shared.misc.mapping.ResourceType
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.ClassDef

/**
 * A method of the fragment behind every element renderer bottom sheet, which is what the
 * captions list is drawn in. Only used to reach the class; the interesting method is
 * [bottomSheetCreateViewMethodMatch].
 *
 * The app also builds the captions menu out of a ListView, and shows whichever of the two
 * the player asks for; that one is [captionsListMenuViewMethodMatch].
 */
internal val BytecodePatchContext.elementBottomSheetFragmentMethod
    by gettingFirstMethodDeclaratively("ELEMENT_RENDERER_BOTTOM_SHEET_FRAGMENT_KEY") {
        predicate { true }
    }

/**
 * The method that creates the view of a bottom sheet, resolved inside the class found by
 * [elementBottomSheetFragmentMethod].
 */
internal val ClassDef.bottomSheetCreateViewMethodMatch by ClassDefComposing.composingFirstMethod {
    returnType("Landroid/view/View;")
    parameterTypes(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;",
    )
}

/**
 * The method that builds the view of the captions menu the app makes out of a list rather
 * than out of server-sent components.
 *
 * The app has both and shows whichever the player asks for, so both have to be found. This
 * one puts the caption tracks into a ListView itself and ends by adding a footer that
 * points at the phone's caption settings, which is what it is recognised by.
 */
internal val BytecodePatchContext.captionsListMenuViewMethodMatch by composingFirstMethod {
    returnType("Landroid/view/View;")
    parameterTypes(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;",
    )
    instructions(
        ResourceType.ID("bottom_sheet_list_view"),
        ResourceType.LAYOUT("bottom_sheet_list_fragment_footer"),
        ResourceType.STRING("subtitle_menu_settings_footer_info"),
    )
}

/**
 * The method that says whether the video is showing captions.
 *
 * It is the one that turns the captions button on and off, and it remembers the answer
 * under a name of its own, which is what it is found by.
 */
internal val BytecodePatchContext.captionsButtonStateMethod by gettingFirstMethodDeclaratively(
    "menu_item_captions",
) {
    parameterTypes("Z")
    returnType("V")
}

/**
 * A method of the class that holds the caption tracks of the video and picks between
 * them, found by the two words it builds its menu out of.
 *
 * Only used to reach the object: the extension is handed it and finds the list of tracks
 * and the way one is chosen on it by their shapes, since their names do not survive
 * obfuscation.
 */
internal val BytecodePatchContext.subtitlesManagerMethod by gettingFirstMethodDeclaratively {
    returnType("V")
    parameterTypes(
        "Lcom/google/android/libraries/youtube/innertube/model/player/PlayerResponseModel;",
        "L",
    )
    instructions(
        ResourceType.STRING("turn_off_subtitles"),
        ResourceType.STRING("auto_translate_subtitles"),
    )
}

/**
 * The method that lays out the overlays covering the player, resolved inside the class
 * found by [app.revanced.patches.youtube.shared.getLayoutConstructorMethodMatch].
 *
 * It inflates the inset overlay and casts it to a FrameLayout, which is the group the
 * caption lines are added to.
 */
internal val ClassDef.controlsOverlayMethodMatch by ClassDefComposing.composingFirstMethod {
    returnType("V")
    parameterTypes()
    instructions(
        ResourceType.ID.invoke("inset_overlay_view_layout"),
        afterAtMost(20, allOf(Opcode.CHECK_CAST(), type("Landroid/widget/FrameLayout;"))),
    )
}

/**
 * The watch page layout, which decides how much of the screen the player gets and gives
 * the rest to the list underneath.
 *
 * Unlike almost everything around it the name survives obfuscation, so it is written
 * down rather than described.
 */
internal const val WATCH_LAYOUT_CLASS =
    "Lcom/google/android/apps/youtube/app/watch/nextgenwatch/ui/NextGenWatchLayout;"

/**
 * The view YouTube draws its own captions into.
 *
 * Part of a library the app links rather than of the app itself, so unlike almost
 * everything around it the name survives obfuscation.
 */
internal const val CAPTION_VIEW_CLASS =
    "Lcom/google/android/libraries/youtube/player/subtitles/ui/SubtitleWindowView;"
