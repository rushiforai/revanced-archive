package app.revanced.extension.youtube.bettercaptions;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.ResourceType;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.ui.Dim;

/**
 * One line of captions: the text, its backdrop, and the dragging.
 *
 * The same view is used in the player and in the settings preview, so what is arranged
 * in the settings is literally the thing that appears over the video.
 *
 * Dragging is deliberately not always live. The lines sit over the video, and a view
 * that swallows touches would break tapping the video to bring up the controls, so a
 * line only becomes draggable once it is told to be, which the player does while its
 * controls are on screen and the preview does permanently.
 */
public final class CaptionLineView extends TextView {

    /**
     * Told where the line was dropped, in fractions of its parent.
     */
    public interface OnMoved {
        void moved(float fractionY);
    }

    /**
     * Told that the line was touched without being moved.
     */
    public interface OnTapped {
        void tapped();
    }

    /**
     * Told which word of the caption was tapped, so that it can be looked up.
     */
    public interface OnWordTapped {
        void tapped(String word);
    }

    /**
     * How many lines a caption may take before it is shrunk to fit rather than growing
     * further.
     */
    private static final int LINES_AT_MOST = 4;

    private static final int CORNER_RADIUS_DIP = 4;
    private static final int PADDING_HORIZONTAL_DIP = 8;
    static final int PADDING_VERTICAL_DIP = 3;
    private static final int SELECTION_STROKE_DIP = 2;
    private static final int SELECTION_COLOR = 0xFF4C8DFF;

    private boolean draggable;
    private OnMoved onMoved;
    private OnTapped onTapped;
    private OnWordTapped onWordTapped;

    private int backdropOpacityPercent;
    private boolean selected;

    private float touchStartX;
    private float touchStartY;
    private int viewStartX;
    private int viewStartY;
    private boolean dragging;
    private final int touchSlop;

