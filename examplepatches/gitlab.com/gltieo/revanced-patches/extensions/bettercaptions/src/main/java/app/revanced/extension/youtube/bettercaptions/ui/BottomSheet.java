package app.revanced.extension.youtube.bettercaptions.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.ui.Dim;

/**
 * A sheet that slides up from the bottom of the screen and is thrown back down.
 *
 * The app's own is built for an upright phone: it is as wide as the short side of the
 * screen, which in landscape leaves a column down the middle, and it only closes on a
 * drag that reaches the bottom. This one is as wide as there is room for, up to a
 * readable measure, and a flick downwards closes it the way a sheet is expected to.
 */
public class BottomSheet extends Dialog {

    /**
     * How wide the sheet is allowed to grow. Past this a line of text is too long to
     * read comfortably, so the rest of the screen is left to the video.
     */
    private static final int WIDEST_DIP = 640;

    private static final int SLIDE_MILLISECONDS = 220;

    /**
     * A drag past this much of the sheet, or a flick faster than this, closes it.
     */
    private static final float DISMISS_FRACTION = 0.25f;
    private static final float DISMISS_VELOCITY = 900f;

    private final DragPanel panel;
    private final int touchSlop;
    private VelocityTracker velocity;

    private float touchStartY;
    private float panelStartY;
    private boolean dragging;
    private boolean closing;

    /**
     * What the sheet is holding, asked whether it can still be scrolled up: a drag that
     * starts inside content that has more above it scrolls that instead of moving the
     * sheet.
     */
    public interface Content {
        boolean isScrolledToTop();
    }

    private Content content;

    public BottomSheet(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);

        panel = new DragPanel(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(background());
        panel.setClickable(true);
        panel.addView(handle(context));

        FrameLayout root = new FrameLayout(context);
        root.addView(panel, new FrameLayout.LayoutParams(
                widthOfSheet(), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        root.setOnClickListener(view -> dismiss());

        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0.5f);
            // The player is shown without the system bars; a sheet over it keeps them
            // away rather than bringing them back.
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    }

    /**
     * @param view The sheet's content, which is put under the handle.
     */
    public void setSheetContent(View view) {
        panel.addView(view);
        if (view instanceof Content) content = (Content) view;
    }

    private int widthOfSheet() {
        final int widest = Dim.dp(WIDEST_DIP);
        return Math.min(Dim.SCREEN_WIDTH, widest);
    }

    private ShapeDrawable background() {
        final float radius = Dim.dp(16);
        ShapeDrawable shape = new ShapeDrawable(new RoundRectShape(
                new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, null, null));
        shape.getPaint().setColor(Utils.getDialogBackgroundColor());
        return shape;
    }

    private View handle(Context context) {
        LinearLayout holder = new LinearLayout(context);
        holder.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.setPadding(0, Dim.dp8, 0, Dim.dp4);

        View bar = new View(context);
        ShapeDrawable shape = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(4), null, null));
        shape.getPaint().setColor(Utils.adjustColorBrightness(
                Utils.getDialogBackgroundColor(), 0.9f, 1.25f));
        bar.setBackground(shape);
        holder.addView(bar, new LinearLayout.LayoutParams(Dim.dp40, Dim.dp4));
        return holder;
    }

    @Override
    public void show() {
        super.show();

        panel.post(() -> {
            panel.setTranslationY(panel.getHeight());
            panel.animate()
                    .translationY(0)
                    .setDuration(SLIDE_MILLISECONDS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    @Override
    public void dismiss() {
        if (closing) return;
        closing = true;

        panel.animate()
                .translationY(panel.getHeight())
                .setDuration(SLIDE_MILLISECONDS)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        try {
                            BottomSheet.super.dismiss();
                        } catch (Exception ex) {
                            Logger.printDebug(() -> "Could not close the sheet: " + ex);
                        }
                    }
                })
                .start();
    }

    /**
     * The sheet itself, which takes a drag downwards from the content inside it: the
     * content scrolls until it is at its top, and a pull from there moves the sheet.
     */
    private final class DragPanel extends LinearLayout {

        DragPanel(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handleDrag(event);
                    return false;

                case MotionEvent.ACTION_MOVE: {
                    final float moved = event.getRawY() - touchStartY;
                    if (moved <= touchSlop) return false;
                    return content == null || content.isScrolledToTop();
                }

                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            handleDrag(event);
            return true;
        }
    }

    boolean handleDrag(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = event.getRawY();
                panelStartY = panel.getTranslationY();
                dragging = false;
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
                return false;

            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(event);

                final float moved = event.getRawY() - touchStartY;
                if (!dragging) {
                    if (moved < touchSlop) return false;
                    // Content that can still be scrolled up takes the drag itself.
                    if (content != null && !content.isScrolledToTop()) return false;
                    dragging = true;
                }

                panel.setTranslationY(Math.max(0, panelStartY + moved));
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) {
                    release();
                    return false;
                }
                dragging = false;

                float thrown = 0;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    thrown = velocity.getYVelocity();
                }
                release();

                if (thrown > DISMISS_VELOCITY
                        || panel.getTranslationY() > panel.getHeight() * DISMISS_FRACTION) {
                    dismiss();
                } else {
                    panel.animate()
                            .translationY(0)
                            .setDuration(SLIDE_MILLISECONDS)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                return true;
            }

            default:
                return false;
        }
    }

    private void release() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }
}
