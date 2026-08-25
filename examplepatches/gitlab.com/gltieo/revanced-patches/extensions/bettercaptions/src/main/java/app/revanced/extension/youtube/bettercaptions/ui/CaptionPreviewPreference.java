package app.revanced.extension.youtube.bettercaptions.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.settings.preference.ColorPickerView;
import app.revanced.extension.shared.ui.CustomDialog;
import app.revanced.extension.shared.ui.Dim;
import app.revanced.extension.youtube.bettercaptions.CaptionLayout;
import app.revanced.extension.youtube.bettercaptions.CaptionLineView;
import app.revanced.extension.youtube.bettercaptions.BetterCaptionsSettings;
import app.revanced.extension.youtube.bettercaptions.CaptionSlot;

/**
 * The whole of how captions look and where they sit, arranged on the thing itself.
 *
 * A stand-in player holds the two caption lines. A line is dragged to the place it
 * should keep: there are four, two rows along each edge, and a line snaps to the nearest
 * of them when let go. Dropping one where the other already is swaps the two, so they
 * can never end up in the same place.
 *
 * Touching a line picks it, and the controls underneath then shape that line. They are
 * the same views the player uses and they are styled from the same settings, so what is
 * arranged here is literally what appears over a video, which is why none of it needs
 * explaining in words.
 */
@SuppressWarnings("unused")
public class CaptionPreviewPreference extends Preference {

    static {
        // The settings screen builds this as it inflates, which is the moment this
        // patch's settings have to exist on the list the screen reads.
        BetterCaptionsSettings.load();
    }

    private static final float ASPECT_WIDTH = 16f;
    private static final float ASPECT_HEIGHT = 9f;

    private static final int EDGE_MARGIN_DIP = CaptionLayout.EDGE_MARGIN_DIP;
    private static final int LINE_GAP_DIP = CaptionLayout.LINE_GAP_DIP;

    private static final int MINIMUM_TEXT_SIZE_SP = 10;
    private static final int MAXIMUM_TEXT_SIZE_SP = 32;

    private static final int SWATCH_SIZE_DIP = 30;
    private static final int SWATCH_GAP_DIP = 10;
    private static final int PICKER_HEIGHT_DIP = 240;

    private static final int SELECTION_COLOR = 0xFF4C8DFF;

    /**
     * Colours to choose from: the plain one first, then those the eye separates from a
     * picture and from each other while staying readable on any of it.
     */
    private static final int[] COLOURS = {
            0xFFFFFFFF, 0xFFFFE082, 0xFF80DEEA, 0xFFA5D6A7, 0xFFF48FB1, 0xFFCE93D8,
    };

    private CaptionLineView spokenLine;
    private CaptionLineView secondLine;
    private View video;

    /**
     * Which line the controls shape. The spoken line to begin with, being the one that
     * is always there.
     */
    private boolean spokenSelected = true;

    private SeekBar sizeBar;
    private LinearLayout swatchRow;

