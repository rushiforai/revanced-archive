package app.revanced.extension.youtube.vot;

/** No Activity lifecycle input: Home/screen-off must not pause background translation. */
public final class PlaybackClock {
    private long position = -1, sampledAt, advancedAt;
    private boolean playing, hasAdvanced;
    private float speed = 1f;
    public void reset() { position = -1; playing = false; hasAdvanced = false; speed = 1; }
    public void state(boolean value, long now) {
        position = expected(now); sampledAt = now; playing = value;
        // Resume only after a fresh player position; stale timestamps must not produce speech.
        hasAdvanced = false;
    }
    public void speed(float value, long now) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0 || value > 8 || value == speed) return;
        position = expected(now); sampledAt = now; speed = value;
    }
    public void sample(long value, long now) {
        if (value < 0) return;
        // A repeated frozen position is buffering, even if player status still says PLAYING.
        if (position < 0 || value != position) { advancedAt = now; hasAdvanced = true; }
        position = value; sampledAt = now;
    }
    public long expected(long now) {
        if (position < 0) return -1;
        return position + (playing ? (long) (Math.max(0, now - sampledAt) * speed) : 0);
    }
    public boolean shouldPlay(long now) {
        return playing && position >= 0 && hasAdvanced && now - advancedAt < 3000;
    }
    public float speed() { return speed; }
}
