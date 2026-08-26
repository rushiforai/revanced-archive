package app.revanced.extension.youtube.bettercaptions;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.youtube.shared.VideoState;

/**
 * Stopping and starting the video, through the media buttons the player already answers.
 *
 * The player's own pause is not reachable from here, but every player answers the
 * headphone buttons, and the video being watched is what holds the media session.
 */
public final class Playback {

    /**
     * Pauses the video if it is playing.
     *
     * @return Whether it was playing, and so whether it is worth starting again.
     */
    public static boolean pause() {
        try {
            // The state is only as fresh as the last time the player reported one, so
            // anything that is not a video standing still counts as playing: pausing one
            // that is already paused costs nothing, while missing one that is playing
            // leaves the video running under the word being read.
            final VideoState state = VideoState.getCurrent();
            if (state == VideoState.PAUSED || state == VideoState.ENDED) return false;

            Logger.printDebug(() -> "Pausing the video while a word is open");
            send(KeyEvent.KEYCODE_MEDIA_PAUSE);
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not pause the video", ex);
            return false;
        }
    }

    public static void play() {
        try {
            send(KeyEvent.KEYCODE_MEDIA_PLAY);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not start the video again", ex);
        }
    }

    private static void send(int keyCode) {
        AudioManager audio = (AudioManager)
                Utils.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;

        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private Playback() {
    }
}
