package app.revanced.extension.youtube.bettercaptions;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import android.graphics.Rect;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.ui.Dim;
import app.revanced.extension.youtube.bettercaptions.ui.WordSheet;
import app.revanced.extension.youtube.patches.VideoInformation;
import app.revanced.extension.youtube.shared.PlayerType;
import app.revanced.extension.youtube.shared.VideoState;

/**
 * The caption lines drawn over the player: the language spoken, and a second language.
 *
 * Each line sits in one of four slots, two rows along the top edge and two along the
 * bottom, and stacks against its edge. Lines belong to an edge rather than to a point,
 * because an upright player and a landscape one are different shapes and a position
 * held as a fraction of one lands somewhere else in the other.
 *
 * The player reports its position once per second, which is far too coarse to put a
 * subtitle on screen at the right moment, so the position in between is estimated from
 * the elapsed wall clock and the playback speed and corrected on every report.
 */
@SuppressWarnings("unused")
public final class BetterCaptionsOverlay {

    private static final int REFRESH_INTERVAL_MILLISECONDS = 100;

    /**
     * Space between two lines sharing an edge, and between a line and its edge. In dp
     * rather than a fraction of the player, so it looks the same whatever shape the
     * player is.
     */
    private static final int LINE_GAP_DIP = 2;
    private static final int EDGE_MARGIN_DIP = 4;

    /**
     * How many lines of text a caption keeps room for.
     *
     * A sentence wraps to two lines about as often as it fits on one, and room that
     * came and went with it would move the video on every subtitle. So the room is the
     * same either way, and a single line sits in the middle of it.
     */
    private static final int LINES_PER_CAPTION = 2;

    private static WeakReference<FrameLayout> overlayRef = new WeakReference<>(null);
    private static WeakReference<CaptionLineView> spokenLineRef = new WeakReference<>(null);
    private static WeakReference<CaptionLineView> secondLineRef = new WeakReference<>(null);

    /**
     * The views YouTube draws its own captions into, hidden while this draws the lines
     * itself and put back the moment it stops.
     *
     * There is more than one: the app makes a window per caption position the format
     * asks for, so keeping only the newest left the others on screen, which is what a
     * caption showing through the patch's own looked like.
     */
    private static final List<WeakReference<View>> originalCaptions = new ArrayList<>();

    private static long reportedVideoTime = -1;
    private static long reportedAtRealTime;

    @Nullable
    private static String lastSpokenText;
    @Nullable
    private static String lastSecondText;

