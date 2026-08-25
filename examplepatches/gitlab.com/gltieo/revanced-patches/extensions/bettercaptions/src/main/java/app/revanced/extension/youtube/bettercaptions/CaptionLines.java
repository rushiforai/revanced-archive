package app.revanced.extension.youtube.bettercaptions;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.youtube.bettercaptions.requests.CaptionTracksRequest;
import app.revanced.extension.youtube.bettercaptions.requests.TimedTextRequest;

/**
 * The two tracks of the video playing, and which line belongs on screen right now.
 *
 * Each line is shown in whichever language was chosen for it, which is a track of the
 * video where it has one and a translation YouTube generates where it has not. The
 * upper line follows the video unless a language was chosen for it, so it is the
 * language being spoken by default.
 *
 * Both tracks are loaded once per video in the background. The upper line appears as
 * soon as it arrives; the lower line can take a while when YouTube has to translate it
 * first, so it fills in later rather than holding up the rest.
 */
final class CaptionLines {

    private static final Object lock = new Object();

    private static String loadedVideoId = "";
    private static String loadedFirstLanguage = "";
    private static String loadedSecondLanguage = "";

    private static volatile List<TimedTextRequest.Cue> upper = Collections.emptyList();
    private static volatile List<TimedTextRequest.Cue> lower = Collections.emptyList();

    /**
     * What each line is written in, which is what a word tapped in it is looked up as.
     */
    private static volatile String upperLanguage = "";
    private static volatile String lowerLanguage = "";

    static String upperLanguage() {
        return upperLanguage;
    }

    static String lowerLanguage() {
        return lowerLanguage;
    }

    private CaptionLines() {
    }

    /**
     * Loads the tracks of the video, if they are not already loaded. Cheap to call
     * often; the work happens once per video and chosen language.
     */
    static void ensureLoaded(String videoId) {
        final String firstLanguage = BetterCaptionsSettings.FIRST_LANGUAGE.get();
        final String secondLanguage = BetterCaptionsSettings.LANGUAGE.get();

        synchronized (lock) {
            if (videoId.equals(loadedVideoId)
                    && firstLanguage.equals(loadedFirstLanguage)
                    && secondLanguage.equals(loadedSecondLanguage)) {
                return;
            }
            loadedVideoId = videoId;
            loadedFirstLanguage = firstLanguage;
            loadedSecondLanguage = secondLanguage;
            upper = Collections.emptyList();
            lower = Collections.emptyList();
            upperLanguage = "";
            lowerLanguage = "";
            pairedByPlace = false;
        }

        Utils.runOnBackgroundThread(() -> load(videoId, firstLanguage, secondLanguage));
    }

    private static void load(String videoId, String firstLanguage, String secondLanguage) {
        try {
            List<CaptionTracksRequest.Track> tracks =
                    CaptionTracksRequest.getRequestForVideoId(videoId).getTracks(15_000);
            if (tracks.isEmpty()) {
                Logger.printDebug(() -> "No caption tracks for " + videoId);
                return;
            }

            CaptionTracksRequest.Track source = sourceTrack(tracks);
            if (source == null) return;

            final String shownAbove = firstLanguage.isEmpty() ? source.languageCode : firstLanguage;

            List<TimedTextRequest.Cue> upperCues = cuesOf(videoId, source, tracks, firstLanguage);
            if (!videoId.equals(currentlyLoading())) return; // Moved on to another video.
            upper = upperCues;
            upperLanguage = shownAbove;
            if (BetterCaptionsOverlay.areCaptionsOn()) BetterCaptionsOverlay.tellTheAppCaptionsAreOn();
            Logger.printDebug(() -> "Loaded " + upperCues.size() + " cues for the upper line in "
                    + shownAbove);
            BetterCaptionsOverlay.refreshNow();

            // A second line in the language of the first is the same sentence twice.
            if (secondLanguage.isEmpty() || sameLanguage(secondLanguage, shownAbove)) {
                Logger.printDebug(() -> "No second line: " + (secondLanguage.isEmpty()
                        ? "no language chosen"
                        : secondLanguage + " is what the line above shows"));
                return;
            }

            List<TimedTextRequest.Cue> secondCues = cuesOf(videoId, source, tracks, secondLanguage);

            if (!videoId.equals(currentlyLoading())) return;
            lower = secondCues;
            lowerLanguage = secondLanguage;
            pairedByPlace = cutTheSameWay(upperCues, secondCues);
            Logger.printDebug(() -> "Loaded " + secondCues.size() + " cues for " + secondLanguage);
            BetterCaptionsOverlay.refreshNow();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not load the subtitle tracks", ex);
        }
    }

