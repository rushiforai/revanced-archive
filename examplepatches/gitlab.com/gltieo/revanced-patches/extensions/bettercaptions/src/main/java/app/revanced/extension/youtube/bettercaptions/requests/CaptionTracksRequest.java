package app.revanced.extension.youtube.bettercaptions.requests;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.requests.Requester;

/**
 * The caption tracks of one video, and the languages YouTube is willing to translate them into.
 *
 * The app fetches these itself for its own captions menu, but keeps them in obfuscated
 * objects that are awkward to reach and hold only the one track being shown. Asking
 * InnerTube directly costs one request per video and gives the signed timedtext URLs
 * needed for the second line.
 */
public final class CaptionTracksRequest {

    private static final String PLAYER_URL =
            "https://youtubei.googleapis.com/youtubei/v1/player?fields=captions,responseContext";

    private static final String CLIENT_VERSION = "20.10.38";
    private static final String USER_AGENT =
            "com.google.android.youtube/" + CLIENT_VERSION + " (Linux; U; Android 14) gzip";

    private static final int HTTP_TIMEOUT_MILLISECONDS = 10 * 1000;

    /**
     * Cache of the most recently opened videos, so reopening the menu costs nothing.
     */
    private static final Map<String, CaptionTracksRequest> cache =
            Collections.synchronizedMap(Utils.createSizeRestrictedMap(10));

    public static CaptionTracksRequest getRequestForVideoId(String videoId) {
        synchronized (cache) {
            CaptionTracksRequest request = cache.get(videoId);
            if (request == null) {
                request = new CaptionTracksRequest(videoId);
                cache.put(videoId, request);
            }
            return request;
        }
    }

    /**
     * One caption track, or one language YouTube can translate into.
     */
    public static final class Track {
        /** Language code such as {@code de} or {@code pt-BR}. */
        public final String languageCode;
        /** Name to show, as YouTube writes it, such as "German" or "English (auto-generated)". */
        public final String name;
        /** Signed timedtext URL of the source track. Empty for a translation-only language. */
        public final String baseUrl;
        /** Whether this is a translation of another track rather than a track of its own. */
        public final boolean isTranslation;

        Track(String languageCode, String name, String baseUrl, boolean isTranslation) {
            this.languageCode = languageCode;
            this.name = name;
            this.baseUrl = baseUrl;
            this.isTranslation = isTranslation;
        }
    }

    /**
     * Index into the track list of the track the video is spoken in, which is the one
     * YouTube itself translates from.
     */
    private static volatile int sourceTrackIndex = -1;

    public static int getSourceTrackIndex() {
        return sourceTrackIndex;
    }

    private final String videoId;
    private final Future<List<Track>> future;

    private CaptionTracksRequest(String videoId) {
        this.videoId = videoId;
        this.future = Utils.submitOnBackgroundThread(() -> fetch(videoId));
    }

    /**
     * @return The available tracks, or an empty list if the request failed or is not done yet.
     */
    public List<Track> getTracks(long maxWaitMilliseconds) {
        try {
            return future.get(maxWaitMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not get caption tracks for " + videoId, ex);
            return Collections.emptyList();
        }
    }

    private static List<Track> fetch(String videoId) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(PLAYER_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("X-YouTube-Client-Name", "3");
        connection.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION);
        connection.setConnectTimeout(HTTP_TIMEOUT_MILLISECONDS);
        connection.setReadTimeout(HTTP_TIMEOUT_MILLISECONDS);
        connection.setUseCaches(false);
        connection.setDoOutput(true);

        byte[] body = body(videoId).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        connection.getOutputStream().write(body);

        final int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            Logger.printDebug(() -> "Caption track request failed: " + responseCode);
            return Collections.emptyList();
        }

        try {
            return parse(Requester.parseJSONObject(connection));
        } catch (org.json.JSONException ex) {
            Logger.printException(() -> "Could not read the caption track response", ex);
            return Collections.emptyList();
        }
    }

    private static String body(String videoId) {
        // Written by hand rather than with JSONObject, so the client context stays readable.
        return "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\""
                + CLIENT_VERSION + "\",\"androidSdkVersion\":34,\"hl\":\""
                + Utils.getContext().getResources().getConfiguration().getLocales().get(0).getLanguage()
                + "\",\"gl\":\"US\"},\"user\":{\"lockedSafetyMode\":false}},\"videoId\":\""
                + videoId + "\",\"contentCheckOk\":true,\"racyCheckOk\":true}";
    }

    @Nullable
    private static String visitorData;

    /**
     * @return The visitor id from the last response, which the translated
     *         timedtext requests need to be accepted.
     */
    @Nullable
    public static String getVisitorData() {
        return visitorData;
    }

    private static List<Track> parse(JSONObject response) {
        List<Track> tracks = new ArrayList<>();
        try {
            JSONObject context = response.optJSONObject("responseContext");
            if (context != null) {
                String data = context.optString("visitorData", "");
                if (!data.isEmpty()) visitorData = data;
            }

            JSONObject captions = response.optJSONObject("captions");
            if (captions == null) return tracks;
            JSONObject list = captions.optJSONObject("playerCaptionsTracklistRenderer");
            if (list == null) return tracks;

            // Which track the video is actually spoken in. YouTube names it as the one
            // it offers translations of, which beats guessing from the order.
            JSONArray sourceIndices = list.optJSONArray("defaultTranslationSourceTrackIndices");
            sourceTrackIndex = (sourceIndices != null && sourceIndices.length() > 0)
                    ? sourceIndices.optInt(0, -1)
                    : -1;

            JSONArray captionTracks = list.optJSONArray("captionTracks");
            if (captionTracks != null) {
                for (int i = 0; i < captionTracks.length(); i++) {
                    JSONObject track = captionTracks.getJSONObject(i);
                    tracks.add(new Track(
                            track.optString("languageCode"),
                            name(track.optJSONObject("name")),
                            track.optString("baseUrl"),
                            false));
                }
            }

            JSONArray translations = list.optJSONArray("translationLanguages");
            if (translations != null) {
                for (int i = 0; i < translations.length(); i++) {
                    JSONObject language = translations.getJSONObject(i);
                    tracks.add(new Track(
                            language.optString("languageCode"),
                            name(language.optJSONObject("languageName")),
                            "",
                            true));
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not parse caption tracks", ex);
        }
        return tracks;
    }

    /**
     * Names arrive either as a plain string or split into runs.
     */
    private static String name(@Nullable JSONObject name) {
        if (name == null) return "";
        String simple = name.optString("simpleText", "");
        if (!simple.isEmpty()) return simple;

        JSONArray runs = name.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            text.append(Objects.requireNonNull(runs.optJSONObject(i)).optString("text", ""));
        }
        return text.toString();
    }
}
