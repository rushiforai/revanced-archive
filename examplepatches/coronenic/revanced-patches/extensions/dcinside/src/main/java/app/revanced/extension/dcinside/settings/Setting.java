package app.revanced.extension.dcinside.settings;

/** One on/off setting, contributed by the patch that owns the feature it controls. */
final class Setting {
    final String key;
    final String title;
    final String summary;
    final boolean defaultValue;

    Setting(String key, String title, String summary, boolean defaultValue) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.defaultValue = defaultValue;
    }
}
