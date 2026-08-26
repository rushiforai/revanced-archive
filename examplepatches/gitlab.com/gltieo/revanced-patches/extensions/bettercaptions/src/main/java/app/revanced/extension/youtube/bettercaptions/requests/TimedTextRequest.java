package app.revanced.extension.youtube.bettercaptions.requests;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.requests.Requester;

/**
 * The cues of one caption track.
 *
 * Tracks are fetched from the signed timedtext URL that came with the track list. A
 * translated track is the same URL with a target language appended, which YouTube
 * generates on demand: the first requests are answered with 429 while it is not ready,
 * and a later one returns the finished translation, after which it stays available.
 * Measured on a video asked for the first time, that was five refusals over about forty
 * seconds, so the retries are spread over a minute and run in the background while the
 * untranslated line is already on screen.
 */
public final class TimedTextRequest {

    private static final String USER_AGENT =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip";

    private static final int HTTP_TIMEOUT_MILLISECONDS = 10 * 1000;

    /**
     * Delays between attempts, in milliseconds. Long enough to cover the wait measured
     * for a translation that has to be generated, without hammering.
     */
    private static final long[] RETRY_DELAYS =
            { 2000, 4000, 8000, 12000, 16000, 20000, 30000, 30000, 30000, 30000 };

    /**
     * One line of subtitle, with the time it belongs to.
     */
    public static final class Cue {
        public final long startMs;
        public final long endMs;
        public final String text;

        public Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    private TimedTextRequest() {
    }

    /**
     * Fetches a track, retrying while YouTube answers 429.
     *
     * A track that was fetched once is kept, because YouTube hands out a translation it
     * has not made yet only after refusing for a while, and refuses the more the more it
     * is asked. Watching the same video again, or turning the patch off and on, is then
     * free rather than another minute of waiting.
     *
     * @param videoId           The video the track belongs to, which the kept copy is
     *                          named after.
     * @param baseUrl           Signed timedtext URL from the track list.
     * @param translateToOrNull Language to translate into, or null for the track as it is.
     * @return The cues in order, or an empty list if the track could not be fetched.
     */
    public static List<Cue> fetch(String videoId, String baseUrl,
                                  @Nullable String translateToOrNull) {
        final String language = translateToOrNull == null ? "" : translateToOrNull;

        List<Cue> kept = readKept(videoId, language);
        if (kept != null) {
            Logger.printDebug(() -> "Using the kept copy of " + videoId + " " + language);
            return kept;
        }

        List<Cue> fetched = fetch(baseUrl, translateToOrNull);
        if (!fetched.isEmpty()) keep(videoId, language, fetched);
        return fetched;
    }

    private static List<Cue> fetch(String baseUrl, @Nullable String translateToOrNull) {
        // The signed URL already carries a format, and the first one wins, so asking
        // for json3 only works once the original is gone.
        String url = baseUrl.replaceAll("([?&])fmt=[^&]*", "$1fmt=json3");
        if (!url.contains("fmt=")) url += "&fmt=json3";
        if (translateToOrNull != null && !translateToOrNull.isEmpty()) {
            url += "&tlang=" + translateToOrNull;
        }

        for (int attempt = 0; ; attempt++) {
            try {
                List<Cue> cues = request(url);
                if (cues != null) return cues;
            } catch (IOException ex) {
                // A connection that broke says nothing about the track, so it is worth
                // another go the same way a refusal is.
                Logger.printDebug(() -> "Timed text request failed, will retry: " + ex);
            } catch (Exception ex) {
                Logger.printException(() -> "Timed text request failed", ex);
                return Collections.emptyList();
            }

            if (attempt >= RETRY_DELAYS.length) {
                Logger.printDebug(() -> "Giving up on the translated track after "
                        + RETRY_DELAYS.length + " attempts");
                return Collections.emptyList();
            }

            try {
                Thread.sleep(RETRY_DELAYS[attempt]);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            }
        }
    }

