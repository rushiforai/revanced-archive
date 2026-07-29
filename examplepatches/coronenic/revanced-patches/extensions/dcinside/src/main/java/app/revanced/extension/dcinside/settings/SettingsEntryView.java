package app.revanced.extension.dcinside.settings;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * The "ReVanced 패치 버전" row the resource patch inserts into {@code res/layout/fragment_settings.xml}.
 *
 * Inflated by class name, so the row owns its own click and opens {@link SettingsActivity} — no hook
 * into the app's (obfuscated) settings fragment is needed.
 */
public final class SettingsEntryView extends LinearLayout implements View.OnClickListener {

    public SettingsEntryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Context context = getContext();
        try {
            Intent intent = new Intent(context, SettingsActivity.class);
            Ui.putPalette(context, intent);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }
}