    private static final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                refresh();
            } catch (Exception ex) {
                Logger.printException(() -> "refresh failure", ex);
            } finally {
                FrameLayout overlay = overlayRef.get();
                if (overlay != null) {
                    overlay.postDelayed(this, REFRESH_INTERVAL_MILLISECONDS);
                }
            }
        }
    };

    /**
     * Injection point. Called with the view group covering the player.
     */
    public static void initialize(ViewGroup playerOverlays) {
        try {
            Context context = playerOverlays.getContext();

            FrameLayout overlay = new FrameLayout(context);
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            // Nothing here takes touches; they belong to the player underneath.
            overlay.setClickable(false);
            overlay.setFocusable(false);
            // The captions sit in the room made beside the video, which is past the
            // edges of this group: it is as tall as the player was before the room was
            // made.
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);

            // Out of the way until there is something to say, so an empty line never
            // takes room from the video.
            CaptionLineView spoken = new CaptionLineView(context);
            CaptionLineView second = new CaptionLineView(context);
            spoken.setVisibility(View.GONE);
            second.setVisibility(View.GONE);
            spoken.setOnWordTapped(word ->
                    WordSheet.show(context, word, CaptionLines.upperLanguage()));
            second.setOnWordTapped(word ->
                    WordSheet.show(context, word, CaptionLines.lowerLanguage()));
            overlay.addView(spoken);
            overlay.addView(second);

            playerOverlays.addView(overlay);
            playerOverlays.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    FrameLayout existing = overlayRef.get();
                    if (existing != null) existing.bringToFront();
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                }
            });

            overlayRef = new WeakReference<>(overlay);
            spokenLineRef = new WeakReference<>(spoken);
            secondLineRef = new WeakReference<>(second);

            // Positions are settled between layout and drawing, so a frame is never
            // painted with a line still in the place it held before its text changed.
            overlay.getViewTreeObserver().addOnPreDrawListener(() -> {
                try {
                    FrameLayout current = overlayRef.get();
                    CaptionLineView first = spokenLineRef.get();
                    CaptionLineView other = secondLineRef.get();
                    if (current != null && first != null && other != null) {
                        positionLines(current, first, other);
                        // Every frame, not on the tick: the app puts its own captions
                        // back whenever a new sentence arrives, and a tick later is a
                        // frame of them on screen.
                        keepOriginalCaptionsAway(current);
                    }
                } catch (Exception ex) {
                    Logger.printException(() -> "Could not position the caption lines", ex);
                }
                return true;
            });

            overlay.removeCallbacks(refreshRunnable);
            overlay.postDelayed(refreshRunnable, REFRESH_INTERVAL_MILLISECONDS);

            Logger.printDebug(() -> "Captions overlay attached to "
                    + playerOverlays.getClass().getSimpleName());
        } catch (Exception ex) {
            Logger.printException(() -> "initialize failure", ex);
        }
    }

    /**
     * Injection point. Called as YouTube builds the view it draws its own captions into,
     * which is when the first caption of a video arrives rather than when the player is
     * built.
     */
    public static void onOriginalCaptionsCreated(View captionView) {
        try {
            for (Iterator<WeakReference<View>> known = originalCaptions.iterator(); known.hasNext(); ) {
                View view = known.next().get();
                if (view == null) known.remove();
                else if (view == captionView) return;
            }
            originalCaptions.add(new WeakReference<>(captionView));
        } catch (Exception ex) {
            Logger.printException(() -> "onOriginalCaptionsCreated failure", ex);
        }
    }

    /**
     * Injection point. Called on the main thread roughly once per second, and again
     * right after a seek.
     */
    public static void setVideoTime(long videoTime) {
        reportedVideoTime = videoTime;
        reportedAtRealTime = SystemClock.elapsedRealtime();
    }

    /**
     * Redraws after a setting changed, without waiting for the next tick.
     */
    /**
     * Injection point. Called when the app turns the captions of the video on or off,
     * which the patch follows: the captions button of the player is how anyone turns
     * captions on, and the lines drawn here are captions.
     */
    public static void onCaptionsEnabled(boolean enabled) {
        // Every video starts with the app's own captions off and says so, which is not
        // the viewer turning anything off: it is the app before anything has played.
        // Only a change made while there are captions on screen is one of theirs.
        if (!enabled && !CaptionLines.hasSpokenTrack()) {
            Logger.printDebug(() -> "Ignoring the app starting with captions off");
            return;
        }

        setCaptionsOn(enabled);
    }

    /**
     * Whether the captions are showing. Remembered rather than followed, because the
     * lines are drawn by the patch and the app is never asked to draw any: asking it
     * would put its own captions on screen underneath these.
     */
    public static void setCaptionsOn(boolean enabled) {
        if (areCaptionsOn() == enabled) return;

        BetterCaptionsSettings.CAPTIONS_ON.save(enabled);
        Logger.printDebug(() -> "Captions " + (enabled ? "on" : "off"));
        refreshNow();
    }

    /**
     * Turns the captions on or off, here and in the app.
     *
     * The app is told as well so that its own captions button shows the same answer and
     * the next video starts the same way; what it draws is hidden while this patch is on,
     * so only these lines are ever on screen.
     */
    public static void toggleCaptions(boolean enabled) {
        setCaptionsOn(enabled);
        BetterCaptionsMenu.chooseTrack(enabled ? 1 : 0);
    }

    /**
     * Tells the app that this video is showing captions, so that its captions button
     * agrees with what is on screen. What the app then draws is hidden.
     */
    public static void tellTheAppCaptionsAreOn() {
        Utils.runOnMainThreadNowOrLater(() -> BetterCaptionsMenu.chooseTrack(1));
    }

    public static boolean areCaptionsOn() {
        return BetterCaptionsSettings.CAPTIONS_ON.get();
    }



    public static void refreshNow() {
        Utils.runOnMainThreadNowOrLater(BetterCaptionsOverlay::refresh);
    }

    /**
     * @return Estimated playback position in milliseconds, or -1 before anything plays.
     */
    public static long getEstimatedVideoTime() {
        if (reportedVideoTime < 0) return -1;

        // Anything but playing means the picture is standing still: paused, ended, or
        // waiting for the video to load. The captions stand still with it, rather than
        // running on and arriving ahead of what is being said.
        if (VideoState.getCurrent() != VideoState.PLAYING) return reportedVideoTime;

        final long elapsed = SystemClock.elapsedRealtime() - reportedAtRealTime;
        final long estimate = reportedVideoTime
                + (long) (elapsed * VideoInformation.getPlaybackSpeed());

        final long videoLength = VideoInformation.getVideoLength();
        return videoLength > 0 ? Math.min(estimate, videoLength) : estimate;
    }

    private static void refresh() {
        FrameLayout overlay = overlayRef.get();
        CaptionLineView spoken = spokenLineRef.get();
        CaptionLineView second = secondLineRef.get();
        if (overlay == null || spoken == null || second == null) return;

        ViewGroup player = (ViewGroup) overlay.getParent();

        final boolean active = BetterCaptionsSettings.ENABLED.get()
                && areCaptionsOn()
                && PlayerType.getCurrent().isMaximizedOrFullscreen()
                && reportedVideoTime >= 0;

        if (!active) {
            overlay.setVisibility(View.GONE);
            showOriginalCaptions();
            return;
        }


        overlay.setVisibility(View.VISIBLE);

        final String videoId = VideoInformation.getVideoId();
        if (!videoId.isEmpty()) CaptionLines.ensureLoaded(videoId);

        final long time = getEstimatedVideoTime();
        final String spokenText = CaptionLines.upperLineAt(time);
        final String secondText = CaptionLines.lowerLineAt(time);

        reportChanges(spokenText, secondText);

        show(spoken, spokenText,
                BetterCaptionsSettings.TEXT_SIZE.get(),
                BetterCaptionsSettings.COLOR.get());
        show(second, secondText,
                BetterCaptionsSettings.SECOND_TEXT_SIZE.get(),
                BetterCaptionsSettings.SECOND_COLOR.get());

    }

    private static void show(CaptionLineView line, @Nullable String text,
                             int textSizeSp, int textColor) {
        if (text == null) {
            line.setVisibility(View.GONE);
            return;
        }

        line.setVisibility(View.VISIBLE);
        line.applyStyle(textSizeSp, textColor, BetterCaptionsSettings.BACKGROUND_OPACITY.get());
        if (!text.contentEquals(line.getText())) {
            line.setText(text);
        }
    }

    /**
     * Both lines come from one block, so a pass reporting only one of them changing is
     * a fault worth seeing.
     */
    private static void reportChanges(@Nullable String spokenText, @Nullable String secondText) {
        final boolean spokenChanged = !Objects.equals(spokenText, lastSpokenText);
        final boolean secondChanged = !Objects.equals(secondText, lastSecondText);
        lastSpokenText = spokenText;
        lastSecondText = secondText;

        if (spokenChanged || secondChanged) {
            Logger.printDebug(() -> "lines at " + getEstimatedVideoTime()
                    + ": [" + spokenText + "] [" + secondText + "]");
        }
    }

    /**
     * Puts each line in the room kept for it.
     *
     * Lines stack against their edge, so one never floats in the middle because the row
     * beside it is empty, and two sharing an edge sit one above the other rather than
     * over each other.
     */
    private static void positionLines(FrameLayout overlay,
                                      CaptionLineView spoken, CaptionLineView second) {
        if (overlay.getVisibility() != View.VISIBLE) return;

        final int margin = Dim.dp(CaptionLayout.EDGE_MARGIN_DIP);

        for (boolean top : new boolean[]{true, false}) {
            CaptionLineView[] rows = rowsOn(top, spoken, second);
            if (rows == null) continue;

            final int height = stack(rows);
            final int stackTop = top ? margin : overlay.getHeight() - margin - height;
            place(overlay, rows, stackTop);
        }
    }

    /**
     * The lines belonging to one edge, nearest the edge first, or null when that edge
     * holds none.
     */
    @Nullable
    private static CaptionLineView[] rowsOn(boolean top,
                                            CaptionLineView spoken, CaptionLineView second) {
        CaptionSlot spokenSlot = BetterCaptionsSettings.SLOT.get();
        CaptionSlot secondSlot = CaptionLayout.secondSlot(spokenSlot);

        CaptionLineView firstRow = null;
        CaptionLineView secondRow = null;

        if (spokenSlot.isTop() == top) {
            if (spokenSlot.isFirstRow()) firstRow = spoken;
            else secondRow = spoken;
        }
        // A second language that was never chosen never appears, so it keeps no room.
        if (secondSlot.isTop() == top && !BetterCaptionsSettings.LANGUAGE.get().isEmpty()) {
            if (secondSlot.isFirstRow()) firstRow = second;
            else secondRow = second;
        }

        if (firstRow == null && secondRow == null) return null;
        return new CaptionLineView[]{firstRow, secondRow};
    }

    /**
     * How much of the player one edge's lines take, counting the room kept for a second
     * line of text that a caption may or may not need.
     */
    private static int band(@Nullable CaptionLineView[] rows) {
        if (rows == null) return 0;

        final int margin = Dim.dp(CaptionLayout.EDGE_MARGIN_DIP);
        return margin + stack(rows) + margin;
    }

    private static int stack(CaptionLineView[] rows) {
        final int firstHeight = rows[0] == null ? 0 : slotHeight(rows[0]);
        final int secondHeight = rows[1] == null ? 0 : slotHeight(rows[1]);
        final int gap = rows[0] != null && rows[1] != null ? Dim.dp(CaptionLayout.LINE_GAP_DIP) : 0;
        return firstHeight + gap + secondHeight;
    }

    /**
     * The room one caption keeps, whatever it happens to say right now.
     */
    private static int slotHeight(CaptionLineView line) {
        // The same arithmetic the watch page was answered with, so what was asked for
        // and what is drawn agree.
        return CaptionLayout.slotHeight(line.textSizeSp());
    }

    /**
     * Centres each line in the room kept for it, so one line of text sits where the
     * middle of two would be rather than against the top of the space.
     */
    private static void place(FrameLayout overlay, CaptionLineView[] rows, int stackTop) {
        final int firstHeight = rows[0] == null ? 0 : slotHeight(rows[0]);
        final int gap = rows[0] != null && rows[1] != null ? Dim.dp(CaptionLayout.LINE_GAP_DIP) : 0;

        if (rows[0] != null) centreIn(overlay, rows[0], stackTop, firstHeight);
        if (rows[1] != null) {
            centreIn(overlay, rows[1], stackTop + firstHeight + gap, slotHeight(rows[1]));
        }
    }

    private static void centreIn(FrameLayout overlay, CaptionLineView line,
                                 int slotTop, int slotHeight) {
        centre(overlay, line);
        line.setY(slotTop + (slotHeight - line.getHeight()) / 2f);
    }

    private static void centre(FrameLayout overlay, CaptionLineView line) {
        line.setX(Math.max(0, (overlay.getWidth() - line.getWidth()) / 2f));
    }

    /**
     * Keeps YouTube's captions out of the way while this draws its own, and hands them
     * back the moment it stops.
     */
    /**
     * While the patch is on, the app's own captions are never on screen: these lines are
     * the captions, and turning them off means none rather than the app's back again in
     * their place.
     */
    private static void keepOriginalCaptionsAway(FrameLayout overlay) {
        if (BetterCaptionsSettings.ENABLED.get()) {
            hideOriginalCaptions(overlay);
        } else {
            showOriginalCaptions();
        }
    }

    /**
     * Hides YouTube's own captions while this draws them itself, so the spoken line does
     * not appear twice, once in the app's place and once in the chosen one.
     *
     * The view keeps its class name through obfuscation, being part of a library the app
     * links rather than of the app itself.
     */
    private static void hideOriginalCaptions(View overlay) {
        setOriginalCaptionsVisible(false);
    }

    private static void showOriginalCaptions() {
        setOriginalCaptionsVisible(true);
    }

    private static void setOriginalCaptionsVisible(boolean visible) {
        final int wanted = visible ? View.VISIBLE : View.GONE;

        for (Iterator<WeakReference<View>> known = originalCaptions.iterator(); known.hasNext(); ) {
            View view = known.next().get();
            if (view == null) {
                known.remove();
            } else if (view.getVisibility() != wanted) {
                view.setVisibility(wanted);
            }
        }
    }

    private BetterCaptionsOverlay() {
    }
}
