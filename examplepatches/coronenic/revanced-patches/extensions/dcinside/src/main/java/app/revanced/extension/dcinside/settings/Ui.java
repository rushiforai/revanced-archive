package app.revanced.extension.dcinside.settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;

/**
 * Theme and metric helpers.
 *
 * The settings page builds its views in code, so it has to read the colors it paints with off the
 * theme. The app picks one of ~30 skin themes ({@code AppTheme.ColorN}) at runtime and there is no
 * public API to learn which one an activity ended up with, so the row that opens the page resolves
 * the colors in its own (correctly themed) context and passes them along explicitly.
 */
final class Ui {
    private Ui() {}

    static final String EXTRA_BACKGROUND = "revanced.settings.background";
    static final String EXTRA_PRIMARY = "revanced.settings.primary";
    static final String EXTRA_TEXT = "revanced.settings.text";
    static final String EXTRA_TEXT_SUB = "revanced.settings.textSub";

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Resolve a color attribute against {@code context}'s theme. */
    static int themeColor(Context context, int attr, int fallback) {
        if (attr == 0) return fallback;
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, fallback);
        } catch (Throwable t) {
            return fallback;
        } finally {
            a.recycle();
        }
    }

    static Drawable themeDrawable(Context context, int attr) {
        if (attr == 0) return null;
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getDrawable(0);
        } catch (Throwable t) {
            return null;
        } finally {
            a.recycle();
        }
    }

    /** The app's own attributes and drawables are not in {@code android.R}: resolve them by name. */
    static int attrId(Context context, String name) {
        return context.getResources().getIdentifier(name, "attr", context.getPackageName());
    }

    static int drawableId(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    static int background(Context context) {
        return themeColor(context, attrId(context, "windowBackgroundColor"),
                themeColor(context, android.R.attr.colorBackground, 0xFFFFFFFF));
    }

    static int primary(Context context) {
        return themeColor(context, attrId(context, "colorPrimary"), 0xFF3E8CE8);
    }

    static int text(Context context) {
        return themeColor(context, android.R.attr.textColorPrimary, 0xFF262626);
    }

    static int textSub(Context context) {
        return themeColor(context, android.R.attr.textColorSecondary, 0xFF888888);
    }

    /** Carry the caller's skin colors to the settings page, which has no way to look them up. */
    static void putPalette(Context context, Intent intent) {
        intent.putExtra(EXTRA_BACKGROUND, background(context));
        intent.putExtra(EXTRA_PRIMARY, primary(context));
        intent.putExtra(EXTRA_TEXT, text(context));
        intent.putExtra(EXTRA_TEXT_SUB, textSub(context));
    }
}
