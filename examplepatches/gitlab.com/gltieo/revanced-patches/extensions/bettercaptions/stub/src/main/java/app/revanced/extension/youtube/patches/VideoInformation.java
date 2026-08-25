package app.revanced.extension.youtube.patches;

/**
 * Stands in for the class of the same name in ReVanced's own extension, which is in the
 * app already. Only the shape is needed, to compile against; nothing here is shipped.
 */
public final class VideoInformation {
    public static String getVideoId() {
        throw new UnsupportedOperationException("stub");
    }

    public static float getPlaybackSpeed() {
        throw new UnsupportedOperationException("stub");
    }

    public static long getVideoLength() {
        throw new UnsupportedOperationException("stub");
    }

    private VideoInformation() {
    }
}
