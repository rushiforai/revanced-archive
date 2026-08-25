package app.revanced.extension.youtube.shared;

/**
 * Stands in for the enum of the same name in ReVanced's own extension. Only the constants
 * and methods this patch uses are declared; nothing here is shipped.
 */
public enum VideoState {
    NEW,
    PLAYING,
    PAUSED,
    ENDED;

    public static VideoState getCurrent() {
        throw new UnsupportedOperationException("stub");
    }
}
