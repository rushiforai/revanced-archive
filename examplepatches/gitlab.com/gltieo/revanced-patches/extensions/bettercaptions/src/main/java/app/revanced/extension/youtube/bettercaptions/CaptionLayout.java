package app.revanced.extension.youtube.bettercaptions;

import android.graphics.Paint;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.ResourceType;
import app.revanced.extension.shared.Utils;
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
     * @return Whether the player is showing its own buttons, which the captions stand
     *         clear of and never take a tap from.
     */
    public static boolean controlsShowing(View anyViewInThePlayer) {
        try {
            View root = anyViewInThePlayer.getRootView();

            // The play button is only on screen while the buttons are, whereas the group
            // holding them stays where it is and fades.
            for (String name : new String[]{
                    "player_control_play_pause_replay_button", "controls_layout"}) {
                final int id = Utils.getResourceIdentifier(ResourceType.ID, name);
                if (id == 0) continue;

                View control = root.findViewById(id);
                if (control == null) continue;

                final boolean showing = control.getVisibility() == View.VISIBLE
                        && control.getAlpha() > 0.4f;
                if (showing != controlsWereShowing) {
                    controlsWereShowing = showing;
                    Logger.printDebug(() -> "The player's buttons are "
                            + (showing ? "on screen" : "away") + " (" + name + ")");
                }
                return showing;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean controlsWereShowing;

    /**
     * @return How far from the given edge of the player the captions have to stay while
     *         the player is showing its buttons, which is however much of that edge the
     *         buttons cover.
     *
     * The player draws everything at its foot from the time bar downwards, the row of
     * buttons sitting under it, and everything at its head from the top down to the
     * title. So the room to leave is the distance from the edge to the far side of what
     * is drawn there, whatever size the screen is and however tall the player has made
     * its bars.
     */
    public static int clearanceAt(View overlay, boolean top) {
        try {
            // Only while the player is showing its buttons: with them away the captions
            // belong at the edge, and anything else that happens to be drawn there, a
            // watermark or a pill, must not move them.
            if (!controlsShowing(overlay)) return 0;

            final Rect player = onScreen(overlay);
            if (player.height() <= 0) return 0;

            // What the player has drawn over its own picture at that edge: a bar or a row
            // of buttons runs most of the way across and is a fraction of the height, so
            // that is what is looked for, rather than any particular view of the app's.
            final int band = player.height() / 3;
            final Rect edge = top
                    ? new Rect(player.left, player.top, player.right, player.top + band)
                    : new Rect(player.left, player.bottom - band, player.right, player.bottom);

            int covered = 0;
            for (View control : rowsOver(overlay.getRootView(), player, edge)) {
                final Rect drawn = onScreen(control);
                // Only the part of it that lies in the band counts: a view that reaches
                // up the whole player covers the edge no more than the band is deep.
                covered = Math.max(covered, top
                        ? Math.min(drawn.bottom, edge.bottom) - player.top
                        : player.bottom - Math.max(drawn.top, edge.top));
            }
            return Math.min(covered, band);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Rect onScreen(View view) {
        int[] place = new int[2];
        view.getLocationOnScreen(place);
        return new Rect(place[0], place[1],
                place[0] + view.getWidth(), place[1] + view.getHeight());
    }

    /**
     * @return The bars and rows of buttons the player has drawn in the given band of
     *         itself: wide, short, and on screen.
     */
    private static List<View> rowsOver(View root, Rect player, Rect edge) {
        List<View> rows = new ArrayList<>();
        collectRows(root, player, edge, rows, 1f);
        return rows;
    }

    private static void collectRows(View view, Rect player, Rect edge, List<View> rows,
                                    float alpha) {
        // A row inside a group that has faded away is faded away with it, however solid
        // it believes itself to be: the player fades its buttons as a whole.
        final float shown = alpha * view.getAlpha();
        if (view.getVisibility() != View.VISIBLE || shown < 0.4f) return;
        // The patch's own lines are wide and short as well, and a line that counted
        // itself would push itself further up the picture with every pass.
        if (view instanceof CaptionLineView) return;

        if (view.getWidth() > player.width() / 3 && view.getHeight() > 0
                && view.getHeight() < edge.height() / 2) {
            // Wholly inside the band, so that a group reaching up the whole player, or
            // the row of buttons in the middle of it, is not taken for a bar at the edge.
            final Rect drawn = onScreen(view);
            if (edge.contains(drawn.left, drawn.top, drawn.right, drawn.bottom)
                    || (drawn.top >= edge.top && drawn.bottom <= edge.bottom)) {
                rows.add(view);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectRows(group.getChildAt(index), player, edge, rows, shown);
            }
        }
    }

    /**
     * What the player draws at each edge: the lowest thing at its head, and the highest
     * at its foot.
     */
    private static final String[] HEAD_OF_THE_PLAYER =
            {"player_video_title_view", "player_collapse_button", "player_overflow_button"};
    private static final String[] FOOT_OF_THE_PLAYER =
            {"time_bar", "watch_while_time_bar_view"};

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
