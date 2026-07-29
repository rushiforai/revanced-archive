package app.revanced.extension.dcinside.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry and storage for the settings the applied patches contribute.
 *
 * A patch cannot share source with this extension, so registration is a bytecode hook: every patch
 * that owns a setting appends a {@link #registerSwitch} call to {@link #declarePatchSettings()} (see
 * {@code addSwitchSetting} in patches/.../dcinside/SettingsPatch.kt). Only settings belonging to
 * patches that were actually applied therefore exist at runtime, and the default a patch declares is
 * the one {@link #isEnabled} falls back to — patch and runtime cannot disagree.
 *
 * Values live in the app's own SharedPreferences file {@code revanced}.
 */
public final class Settings {
    private Settings() {}

    private static final String PREFS = "revanced";

    private static final List<Setting> ENTRIES = new ArrayList<Setting>();
    private static boolean declared;

    /** Called by patch-injected code in {@link #declarePatchSettings()} only. */
    public static void registerSwitch(String key, String title, String summary, boolean defaultValue) {
        ENTRIES.add(new Setting(key, title, summary, defaultValue));
    }

    /**
     * Patch injection point: {@code registerSwitch(...)} calls are appended here, in patch order.
     * Empty in the prebuilt extension — the patch owns this body, so do not add code or locals.
     */
    private static void declarePatchSettings() {
    }

    /** The registered settings, in the order the patches declared them. */
    static List<Setting> entries() {
        if (!declared) {
            declared = true;
            try {
                declarePatchSettings();
            } catch (Throwable ignored) {
            }
        }
        return ENTRIES;
    }

    /** @return the stored value of {@code key}, or the default its patch registered. */
    public static boolean isEnabled(Context context, String key) {
        boolean defaultValue = false;
        List<Setting> entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key.equals(key)) {
                defaultValue = entries.get(i).defaultValue;
                break;
            }
        }
        try {
            return prefs(context).getBoolean(key, defaultValue);
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    static void setEnabled(Context context, String key, boolean value) {
        try {
            prefs(context).edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
