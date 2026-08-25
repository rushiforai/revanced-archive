package app.revanced.extension.youtube.bettercaptions;

import android.graphics.Paint;
import android.util.TypedValue;

import app.revanced.extension.shared.ui.Dim;

/**
 * How much room a caption line keeps, and which of the four slots the second line sits
 * in.
 *
 * The lines are drawn over the player, the way the app draws its own captions. Nothing
 * about the player is moved or resized for them.
 */
public final class CaptionLayout {

    /**
     * Space between two lines sharing an edge, and between a line and its edge.
     */
    public static final int LINE_GAP_DIP = 2;
    public static final int EDGE_MARGIN_DIP = 4;

    /**
     * How many lines of text a caption keeps room for. A sentence wraps to two about as
     * often as it fits on one, and room that came and went with it would move the video
     * on every subtitle.
     */
    public static final int LINES_PER_CAPTION = 2;

    /**
     * @return Where the second line goes. Two lines never share a slot; if the settings
     *         ever say so, one moves aside.
     */
    public static CaptionSlot secondSlot(CaptionSlot spokenSlot) {
        CaptionSlot slot = BetterCaptionsSettings.SECOND_SLOT.get();
        return slot == spokenSlot ? slot.otherRow() : slot;
    }

    /**
     * The room one caption keeps, whatever it happens to say.
     */
    public static int slotHeight(int textSizeSp) {
        return LINES_PER_CAPTION * lineHeight(textSizeSp)
                + 2 * Dim.dp(CaptionLineView.PADDING_VERTICAL_DIP);
    }

    private static final Paint measuring = new Paint();

    private static int lineHeight(int textSizeSp) {
        measuring.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, textSizeSp,
                android.content.res.Resources.getSystem().getDisplayMetrics()));

        Paint.FontMetrics metrics = measuring.getFontMetrics();
        return Math.round(metrics.descent - metrics.ascent + metrics.leading);
    }




    /**
     * The fraction of its parent a line was dragged to, which is all a drop needs to
     * know: it says which of the four slots was meant.
     */
    public static float fractionY(int playerHeight, int lineHeight, int y) {
        if (playerHeight <= 0) return 0.9f;
        return Math.max(0f, Math.min(1f, (y + lineHeight / 2f) / playerHeight));
    }

    private CaptionLayout() {
    }
}