    public CaptionPreviewPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CaptionPreviewPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CaptionPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptionPreviewPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        try {
            Context context = getContext();

            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(Dim.dp16, Dim.dp8, Dim.dp16, Dim.dp16);
            column.addView(player(context));
            column.addView(controls(context));

            select(true);
            return column;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not build the caption preview", ex);
            return new View(getContext());
        }
    }

    // The stand-in player.

    private View player(Context context) {
        FrameLayout player = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                final int width = MeasureSpec.getSize(widthSpec);
                final int height = Math.round(width * ASPECT_HEIGHT / ASPECT_WIDTH);
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        };

        GradientDrawable letterbox = new GradientDrawable();
        letterbox.setColor(Color.BLACK);
        letterbox.setCornerRadius(Dim.dp8);
        player.setBackground(letterbox);
        player.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        video = new View(context);
        video.setBackground(new MockVideo(Dim.dp4));
        player.addView(video, new FrameLayout.LayoutParams(0, 0));

        spokenLine = line(context, str("revanced_better_captions_preview_first"), true);
        secondLine = line(context, str("revanced_better_captions_preview_second"), false);
        player.addView(spokenLine);
        player.addView(secondLine);

        // Everything is settled between layout and drawing, so the lines are never
        // painted where they were before a drag or a size change moved them.
        player.getViewTreeObserver().addOnPreDrawListener(() -> {
            layOut(player);
            return true;
        });
        return player;
    }

    private CaptionLineView line(Context context, String text, boolean spoken) {
        CaptionLineView view = new CaptionLineView(context);
        view.setText(text);
        view.setDraggable(true, fractionY -> dropped(spoken, fractionY));
        view.setOnTapped(() -> select(spoken));
        style(view, spoken);
        return view;
    }

    private void style(CaptionLineView line, boolean spoken) {
        line.applyStyle(
                spoken ? BetterCaptionsSettings.TEXT_SIZE.get()
                        : BetterCaptionsSettings.SECOND_TEXT_SIZE.get(),
                spoken ? BetterCaptionsSettings.COLOR.get()
                        : BetterCaptionsSettings.SECOND_COLOR.get(),
                BetterCaptionsSettings.BACKGROUND_OPACITY.get());
        line.setSelectionShown(spoken == spokenSelected);
    }

    private void restyle() {
        style(spokenLine, true);
        style(secondLine, false);
    }

    /**
     * Moves a line to the slot it was dropped nearest.
     *
     * Dropping one where the other already is means one of two things. From the other
     * edge, it means "put me here too": the two then stack against this edge, the newcomer
     * taking the row that is free. From the same edge, there is nowhere else to mean, so
     * the two change places.
     */
    private void dropped(boolean spoken, float fractionY) {
        final boolean top = fractionY < 0.5f;
        // Within an edge, the half nearer the edge is the first row.
        final boolean firstRow = top ? fractionY < 0.25f : fractionY < 0.75f;
        final CaptionSlot chosen = CaptionSlot.of(top, firstRow);

        final CaptionSlot mine = spoken
                ? BetterCaptionsSettings.SLOT.get()
                : BetterCaptionsSettings.SECOND_SLOT.get();
        final CaptionSlot theirs = spoken
                ? BetterCaptionsSettings.SECOND_SLOT.get()
                : BetterCaptionsSettings.SLOT.get();

        CaptionSlot wanted = chosen;
        if (chosen == theirs) {
            if (mine.isTop() == chosen.isTop()) {
                // Both already at this edge: swap, which is the only move left.
                if (spoken) BetterCaptionsSettings.SECOND_SLOT.save(mine);
                else BetterCaptionsSettings.SLOT.save(mine);
            } else {
                // Coming from the other edge: join it rather than push it away.
                wanted = chosen.otherRow();
            }
        }

        if (spoken) BetterCaptionsSettings.SLOT.save(wanted);
        else BetterCaptionsSettings.SECOND_SLOT.save(wanted);

        select(spoken);
    }

    private void select(boolean spoken) {
        spokenSelected = spoken;
        if (spokenLine != null) spokenLine.setSelectionShown(spoken);
        if (secondLine != null) secondLine.setSelectionShown(!spoken);

        if (sizeBar != null) sizeBar.setProgress(selectedSize() - MINIMUM_TEXT_SIZE_SP);
        markSelectedSwatch();
    }

    private int selectedSize() {
        return spokenSelected
                ? BetterCaptionsSettings.TEXT_SIZE.get()
                : BetterCaptionsSettings.SECOND_TEXT_SIZE.get();
    }

    private int selectedColour() {
        return spokenSelected
                ? BetterCaptionsSettings.COLOR.get()
                : BetterCaptionsSettings.SECOND_COLOR.get();
    }

    // The controls, all of which shape the line that is picked.

    private View controls(Context context) {
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, Dim.dp16, 0, 0);

        // The size and the colour shape whichever line is picked; what follows the rule
        // belongs to both of them, and sitting right under the colours it read as one
        // more thing about the picked line.
        controls.addView(sizeRow(context));
        controls.addView(swatchRow(context));
        controls.addView(rule(context));
        controls.addView(opacityRow(context));
        return controls;
    }

    /**
     * The size of the picked line, between a small letter and a large one so the ends of
     * the slider say what it does.
     */
    private View sizeRow(Context context) {
        sizeBar = new SeekBar(context);
        sizeBar.setMax(MAXIMUM_TEXT_SIZE_SP - MINIMUM_TEXT_SIZE_SP);
        sizeBar.setProgress(selectedSize() - MINIMUM_TEXT_SIZE_SP);
        sizeBar.setOnSeekBarChangeListener(new Changed(progress -> {
            final int size = MINIMUM_TEXT_SIZE_SP + progress;
            if (spokenSelected) BetterCaptionsSettings.TEXT_SIZE.save(size);
            else BetterCaptionsSettings.SECOND_TEXT_SIZE.save(size);
            restyle();
        }));

        return row(context, letter(context, 12), sizeBar, letter(context, 20));
    }

    private TextView letter(Context context, int sizeSp) {
        TextView letter = new TextView(context);
        letter.setText("A");
        letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        letter.setGravity(Gravity.CENTER);
        return letter;
    }

    /**
     * The colour of the picked line.
     */
    private View swatchRow(Context context) {
        swatchRow = new LinearLayout(context);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER);
        swatchRow.setPadding(0, Dim.dp12, 0, 0);

        for (final int colour : COLOURS) {
            swatchRow.addView(swatch(context, view -> chose(colour)));
        }
        // The last spot is whatever the six are not.
        swatchRow.addView(swatch(context, view -> askForColour(context)));

        markSelectedSwatch();
        return swatchRow;
    }

    private View swatch(Context context, View.OnClickListener onClick) {
        View swatch = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Dim.dp(SWATCH_SIZE_DIP), Dim.dp(SWATCH_SIZE_DIP));
        params.setMargins(Dim.dp(SWATCH_GAP_DIP) / 2, 0, Dim.dp(SWATCH_GAP_DIP) / 2, 0);
        swatch.setLayoutParams(params);
        swatch.setOnClickListener(onClick);
        return swatch;
    }

    private void chose(int colour) {
        if (spokenSelected) BetterCaptionsSettings.COLOR.save(colour);
        else BetterCaptionsSettings.SECOND_COLOR.save(colour);
        restyle();
        markSelectedSwatch();
    }

    /**
     * Opens the colour wheel for a colour the six spots do not offer. The picked line
     * follows the finger around the wheel, so the choice is made against the picture
     * rather than against a number.
     */
    private void askForColour(Context context) {
        final int before = selectedColour();

        ColorPickerView picker = new ColorPickerView(context);
        picker.setOpacitySliderEnabled(false);
        picker.setColor(before);
        picker.setOnColorChangedListener(colour -> chose(colour | 0xFF000000));

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(Dim.dp16, Dim.dp16, Dim.dp16, 0);
        holder.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Dim.dp(PICKER_HEIGHT_DIP)));

        Pair<Dialog, LinearLayout> dialog = CustomDialog.create(
                context,
                str("revanced_better_captions_color_title"),
                null, null, null,
                () -> {
                },
                () -> chose(before),
                null, null, false);
        dialog.second.addView(holder, dialog.second.getChildCount() - 1);
        dialog.first.show();
    }

    /**
     * Rings the swatch the picked line is wearing, so the row shows the current colour
     * as well as offering the others.
     */
    private void markSelectedSwatch() {
        if (swatchRow == null) return;
        final int current = selectedColour();

        for (int index = 0; index < swatchRow.getChildCount(); index++) {
            // The last spot wears whatever colour is set but is not one of the six, and
            // otherwise shows the wheel it opens.
            final boolean custom = index == COLOURS.length;
            final int colour = custom ? current : COLOURS[index];
            if (custom && isOneOfTheSix(current)) {
                swatchRow.getChildAt(index).setBackground(wheel());
                continue;
            }

            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            fill.setColor(colour);
            fill.setStroke(Dim.dp1, 0x30000000);

            if (colour != current) {
                swatchRow.getChildAt(index).setBackground(fill);
                continue;
            }

            // A ring around the colour rather than on it, so white reads as picked on a
            // white page and the colour itself is still shown whole.
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(Dim.dp2, SELECTION_COLOR);

            LayerDrawable picked = new LayerDrawable(new android.graphics.drawable.Drawable[]{ring, fill});
            picked.setLayerInset(1, Dim.dp4, Dim.dp4, Dim.dp4, Dim.dp4);
            swatchRow.getChildAt(index).setBackground(picked);
        }
    }

    private static boolean isOneOfTheSix(int colour) {
        for (int candidate : COLOURS) {
            if (candidate == colour) return true;
        }
        return false;
    }

    /**
     * The spot that opens the wheel, drawn as one.
     */
    private static GradientDrawable wheel() {
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF});
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(Dim.dp1, 0x30000000);
        return circle;
    }

    /**
     * How opaque the box behind the text is, from none to solid. Shared by both lines,
     * which read as one block when they sit together.
     */
    private View opacityRow(Context context) {
        SeekBar opacity = new SeekBar(context);
        opacity.setMax(100);
        opacity.setProgress(BetterCaptionsSettings.BACKGROUND_OPACITY.get());
        opacity.setOnSeekBarChangeListener(new Changed(progress -> {
            BetterCaptionsSettings.BACKGROUND_OPACITY.save(progress);
            restyle();
        }));

        return row(context, box(context, 0), opacity, box(context, 255));
    }

    /**
     * A hairline between what shapes the picked line and what shapes both.
     */
    private View rule(Context context) {
        View line = new View(context);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dim.dp1);
        params.setMargins(0, Dim.dp16, 0, Dim.dp16);
        line.setLayoutParams(params);
        line.setBackgroundColor(0x22808080);
        return line;
    }

    private View box(Context context, int alpha) {
        View chip = new View(context);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.argb(alpha, 0, 0, 0));
        fill.setCornerRadius(Dim.dp4);
        fill.setStroke(Dim.dp1, 0x40808080);
        chip.setBackground(fill);
        chip.setLayoutParams(new LinearLayout.LayoutParams(Dim.dp20, Dim.dp16));
        return chip;
    }

    private String str(String key) {
        Context context = getContext();
        final int id = context.getResources().getIdentifier(key, "string", context.getPackageName());
        return id == 0 ? "" : context.getString(id);
    }

    private View row(Context context, View start, View middle, View end) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(start);
        LinearLayout.LayoutParams stretch = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stretch.setMargins(Dim.dp8, 0, Dim.dp8, 0);
        row.addView(middle, stretch);
        row.addView(end);
        return row;
    }

    /**
     * Reports a slider's value while it is being moved, so the preview follows the
     * finger rather than waiting for it to be lifted.
     */
    private static final class Changed implements SeekBar.OnSeekBarChangeListener {
        interface Value {
            void set(int progress);
        }

        private final Value value;

        Changed(Value value) {
            this.value = value;
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (fromUser) value.set(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar bar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
        }
    }

    // Where everything sits.

    /**
     * Lays the lines against their edges, the way the player does.
     */
    private void layOut(FrameLayout player) {
        if (spokenLine == null || secondLine == null) return;
        if (spokenLine.isBeingDragged() || secondLine.isBeingDragged()) return;

        CaptionSlot spokenSlot = BetterCaptionsSettings.SLOT.get();
        CaptionSlot secondSlot = BetterCaptionsSettings.SECOND_SLOT.get();
        if (spokenSlot == secondSlot) secondSlot = secondSlot.otherRow();

        stack(player, true, spokenSlot, secondSlot);
        stack(player, false, spokenSlot, secondSlot);

        layOutVideo(player, 0, 0);
    }

    /**
     * Puts the lines belonging to one edge against it, and returns how much of the
     * player they take up.
     */
    private int stack(FrameLayout player, boolean top,
                      CaptionSlot spokenSlot, CaptionSlot secondSlot) {
        CaptionLineView firstRow = null;
        CaptionLineView secondRow = null;

        if (spokenSlot.isTop() == top) {
            if (spokenSlot.isFirstRow()) firstRow = spokenLine;
            else secondRow = spokenLine;
        }
        if (secondSlot.isTop() == top) {
            if (secondSlot.isFirstRow()) firstRow = secondLine;
            else secondRow = secondLine;
        }
        if (firstRow == null && secondRow == null) return 0;

        final int margin = Dim.dp(EDGE_MARGIN_DIP);
        final int gap = Dim.dp(LINE_GAP_DIP);

        final int firstHeight = firstRow == null ? 0 : slotHeight(firstRow);
        final int secondHeight = secondRow == null ? 0 : slotHeight(secondRow);
        final boolean both = firstRow != null && secondRow != null;
        final int stack = firstHeight + secondHeight + (both ? gap : 0);

        final int stackTop = top ? margin : player.getHeight() - margin - stack;

        if (firstRow != null) place(player, firstRow, stackTop, firstHeight);
        if (secondRow != null) {
            place(player, secondRow, stackTop + firstHeight + (both ? gap : 0), secondHeight);
        }
        return margin + stack + margin;
    }

    private int slotHeight(CaptionLineView line) {
        return CaptionLayout.slotHeight(line.textSizeSp());
    }

    private void place(FrameLayout player, CaptionLineView line, int slotTop, int slotHeight) {
        centre(player, line);
        line.setY(slotTop + (slotHeight - line.getHeight()) / 2f);
    }

    /**
     * Fits the picture between the bands the captions take, keeping its shape and its
     * place in the middle of what is left.
     */
    private void layOutVideo(FrameLayout player, int topBand, int bottomBand) {
        if (video == null) return;

        final int playerWidth = player.getWidth();
        final int playerHeight = player.getHeight();
        if (playerWidth == 0 || playerHeight == 0) return;

        final int free = Math.max(1, playerHeight - topBand - bottomBand);
        int height = Math.min(playerHeight, free);
        int width = Math.round(height * ASPECT_WIDTH / ASPECT_HEIGHT);
        if (width > playerWidth) {
            width = playerWidth;
            height = Math.round(width * ASPECT_HEIGHT / ASPECT_WIDTH);
        }

        final int left = (playerWidth - width) / 2;
        final int top = topBand + (free - height) / 2;

        ViewGroup.LayoutParams params = video.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            video.setLayoutParams(params);
        }
        video.setX(left);
        video.setY(top);
    }

    private void centre(FrameLayout player, CaptionLineView line) {
        line.setX(Math.max(0, (player.getWidth() - line.getWidth()) / 2f));
    }
}
