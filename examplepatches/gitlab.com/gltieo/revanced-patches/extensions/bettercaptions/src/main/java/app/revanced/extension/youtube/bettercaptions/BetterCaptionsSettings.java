package app.revanced.extension.youtube.bettercaptions;

import static app.revanced.extension.shared.settings.Setting.parent;

import app.revanced.extension.shared.settings.BooleanSetting;
import app.revanced.extension.shared.settings.EnumSetting;
import app.revanced.extension.shared.settings.IntegerSetting;
import app.revanced.extension.shared.settings.StringSetting;

/**
 * What this patch remembers, kept to itself.
 *
 * It does not go in the app's own settings class the way every other patch's does. This
 * bundle is meant to be picked alongside ReVanced's, and both carry a copy of that class:
 * only one of them can end up in the app, and whichever loses takes its settings with it.
 * A class of this patch's own belongs to no one else and cannot be replaced by another
 * bundle's copy.
 *
 * Settings put themselves on the list as they are made, so this class has to be loaded
 * before the settings screen is built. Touching it from
 * {@link app.revanced.extension.youtube.bettercaptions.ui.CaptionPreviewPreference}, which
 * the screen builds as it inflates, is what does that.
 */
public final class BetterCaptionsSettings {

    public static final BooleanSetting ENABLED =
            new BooleanSetting("revanced_better_captions_enabled", Boolean.TRUE);
    /**
     * Whether the captions are showing. The captions button of the player sets it, and so
     * does the row in the captions menu; it is remembered the way the app remembers its
     * own.
     */
    public static final BooleanSetting CAPTIONS_ON =
            new BooleanSetting("revanced_better_captions_on", Boolean.FALSE);
    /**
     * Language of the upper line. Empty means the language the video is spoken in.
     */
    public static final StringSetting FIRST_LANGUAGE =
            new StringSetting("revanced_better_captions_first_language", "");
    /**
     * Language of the lower line. Empty means there is no lower line.
     */
    public static final StringSetting LANGUAGE =
            new StringSetting("revanced_better_captions_language", "");
    public static final IntegerSetting TEXT_SIZE =
            new IntegerSetting("revanced_better_captions_text_size", 17);
    public static final IntegerSetting SECOND_TEXT_SIZE =
            new IntegerSetting("revanced_better_captions_second_text_size", 15);
    public static final IntegerSetting COLOR =
            new IntegerSetting("revanced_better_captions_color", 0xFFFFFFFF);
    public static final IntegerSetting SECOND_COLOR =
            new IntegerSetting("revanced_better_captions_second_color", 0xFFFFE082);
    public static final IntegerSetting BACKGROUND_OPACITY =
            new IntegerSetting("revanced_better_captions_background_opacity", 75);
    public static final EnumSetting<CaptionSlot> SLOT =
            new EnumSetting<>("revanced_better_captions_slot", CaptionSlot.BOTTOM_FIRST);
    public static final EnumSetting<CaptionSlot> SECOND_SLOT =
            new EnumSetting<>("revanced_better_captions_second_slot", CaptionSlot.BOTTOM_SECOND);

    /**
     * Whether the two lines have been put back under the video once.
     *
     * While the video was made to keep clear of the captions, the second line was moved
     * to the opposite edge for the room to be used, which left the arrangement split
     * between the top and the bottom. That feature is gone, so the arrangement is put
     * back together once.
     */
    private static final BooleanSetting SLOTS_SETTLED =
            new BooleanSetting("revanced_better_captions_slots_settled", Boolean.FALSE);

    /**
     * Loads this class, and with it every setting above.
     */
    public static void load() {
        if (SLOTS_SETTLED.get()) return;

        SLOTS_SETTLED.save(Boolean.TRUE);
        if (SLOT.get().isTop() || SECOND_SLOT.get().isTop()) {
            SLOT.save(CaptionSlot.BOTTOM_FIRST);
            SECOND_SLOT.save(CaptionSlot.BOTTOM_SECOND);
        }
    }

    private BetterCaptionsSettings() {
    }
}
