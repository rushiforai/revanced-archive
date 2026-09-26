package app.revanced.extension.youtube.vot;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.youtube.patches.VideoInformation;

/** Owns one translation session on a dedicated looper, independent of the Activity. */
public final class VotController {
    private static final Handler handler;
    private static final ExecutorService network = Executors.newSingleThreadExecutor(r -> new Thread(r, "VOT-network"));
    private static final PlaybackClock clock = new PlaybackClock();
    private static String videoId = "", requestedId = "";
    private static boolean playing, prepared, seeking, seekAgain, lively;
    private static long generation, pendingSeek, seekStarted, startedAt;
    private static float appliedSpeed = -1;
    private static MediaPlayer player;
    private static VotApi api;
    private static Future<?> request;
    private static long duration;
    private static int networkFailures;
    public static volatile String status = "VOT выключен";
    public static volatile boolean active;

    static {
        HandlerThread thread = new HandlerThread("VOT-playback"); thread.start();
        handler = new Handler(thread.getLooper());
    }
    public static SharedPreferences preferences() {
        return Utils.getContext().getApplicationContext().getSharedPreferences("revanced_vot", Context.MODE_PRIVATE);
    }
    private static long now() { return SystemClock.elapsedRealtime(); }
    private static void toast(String text) { Utils.showToastLong(text); }
    public static void outputChanged() { handler.post(VotController::sync); }

    // All injections may arrive from YouTube's playback threads. Never use Activity onPause here.
    public static void onVideoId(String id) {
        handler.post(() -> {
            if (id == null || id.equals(videoId)) return;
            stopInternal(); videoId = id; requestedId = ""; duration = 0;
            clock.reset(); clock.state(playing, now());
        });
    }
    public static void onTime(long milliseconds) {
        final String id = VideoInformation.getVideoId();
        final long length = VideoInformation.getVideoLength();
        final float speed = VideoInformation.getPlaybackSpeed();
        final boolean shorts = VideoInformation.lastVideoIdIsShort();
        handler.post(() -> {
            if (!videoId.equals(id)) return;
            duration = length;
            clock.speed(speed, now());
            clock.sample(milliseconds, now());
            if (!active && requestedId.isEmpty() && length > 0 && !shorts && preferences().getBoolean("auto", false)) start();
            sync();
        });
    }
    public static void onState(Enum<?> state) {
        final String name = state == null ? "" : state.name();
        handler.post(() -> {
            // Controller stage is VIDEO_PLAYING (not the UI enum's PLAYING).
            // AudioTrack state below supplies pause/resume even with no visible player UI.
            boolean nextPlaying = "VIDEO_PLAYING".equals(name);
            if (nextPlaying != playing) { playing = nextPlaying; clock.state(playing, now()); }
            if ("ENDED".equals(name) || "UNRECOVERABLE_ERROR".equals(name)) stopInternal();
            else sync();
        });
    }
    public static void onSpeed(float speed) { handler.post(() -> { clock.speed(speed, now()); sync(); }); }
    public static void toggle() { handler.post(() -> { if (active) stopInternal(); else start(); }); }
    public static void settingsChanged() { handler.post(VotController::sync); }
    public static void translationSettingsChanged() { handler.post(() -> { if (active) start(); }); }

