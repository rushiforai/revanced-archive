package app.revanced.extension.youtube.vot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.view.ViewGroup;
import android.util.Log;
import java.lang.ref.WeakReference;

public final class VotButton {
    private static WeakReference<View> button = new WeakReference<>(null);
    private static final String TAG = "standalone-vot-control";

    private static int id(View view, String name) {
        return view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
    }
    public static void initializeButton(View controls) {
        try {
            if (!(controls instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) controls;
            View target = group.findViewWithTag(TAG);
            if (target != null) { button = new WeakReference<>(target); return; }
            int oldId = id(group, "revanced_vot_button");
            target = oldId == 0 ? null : group.findViewById(oldId);
            if (target == null) throw new IllegalStateException("VOT control resource missing");
            target.setTag(TAG);
            target.setOnClickListener(view -> VotController.toggle());
            target.setOnLongClickListener(view -> { settings(view.getContext()); return true; });
            target.setVisibility(View.INVISIBLE);
            button = new WeakReference<>(target);
        } catch (Exception ex) { Log.e("VOT", "Button initialization failed", ex); }
    }
    public static void setVisibilityNegatedImmediate() { setVisibilityImmediate(false); }
    public static void setVisibilityImmediate(boolean visible) {
        View view = button.get();
        if (view != null) { view.animate().cancel(); view.setAlpha(1); view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); }
    }
    public static void setVisibility(boolean visible, boolean animated) { setVisibilityImmediate(visible); }
    private static Spinner languages(Context context, LinearLayout layout, String title, String[] values, String selected) {
        TextView label = new TextView(context); label.setText(title); layout.addView(label);
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) spinner.setSelection(i);
        layout.addView(spinner); return spinner;
    }
    private static SeekBar volume(Context context, LinearLayout layout, String title, int value) {
        TextView label = new TextView(context); label.setText(title + ": " + value + "%"); layout.addView(label);
        SeekBar bar = new SeekBar(context); bar.setMax(100); bar.setProgress(value); layout.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean user) { label.setText(title + ": " + progress + "%"); }
            public void onStartTrackingTouch(SeekBar seek) { }
            public void onStopTrackingTouch(SeekBar seek) { }
        });
        return bar;
    }
    private static void settings(Context context) {
        SharedPreferences prefs = VotController.preferences();
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density); content.setPadding(padding, padding, padding, 0);
        TextView status = new TextView(context); status.setText(VotController.status); content.addView(status);
        Spinner from = languages(context, content, "Язык оригинала", new String[]{"en", "ru", "de", "fr", "es", "it", "zh", "ja", "ko"}, prefs.getString("from", "en"));
        Spinner to = languages(context, content, "Язык перевода", new String[]{"ru", "en", "kk"}, prefs.getString("to", "ru"));
        SeekBar original = volume(context, content, "Громкость оригинала", prefs.getInt("original", 20));
        SeekBar translated = volume(context, content, "Громкость перевода", prefs.getInt("translated", 100));
        Switch auto = new Switch(context); auto.setText("Переводить следующие видео автоматически");
        auto.setChecked(prefs.getBoolean("auto", false)); content.addView(auto);
        TextView credit = new TextView(context);
        credit.setText("Протокол: voice-over-translation / vot.js, Toil (MIT).\nКороткое нажатие VOT — включить/выключить."); content.addView(credit);
        android.widget.ScrollView scroll = new android.widget.ScrollView(context); scroll.addView(content);
        new AlertDialog.Builder(context).setTitle("VOT — закадровый перевод").setView(scroll)
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (dialog, which) -> {
                    boolean languageChanged = !from.getSelectedItem().toString().equals(prefs.getString("from", "en"))
                            || !to.getSelectedItem().toString().equals(prefs.getString("to", "ru"));
                    prefs.edit().putString("from", from.getSelectedItem().toString()).putString("to", to.getSelectedItem().toString())
                            .putInt("original", original.getProgress()).putInt("translated", translated.getProgress())
                            .putBoolean("auto", auto.isChecked()).apply();
                    if (languageChanged) VotController.translationSettingsChanged();
                    else VotController.settingsChanged();
                }).show();
    }
}
