import android.media.AudioTrack;
import app.revanced.extension.youtube.vot.*;
public final class AudioTests {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static boolean near(float left, float right) { return Math.abs(left - right) < .0001; }
    public static void main(String[] args) {
        AudioTrack track = new AudioTrack();
        OriginalAudio.setVolume(track, .8f); OriginalAudio.play(track);
        check(OriginalAudio.isPlaying(), "Original plays with no UI needed");
        OriginalAudio.duck(.2f);
        check(near(track.left, .16f), "Original ducking");
        check(near(OriginalAudio.gain(), .8f), "Translated focus gain independent of original duck multiplier");
        OriginalAudio.setVolume(track, .1f);
        check(near(track.left, .02f) && near(OriginalAudio.gain(), .1f), "Audio-focus attenuation follows new YouTube gain");
        OriginalAudio.pause(track);
        check(!OriginalAudio.isPlaying(), "Pause from notification/headset silences translation gate");
        check(OriginalAudio.gain() == 0, "Paused outputs contribute no gain");
        OriginalAudio.duck(1);
        check(near(track.left, .1f), "Restore latest requested volume, not initial volume");
        OriginalAudio.play(track); OriginalAudio.duck(0);
        check(track.left == 0 && OriginalAudio.isPlaying(), "Original mute must not pause translated audio");
        check(near(OriginalAudio.gain(), .1f), "Original at zero retains translated focus gain");
        OriginalAudio.setVolume(track, 1f);
        check(track.left == 0 && near(OriginalAudio.gain(), 1f), "Muted original survives audio-focus recovery without muting VOT");
        OriginalAudio.pause(track); OriginalAudio.play(track);
        check(track.left == 0 && OriginalAudio.isPlaying() && near(OriginalAudio.gain(), 1f), "Background resume preserves independent original mute");
        AudioTrack replacement = new AudioTrack(); OriginalAudio.setVolume(replacement, .7f);
        OriginalAudio.pause(track); OriginalAudio.play(replacement);
        check(replacement.left == 0, "New output/Bluetooth route inherits ducking");
        check(near(OriginalAudio.gain(), .7f), "Focus gain uses the playing output");
        OriginalAudio.duck(1);
        check(near(replacement.left, .7f), "New route restores volume");
        OriginalAudio.stop(replacement);
        check(!OriginalAudio.isPlaying(), "Stopped output immediately closes gate");
        OriginalAudio.release(track); OriginalAudio.release(replacement);
        check(!OriginalAudio.isPlaying(), "Closed player has no live outputs");
        check(VotController.callbacks >= 8, "Output changes notify background synchronizer");
        System.out.println("PASS: " + checks + " original-audio/background gate assertions (test doubles)");
    }
}
