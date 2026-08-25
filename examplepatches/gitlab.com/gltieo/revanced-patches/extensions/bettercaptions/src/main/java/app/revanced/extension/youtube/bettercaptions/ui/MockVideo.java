package app.revanced.extension.youtube.bettercaptions.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A picture to stand in for the video while captions are being arranged over it.
 *
 * It is a landscape at dusk rather than a flat rectangle: a caption has to be read
 * against a picture, and a picture is where the trouble is. The sky is bright behind the
 * upper caption and the hills are dark behind the lower one, so a colour or a backdrop
 * that only works against one of them shows itself here rather than on the first video
 * it is tried on.
 *
 * It is drawn rather than carried as an image so it costs nothing to ship and comes out
 * sharp at whatever size the preview happens to be.
 */
final class MockVideo extends Drawable {

    private static final int SKY_TOP = 0xFF1B2A4A;
    private static final int SKY_MIDDLE = 0xFF9C6B8E;
    private static final int SKY_HORIZON = 0xFFF2A65A;

    private static final int SUN = 0xFFFFD9A0;
    private static final int HILLS_FAR = 0xFF3E4A6B;
    private static final int HILLS_NEAR = 0xFF1E2437;
    private static final int WATER = 0xFF2A3556;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cornerRadius;

    MockVideo(float cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        final float width = bounds.width();
        final float height = bounds.height();
        if (width <= 0 || height <= 0) return;

        final int saved = canvas.save();
        path.reset();
        path.addRoundRect(new RectF(bounds), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(path);

        // The horizon sits high enough that the lower caption falls on the dark ground.
        final float horizon = bounds.top + height * 0.66f;

        paint.setShader(new LinearGradient(0, bounds.top, 0, horizon,
                new int[]{SKY_TOP, SKY_MIDDLE, SKY_HORIZON},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds.left, bounds.top, bounds.right, horizon, paint);
        paint.setShader(null);

        paint.setColor(SUN);
        canvas.drawCircle(bounds.left + width * 0.7f, horizon - height * 0.1f, height * 0.09f, paint);

        paint.setColor(WATER);
        canvas.drawRect(bounds.left, horizon, bounds.right, bounds.bottom, paint);

        // Two ridges, the nearer one darker, to give the picture some depth.
        ridge(canvas, bounds.left, bounds.right, horizon, height * 0.20f, 0.30f, HILLS_FAR);
        ridge(canvas, bounds.left, bounds.right, horizon + height * 0.06f, height * 0.26f, 0.66f,
                HILLS_NEAR);

        canvas.restoreToCount(saved);
    }

    /**
     * A hill line: a single rise, peaking at the given fraction across, filled down to
     * the bottom of the picture.
     */
    private void ridge(Canvas canvas, float left, float right, float baseline, float rise,
                       float peakFraction, int colour) {
        final float width = right - left;
        final float peakX = left + width * peakFraction;

        path.reset();
        path.moveTo(left, baseline);
        path.cubicTo(peakX - width * 0.22f, baseline,
                peakX - width * 0.12f, baseline - rise,
                peakX, baseline - rise * 0.92f);
        path.cubicTo(peakX + width * 0.18f, baseline - rise * 0.78f,
                peakX + width * 0.3f, baseline - rise * 0.1f,
                right, baseline - rise * 0.25f);
        path.lineTo(right, getBounds().bottom);
        path.lineTo(left, getBounds().bottom);
        path.close();

        paint.setColor(colour);
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