    /**
     * @return The cues of one line, which are a track of the video where the language
     *         chosen has one, and a translation of the spoken track where it has not.
     *         An unset language is the spoken track itself.
     */
    private static List<TimedTextRequest.Cue> cuesOf(
            String videoId,
            CaptionTracksRequest.Track source,
            List<CaptionTracksRequest.Track> tracks,
            String languageCode) {
        if (languageCode.isEmpty() || sameLanguage(languageCode, source.languageCode)) {
            return TimedTextRequest.fetch(videoId, source.baseUrl, null);
        }

        // A track written in that language is better than a translation of another one.
        CaptionTracksRequest.Track own = trackOfLanguage(tracks, languageCode);
        return own != null
                ? TimedTextRequest.fetch(videoId, own.baseUrl, null)
                : TimedTextRequest.fetch(videoId, source.baseUrl, languageCode);
    }

    private static String currentlyLoading() {
        synchronized (lock) {
            return loadedVideoId;
        }
    }

    /**
     * @return The track the video is spoken in. YouTube marks it as the track it
     *         translates from; without that, the automatic captions are the best guess,
     *         since they are made from the audio.
     */
    @Nullable
    private static CaptionTracksRequest.Track sourceTrack(List<CaptionTracksRequest.Track> tracks) {
        final int index = CaptionTracksRequest.getSourceTrackIndex();
        if (index >= 0 && index < tracks.size()) {
            CaptionTracksRequest.Track track = tracks.get(index);
            if (!track.isTranslation && !track.baseUrl.isEmpty()) return track;
        }

        for (CaptionTracksRequest.Track track : tracks) {
            if (track.isTranslation || track.baseUrl.isEmpty()) continue;
            if (track.name.contains("auto-generated")) return track;
        }

        for (CaptionTracksRequest.Track track : tracks) {
            if (!track.isTranslation && !track.baseUrl.isEmpty()) return track;
        }
        return null;
    }

    @Nullable
    private static CaptionTracksRequest.Track trackOfLanguage(
            List<CaptionTracksRequest.Track> tracks, String languageCode) {
        CaptionTracksRequest.Track sameLanguage = null;

        for (CaptionTracksRequest.Track track : tracks) {
            if (track.isTranslation || track.baseUrl.isEmpty()) continue;
            if (Objects.equals(track.languageCode, languageCode)) return track;

            // A track for the country rather than the language, such as German
            // (Germany) where German was asked for. Still that language, and written
            // rather than machine made, so better than a translation.
            if (sameLanguage == null && sameLanguage(track.languageCode, languageCode)) {
                sameLanguage = track;
            }
        }
        return sameLanguage;
    }

    private static boolean sameLanguage(String one, String other) {
        return primary(one).equals(primary(other));
    }

    private static String primary(String languageCode) {
        final int dash = languageCode.indexOf('-');
        return dash < 0 ? languageCode : languageCode.substring(0, dash);
    }

    /**
     * @return The line to show on top, or null when nothing is being said.
     */
    @Nullable
    static String upperLineAt(long timeMs) {
        final int index = TimedTextRequest.indexAt(upper, timeMs);
        return index < 0 ? null : upper.get(index).text;
    }

    /**
     * @return The line to show underneath, or null when that track says nothing at this
     *         moment.
     *
     * A translation is made from the track above and is cut the same way, so the two
     * lines are the same sentence in the same place and are paired by it: read from its
     * own timing instead, the second line would show the tail of the sentence before
     * wherever the two tracks disagree by a moment. Where the tracks are not cut the same
     * way, which is any track written by someone else, its own timing is all there is to
     * go on.
     */
    @Nullable
    static String lowerLineAt(long timeMs) {
        if (pairedByPlace) {
            final int index = TimedTextRequest.indexAt(upper, timeMs);
            return index < 0 || index >= lower.size() ? null : lower.get(index).text;
        }

        final int index = TimedTextRequest.indexAt(lower, timeMs);
        return index < 0 ? null : lower.get(index).text;
    }

    /**
     * Whether the two tracks are cut the same way, which is what makes the place of a
     * caption in one of them mean the same in the other.
     */
    private static volatile boolean pairedByPlace;

    private static boolean cutTheSameWay(List<TimedTextRequest.Cue> one,
                                         List<TimedTextRequest.Cue> other) {
        if (one.size() != other.size() || one.isEmpty()) return false;

        for (int index = 0; index < one.size(); index++) {
            if (Math.abs(one.get(index).startMs - other.get(index).startMs) > 250) return false;
        }
        return true;
    }

    /**
     * @return Whether the spoken track has arrived. Until it has, YouTube's own captions
     *         are the only ones there are and hiding them would leave the video with
     *         none at all.
     */
    static boolean hasSpokenTrack() {
        return !upper.isEmpty();
    }

}
