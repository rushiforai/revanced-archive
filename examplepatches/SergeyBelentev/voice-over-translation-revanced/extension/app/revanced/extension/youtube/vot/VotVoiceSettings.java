package app.revanced.extension.youtube.vot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Account/voice settings only. Existing language and volume controls stay on long-press VT. */
public final class VotVoiceSettings {
    public static void install(PreferenceFragment fragment) {
        try {
            PreferenceScreen screen = fragment.getPreferenceScreen();
            if (screen == null || screen.findPreference("revanced_vot_voices") != null) return;
            Preference entry = new Preference(fragment.getActivity());
            entry.setKey("revanced_vot_voices"); entry.setTitle("VOT — Живые голоса");
            entry.setSummary("Выбор голосов и вход в Яндекс. Громкость — удерживайте VT в плеере.");
            entry.setOnPreferenceClickListener(preference -> { show(fragment.getActivity()); return true; });
            screen.addPreference(entry);
        } catch (Exception ex) { Log.e("VOT", "Voice settings initialization failed", ex); }
    }
    public static void show(Context context) {
        SharedPreferences prefs = VotController.preferences();
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView info = new TextView(context);
        info.setText("Живые голоса доступны при переводе на русский и требуют входа в Яндекс ID через приложение VOT.\n\nЯзык и две громкости настраиваются долгим нажатием VT в плеере.");
        content.addView(info);
        TextView account = new TextView(context); content.addView(account);
        Switch lively = new Switch(context); lively.setText("Живые голоса");
        lively.setChecked(prefs.getBoolean("lively", false)); content.addView(lively);
        Button login = new Button(context); login.setText("Войти в Яндекс"); content.addView(login);
        Button logout = new Button(context); logout.setText("Выйти из Яндекса"); content.addView(logout);
        Runnable refresh = () -> {
            boolean signedIn = !VotAccount.token().isEmpty();
            account.setText(signedIn ? "\nЯндекс: вход выполнен\n" : "\nЯндекс: необходимо войти\n");
            login.setText(signedIn ? "Войти в другой аккаунт" : "Войти в Яндекс");
            logout.setEnabled(signedIn);
        };
        lively.setOnCheckedChangeListener((button, checked) -> {
            if (checked && (VotAccount.token().isEmpty() || !"ru".equals(prefs.getString("to", "ru")))) {
                lively.setChecked(false);
                Toast.makeText(context, "Сначала войдите в Яндекс и выберите перевод на русский (удерживайте VT)", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putBoolean("lively", checked).apply();
            VotController.translationSettingsChanged();
        });
        login.setOnClickListener(view -> VotLogin.show(context, refresh));
        logout.setOnClickListener(view -> {
            VotAccount.clear();
            prefs.edit().putBoolean("lively", false).apply();
            if (lively.isChecked()) lively.setChecked(false);
            else VotController.translationSettingsChanged();
            refresh.run();
        });
        refresh.run();
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        new AlertDialog.Builder(context).setTitle("VOT — Живые голоса").setView(scroll).setPositiveButton("Готово", null).show();
    }
}
