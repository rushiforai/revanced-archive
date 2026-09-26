package android.media;
/** Test double only: never packaged into an Android extension. */
public class AudioTrack {
    public static final int STATE_INITIALIZED = 1, PLAYSTATE_PLAYING = 3;
    private int state = 1, playState;
    public float left = 1, right = 1;
    public int setVolume(float volume) { left = right = volume; return 0; }
    public int setStereoVolume(float l, float r) { left = l; right = r; return 0; }
    public void play() { playState = 3; }
    public void pause() { playState = 2; }
    public void stop() { playState = 1; }
    public void release() { state = 0; playState = 1; }
    public int getState() { return state; }
    public int getPlayState() { return playState; }
}