    public CaptionLineView(Context context) {
        super(context);
        touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();

        setGravity(Gravity.CENTER);
        setShadowLayer(3, 0, 0, Color.BLACK);
        // A caption keeps two lines and takes more only when it needs them, rather than
        // losing its end: cutting a word in half is worse than a line of text more.
        setMaxLines(LINES_AT_MOST);
        setPadding(
                dip(PADDING_HORIZONTAL_DIP), dip(PADDING_VERTICAL_DIP),
                dip(PADDING_HORIZONTAL_DIP), dip(PADDING_VERTICAL_DIP));
        setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * @param textSizeSp Size of the text.
     * @param textColor  Colour of the text.
     * @param backdropOpacityPercent How opaque the box behind the text is, 0 for none.
     */
    /**
     * The size this line is drawn at, which is also what its room was worked out from.
     */
    public int textSizeSp() {
        return textSizeSp;
    }

    private int textSizeSp;

    public void applyStyle(int textSizeSp, int textColor, int backdropOpacityPercent) {
        this.textSizeSp = textSizeSp;

        // Shrinks a sentence that would need a third line rather than cutting it off,
        // down to two thirds of the size asked for; below that it would be unreadable
        // and the end is dropped instead.
        setAutoSizeTextTypeUniformWithConfiguration(
                Math.max(8, textSizeSp * 2 / 3), textSizeSp, 1, TypedValue.COMPLEX_UNIT_SP);
        setTextColor(textColor);
        this.backdropOpacityPercent = backdropOpacityPercent;
        updateBackdrop();
    }

    /**
     * Rings the line, to say that the controls beside it are the ones that shape it.
     * Only the settings preview does this; over a video nothing is being chosen.
     */
    public void setSelectionShown(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        updateBackdrop();
    }

    private void updateBackdrop() {
        final int alpha = Math.round(Math.max(0, Math.min(100, backdropOpacityPercent)) * 2.55f);
        GradientDrawable backdrop = new GradientDrawable();
        backdrop.setColor(Color.argb(alpha, 0, 0, 0));
        backdrop.setCornerRadius(dip(CORNER_RADIUS_DIP));
        if (selected) backdrop.setStroke(dip(SELECTION_STROKE_DIP), SELECTION_COLOR);
        setBackground(backdrop);
    }

    public void setDraggable(boolean draggable, OnMoved onMoved) {
        this.draggable = draggable;
        this.onMoved = onMoved;
    }

    public void setOnTapped(OnTapped onTapped) {
        this.onTapped = onTapped;
    }

    /**
     * Turns dragging on or off, keeping whoever was told about moves.
     */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    /**
     * @return Whether a finger is moving this line right now, while which nothing else
     *         should decide where it sits.
     */
    public boolean isBeingDragged() {
        return dragging;
    }

    /**
     * Words are looked up when they are tapped, which is the only reason a line over the
     * player takes a touch at all; everything else there belongs to the player.
     */
    public void setOnWordTapped(OnWordTapped onWordTapped) {
        this.onWordTapped = onWordTapped;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!draggable) return onWordTapped == null ? false : wordTouch(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Inside a scrolling list the drag would otherwise be taken for a
                // scroll and the line would never move.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                viewStartX = (int) getX();
                viewStartY = (int) getY();
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                final float deltaX = event.getRawX() - touchStartX;
                final float deltaY = event.getRawY() - touchStartY;
                if (!dragging && Math.hypot(deltaX, deltaY) < touchSlop) return true;

                dragging = true;
                View parent = (View) getParent();
                if (parent == null) return true;

                final int x = clamp(Math.round(viewStartX + deltaX), 0,
                        Math.max(0, parent.getWidth() - getWidth()));
                final int y = clamp(Math.round(viewStartY + deltaY), 0,
                        Math.max(0, parent.getHeight() - getHeight()));
                setX(x);
                setY(y);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (dragging) {
                    reportPosition();
                } else if (onTapped != null) {
                    onTapped.tapped();
                }
                dragging = false;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * A tap on a word opens it; a touch that moves is the player's, since sliding across
     * a caption is how the video is seeked.
     */
    private boolean wordTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                return true;

            case MotionEvent.ACTION_UP: {
                final float moved = (float) Math.hypot(event.getRawX() - touchStartX,
                        event.getRawY() - touchStartY);
                if (moved > touchSlop) return false;

                final String word = wordAt(event.getX(), event.getY());
                if (word.isEmpty()) return false;

                onWordTapped.tapped(word);
                return true;
            }

            default:
                return true;
        }
    }


    /**
     * @return The word drawn under the given point, without the punctuation around it, or
     *         an empty string where there is none.
     */
    private String wordAt(float x, float y) {
        try {
            Layout layout = getLayout();
            CharSequence text = getText();
            if (layout == null || text == null || text.length() == 0) return "";

            final int line = layout.getLineForVertical((int) (y - getTotalPaddingTop()));
            final int offset = layout.getOffsetForHorizontal(line, x - getTotalPaddingLeft());
            if (offset < 0 || offset >= text.length()) return "";
            if (!isWordCharacter(text.charAt(offset))) return "";

            int start = offset;
            while (start > 0 && isWordCharacter(text.charAt(start - 1))) start--;
            int end = offset;
            while (end < text.length() - 1 && isWordCharacter(text.charAt(end + 1))) end++;

            return text.subSequence(start, end + 1).toString();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not read the word that was tapped", ex);
            return "";
        }
    }

    /**
     * Letters and what holds a word together: an apostrophe in "don't", a hyphen in
     * "so-called".
     */
    private static boolean isWordCharacter(char character) {
        return Character.isLetter(character) || character == '\'' || character == '\u2019'
                || character == '-';
    }

    private void reportPosition() {
        try {
            View parent = (View) getParent();
            if (parent == null || onMoved == null) return;

            onMoved.moved(CaptionLayout.fractionY(parent.getHeight(), getHeight(), (int) getY()));
        } catch (Exception ex) {
            Logger.printException(() -> "Could not store the caption position", ex);
        }
    }



    private static int dip(int value) {
        return Dim.dp(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