    private static void start() {
        if (!videoId.matches("[A-Za-z0-9_-]{11}") || duration <= 0 || duration > 14400000
                || VideoInformation.lastVideoIdIsShort()) {
            toast("VOT: откройте обычное видео длительностью до 4 часов"); return;
        }
        stopInternal();
        requestedId = videoId;
        lively = preferences().getBoolean("lively", false);
        final String accountToken = lively ? VotAccount.token() : "";
        final String from = preferences().getString("from", "en"), to = preferences().getString("to", "ru");
        if (lively && !"ru".equals(to)) { fail("Живые голоса: выберите перевод на русский (удерживайте VT)"); return; }
        try { api = new VotApi(lively, accountToken); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); return; }
        active = true;
        final long token = generation;
        final String id = videoId;
        final double seconds = duration / 1000.0;
        final VotApi client = api;
        startedAt = now(); status = lively ? "VOT: подготовка живых голосов…" : "VOT: подготовка перевода…"; toast(status);
        networkFailures = 0;
        poll(token, id, seconds, from, to, client);
    }
    private static void poll(long token, String id, double seconds, String from, String to, VotApi client) {
        if (token != generation || !active) return;
        if (now() - startedAt > 20 * 60 * 1000L) { fail("VOT: превышено время ожидания перевода"); return; }
        request = network.submit(() -> {
            try {
                VotApi.Result result = client.translate(id, seconds, from, to);
                handler.post(() -> {
                    if (token != generation || !active) return;
                    networkFailures = 0;
                    if (result.status == 1 && !result.url.isEmpty()) {
                        prepare(token, result.url);
                    } else if (result.status == 2 || result.status == 3 || result.status == 5 || result.status == 6) {
                        // Wait for the full track. Do not let a partial 10-minute file silently end in background.
                        status = result.status == 5 ? "VOT: ожидаю полную аудиодорожку…" : "VOT: перевод готовится…";
                        int delay = Math.max(5, Math.min(60, result.remaining));
                        handler.postDelayed(() -> poll(token, id, seconds, from, to, client), delay * 1000L);
                    } else if (result.status == 7) fail("VOT: сервис требует входа. Откройте ReVanced → VOT — Живые голоса и войдите в Яндекс");
                    else fail("VOT: сервис не перевёл видео" + (result.message.isEmpty() ? "" : ": " + result.message));
                });
            } catch (Exception ex) {
                handler.post(() -> {
                    if (token != generation || !active) return;
                    if (ex instanceof VotApi.AuthorizationException) {
                        VotAccount.invalidate(client.accountToken());
                        fail(ex.getMessage()); return;
                    }
                    String message = String.valueOf(ex.getMessage());
                    if (++networkFailures <= 3 && !message.matches(".*HTTP 4[0-9][0-9].*")) {
                        status = "VOT: повторное подключение…";
                        handler.postDelayed(() -> poll(token, id, seconds, from, to, client), 1000L << networkFailures);
                    } else fail("VOT: " + message);
                });
            }
        });
    }
    private static void prepare(long token, String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException("Небезопасный URL аудио");
            Context context = Utils.getContext().getApplicationContext();
            MediaPlayer media = new MediaPlayer(); player = media;
            media.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            // YouTube owns the foreground playback service and audio focus. A second focus request
            // here would pause YouTube. MediaPlayer releases its partial wakelock on pause/release.
            media.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            media.setOnPreparedListener(mp -> {
                if (token != generation || player != mp) return;
                prepared = true; status = playingStatus();
                sync(); handler.removeCallbacks(ticker); handler.post(ticker);
            });
            media.setOnSeekCompleteListener(mp -> {
                if (token != generation || player != mp) return;
                seeking = false;
                // Rapid consecutive scrubs: don't start an obsolete seek position.
                if (seekAgain) { seekAgain = false; seek(clock.expected(now())); }
                else sync();
            });
            media.setOnCompletionListener(mp -> {
                if (token == generation && player == mp) {
                    stopInternal(); status = "VOT: аудиодорожка закончилась";
                }
            });
            media.setOnErrorListener((mp, what, extra) -> {
                if (token == generation && player == mp) fail("VOT: ошибка аудиодорожки (" + what + "/" + extra + ")");
                return true;
            });
            media.setDataSource(context, uri); media.prepareAsync();
            handler.postDelayed(() -> { if (token == generation && player == media && !prepared) fail("VOT: аудио не загрузилось за 45 секунд"); }, 45000);
        } catch (Exception ex) { fail("VOT: не удалось открыть аудио: " + ex.getMessage()); }
    }
    private static final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player == null || !active) return;
            sync();
            if (player != null && active) handler.postDelayed(this, 250);
        }
    };
    private static void seek(long position) {
        if (position < 0 || player == null || !prepared) return;
        if (seeking) { if (Math.abs(position - pendingSeek) > 800) seekAgain = true; return; }
        if (player.isPlaying()) player.pause(); OriginalAudio.duck(1);
        seeking = true; pendingSeek = position; seekStarted = now();
        player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
    }
    private static void sync() {
        if (player == null || !prepared || !active) return;
        try {
            long current = now(); long target = clock.expected(current);
            boolean play = clock.shouldPlay(current) && OriginalAudio.isPlaying();
            if (!play) {
                if (player.isPlaying()) player.pause();
                OriginalAudio.duck(1); return;
            }
            if (seeking) {
                if (current - seekStarted > 10000) { fail("VOT: ошибка перемотки аудио"); return; }
                if (Math.abs(target - pendingSeek) > 1500) seekAgain = true;
                return;
            }
            long remaining = duration - target;
            if (remaining <= 0) { stopInternal(); return; }
            if (Math.abs(player.getCurrentPosition() - target) > 700) { seek(target); return; }
            float speed = clock.speed();
            boolean speedChanged = appliedSpeed != speed;
            // Follow YouTube's own audio-focus attenuation (navigation prompts, notifications).
            float translated = preferences().getInt("translated", 100) / 100f * OriginalAudio.gain();
            player.setVolume(translated, translated);
            if (speedChanged) {
                // setPlaybackParams can start a paused MediaPlayer. This branch is only reached when YouTube plays.
                player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f)); appliedSpeed = speed;
            }
            // Explicit start also enters MediaPlayer's Java wake-lock handling after speed changes.
            if (speedChanged || !player.isPlaying()) player.start();
            OriginalAudio.duck(preferences().getInt("original", 20) / 100f);
            status = playingStatus();
        } catch (Exception ex) { fail("VOT: ошибка синхронизации: " + ex.getMessage()); }
    }
    private static String playingStatus() { return lively ? "VOT: живые голоса включены" : "VOT: перевод включён"; }
    private static void fail(String message) { stopInternal(); status = message; toast(message); }
    private static void stopInternal() {
        generation++; active = false;
        handler.removeCallbacks(ticker);
        if (player != null) { player.release(); player = null; }
        prepared = false; seeking = false; seekAgain = false; appliedSpeed = -1;
        OriginalAudio.duck(1); status = "VOT выключен";
        if (api != null) { api.cancel(); api = null; }
        if (request != null) { request.cancel(true); request = null; }
    }
}
