package app.revanced.extension.youtube.vot;

import android.media.AudioTrack;
import java.util.Map;
import java.util.WeakHashMap;

/** All access is serialized; remember YouTube's requested gain, including audio-focus ducking. */
public final class OriginalAudio {
    private static final Map<AudioTrack, float[]> tracks = new WeakHashMap<>();
    private static float multiplier = 1;
    public static synchronized int setVolume(AudioTrack track, float volume) {
        tracks.put(track, new float[]{volume, volume});
        return track.setVolume(volume * multiplier);
    }
    @SuppressWarnings("deprecation")
    public static synchronized int setStereoVolume(AudioTrack track, float left, float right) {
        tracks.put(track, new float[]{left, right});
        return track.setStereoVolume(left * multiplier, right * multiplier);
    }
    public static synchronized void play(AudioTrack track) {
        if (!tracks.containsKey(track)) tracks.put(track, new float[]{1, 1});
        apply(track, tracks.get(track)); track.play(); VotController.outputChanged();
    }
    public static synchronized void pause(AudioTrack track) { track.pause(); VotController.outputChanged(); }
    public static synchronized void stop(AudioTrack track) { track.stop(); VotController.outputChanged(); }
    public static synchronized void release(AudioTrack track) {
        tracks.remove(track); track.release(); VotController.outputChanged();
    }
    public static synchronized boolean isPlaying() {
        for (AudioTrack track : tracks.keySet()) {
            if (track.getState() == AudioTrack.STATE_INITIALIZED && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) return true;
        }
        return false;
    }
    public static synchronized float gain() {
        float gain = 0;
        for (Map.Entry<AudioTrack, float[]> entry : tracks.entrySet()) {
            AudioTrack track = entry.getKey();
            if (track.getState() == AudioTrack.STATE_INITIALIZED && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                gain = Math.max(gain, Math.max(entry.getValue()[0], entry.getValue()[1]));
            }
        }
        return Math.max(0, Math.min(1, gain));
    }
    public static synchronized void duck(float value) {
        value = Math.max(0, Math.min(1, value));
        if (value == multiplier) return;
        multiplier = value;
        for (Map.Entry<AudioTrack, float[]> entry : tracks.entrySet()) apply(entry.getKey(), entry.getValue());
    }
    @SuppressWarnings("deprecation")
    private static void apply(AudioTrack track, float[] gain) {
        try { track.setStereoVolume(gain[0] * multiplier, gain[1] * multiplier); }
        catch (IllegalStateException ignored) { /* YouTube may concurrently replace the audio sink. */ }
    }
}
