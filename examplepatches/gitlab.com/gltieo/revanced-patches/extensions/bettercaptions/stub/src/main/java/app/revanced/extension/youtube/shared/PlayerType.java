package app.revanced.extension.youtube.shared;

/**
 * Stands in for the enum of the same name in ReVanced's own extension. Only the constants
 * and methods this patch uses are declared; nothing here is shipped.
 */
public enum PlayerType {
    NONE,
    HIDDEN,
    WATCH_WHILE_MINIMIZED,
    WATCH_WHILE_MAXIMIZED,
    WATCH_WHILE_FULLSCREEN,
    WATCH_WHILE_SLIDING_MAXIMIZED_FULLSCREEN,
    VIRTUAL_REALITY_FULLSCREEN;

    public static PlayerType getCurrent() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isMaximizedOrFullscreen() {
        throw new UnsupportedOperationException("stub");
    }
}