    /**
     * @return The cues, or null if YouTube is still preparing the track and the request
     *         should be repeated.
     */
    @Nullable
    private static List<Cue> request(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        // Translated tracks are only handed out to a client that identifies itself the
        // way the player response did.
        String visitorData = CaptionTracksRequest.getVisitorData();
        if (visitorData != null) {
            connection.setRequestProperty("X-Goog-Visitor-Id", visitorData);
        }

        connection.setConnectTimeout(HTTP_TIMEOUT_MILLISECONDS);
        connection.setReadTimeout(HTTP_TIMEOUT_MILLISECONDS);

        final int responseCode = connection.getResponseCode();
        if (responseCode == 429) {
            Logger.printDebug(() -> "Track not ready yet, will retry");
            return null;
        }
        if (responseCode != 200) {
            Logger.printDebug(() -> "Timed text request answered " + responseCode);
            return Collections.emptyList();
        }

        try {
            return parse(Requester.parseJSONObject(connection));
        } catch (org.json.JSONException ex) {
            Logger.printException(() -> "Could not read the timed text response", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Where fetched tracks are kept: the app's own cache, which Android empties when it
     * needs the room.
     */
    @Nullable
    private static File keptTracks() {
        try {
            File directory = new File(Utils.getContext().getCacheDir(), "bettercaptions");
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            return directory;
        } catch (Exception ex) {
            return null;
        }
    }

    private static final int KEPT_TRACKS_LIMIT = 60;

    @Nullable
    private static List<Cue> readKept(String videoId, String language) {
        try {
            File file = keptTrack(videoId, language);
            if (file == null || !file.isFile()) return null;

            List<Cue> cues = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int firstTab = line.indexOf('\t');
                    final int secondTab = line.indexOf('\t', firstTab + 1);
                    if (firstTab < 0 || secondTab < 0) continue;

                    cues.add(new Cue(
                            Long.parseLong(line.substring(0, firstTab)),
                            Long.parseLong(line.substring(firstTab + 1, secondTab)),
                            line.substring(secondTab + 1)));
                }
            }
            return cues.isEmpty() ? null : cues;
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not read the kept track: " + ex);
            return null;
        }
    }

    private static void keep(String videoId, String language, List<Cue> cues) {
        try {
            File file = keptTrack(videoId, language);
            if (file == null) return;

            StringBuilder text = new StringBuilder();
            for (Cue cue : cues) {
                // A caption never carries a line break: they are taken out as it is read.
                text.append(cue.startMs).append('\t').append(cue.endMs).append('\t')
                        .append(cue.text).append('\n');
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(text.toString());
            }

            forgetTheOldest();
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not keep the track: " + ex);
        }
    }

    @Nullable
    private static File keptTrack(String videoId, String language) {
        File directory = keptTracks();
        if (directory == null || videoId.isEmpty()) return null;

        // Both are YouTube's own ids, which are letters, digits and dashes.
        return new File(directory, videoId.replaceAll("[^\\w-]", "") + "."
                + language.replaceAll("[^\\w-]", "") + ".track");
    }

    private static void forgetTheOldest() {
        File directory = keptTracks();
        if (directory == null) return;

        File[] kept = directory.listFiles();
        if (kept == null || kept.length <= KEPT_TRACKS_LIMIT) return;

        Arrays.sort(kept, (one, other) -> Long.compare(one.lastModified(), other.lastModified()));
        for (int index = 0; index < kept.length - KEPT_TRACKS_LIMIT; index++) {
            //noinspection ResultOfMethodCallIgnored
            kept[index].delete();
        }
    }

    private static List<Cue> parse(JSONObject response) {
        List<Cue> cues = new ArrayList<>();

        JSONArray events = response.optJSONArray("events");
        if (events == null) return cues;

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;

            JSONArray segments = event.optJSONArray("segs");
            if (segments == null) continue;

            StringBuilder text = new StringBuilder();
            for (int s = 0; s < segments.length(); s++) {
                JSONObject segment = segments.optJSONObject(s);
                if (segment != null) text.append(segment.optString("utf8", ""));
            }

            // Auto-generated tracks carry rows that hold nothing but a line break.
            final String line = text.toString().replace('\n', ' ').trim();
            if (line.isEmpty()) continue;

            final long start = event.optLong("tStartMs");
            final long duration = event.optLong("dDurationMs");
            cues.add(new Cue(start, start + duration, line));
        }

        return cues;
    }

    /**
     * @return The cue covering the given time, or null between cues. The list must be
     *         in order, which is how YouTube sends it.
     */
    @Nullable
    public static Cue cueAt(List<Cue> cues, long timeMs) {
        final int index = indexAt(cues, timeMs);
        return index < 0 ? null : cues.get(index);
    }

    /**
     * @return Index of the caption on screen at the given time, or -1 when there is
     *         none.
     *
     * Automatic tracks roll: a caption stays up while the next one is already showing,
     * so at most moments two of them cover the time and only the later one is what is
     * being said. Taking whichever one a search happened to land on left every other
     * sentence unseen.
     */
    public static int indexAt(List<Cue> cues, long timeMs) {
        int low = 0;
        int high = cues.size() - 1;
        int started = -1;

        while (low <= high) {
            final int middle = (low + high) >>> 1;

            if (cues.get(middle).startMs <= timeMs) {
                started = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        if (started < 0) return -1;
        return timeMs < cues.get(started).endMs ? started : -1;
    }
}
