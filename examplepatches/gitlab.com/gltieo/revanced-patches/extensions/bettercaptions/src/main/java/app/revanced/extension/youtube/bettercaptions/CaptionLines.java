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
            upperSentences = Collections.emptyList();
            lowerSentences = Collections.emptyList();
            captionSpans = Collections.emptyList();
            upperLanguage = "";
            lowerLanguage = "";
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
            captionSpans = captionSpans(upperCues);
            upperSentences = captionsOf(upperCues, captionSpans);
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
            // A track of its own is cut where it says so; a translation is cut where the
            // track above it is, cue for cue.
            lowerSentences = secondCues.size() == upperCues.size()
                    ? captionsOf(secondCues, captionSpans)
                    : captionsOf(secondCues, captionSpans(secondCues));
            lowerLanguage = secondLanguage;
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
        final int index = TimedTextRequest.indexAt(upperSentences, timeMs);
        return index < 0 ? null : upperSentences.get(index).text;
    }

    /**
     * @return The line to show underneath, or null when that track says nothing at this
     *         moment. It is the same sentence as the line above wherever both tracks say
     *         one.
     */
    @Nullable
    static String lowerLineAt(long timeMs) {
        if (lowerSentences.isEmpty()) return null;

        final int index = TimedTextRequest.indexAt(upperSentences, timeMs);
        if (index < 0) return null;

        // Both tracks say the same thing, so with the same number of sentences the nth
        // of one is the nth of the other, whatever cues each was cut into.
        if (lowerSentences.size() == upperSentences.size()) {
            return lowerSentences.get(index).text;
        }
        return coveringMost(upperSentences.get(index), lowerSentences);
    }

    /**
     * @return The sentence of the other track that covers most of the given one, or null
     *         where it says nothing there.
     */
    @Nullable
    private static String coveringMost(TimedTextRequest.Cue sentence,
                                       List<TimedTextRequest.Cue> other) {
        long bestOverlap = 0;
        String best = null;

        for (TimedTextRequest.Cue candidate : other) {
            if (candidate.endMs <= sentence.startMs) continue;
            if (candidate.startMs >= sentence.endMs) break;

            final long overlap = Math.min(sentence.endMs, candidate.endMs)
                    - Math.max(sentence.startMs, candidate.startMs);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                best = candidate.text;
            }
        }
        return best;
    }

    /**
     * The tracks as sentences rather than as the cues they arrive in.
     *
     * A track is cut into cues to be read a line at a time, and two tracks of the same
     * words are cut differently: a translation says in one cue what the spoken track
     * takes two to say. Shown cue by cue, the two lines then sit at different points of
     * the same sentence, one of them a few words behind. Whole sentences are the same
     * thing in both, so both lines change together and always say the same.
     */
    private static volatile List<TimedTextRequest.Cue> upperSentences = Collections.emptyList();
    private static volatile List<TimedTextRequest.Cue> lowerSentences = Collections.emptyList();

    /**
     * Which cues of the track above make one caption each, which the track below is cut
     * by as well.
     */
    private static volatile List<int[]> captionSpans = Collections.emptyList();

    /**
     * How long a caption may run before it is closed whether or not anything ended it,
     * and how long a silence closes one.
     *
     * Some automatic tracks carry no full stops at all, and a caption that is never
     * closed swallows the whole video.
     */
    private static final long LONGEST_CAPTION_MILLISECONDS = 12_000;
    private static final long SILENCE_MILLISECONDS = 1500;

    /**
     * The captions the lines show, which are the cues joined into sentences.
     *
     * A track is cut into cues to be read a line at a time, and a caption is joined until
     * one of them ends a sentence. Both tracks are cut at the same cues, since a
     * translation is made cue by cue from the track above and carries its timing: cutting
     * each by its own words instead left the two lines on different sentences wherever
     * they disagreed by a word.
     */
    private static List<TimedTextRequest.Cue> captionsOf(List<TimedTextRequest.Cue> cues,
                                                         List<int[]> spans) {
        List<TimedTextRequest.Cue> captions = new ArrayList<>();

        for (int[] span : spans) {
            final int from = Math.min(span[0], cues.size() - 1);
            final int to = Math.min(span[1], cues.size() - 1);
            if (from < 0 || to < from) continue;

            StringBuilder text = new StringBuilder();
            for (int index = from; index <= to; index++) {
                final String piece = cues.get(index).text.trim();
                if (piece.isEmpty()) continue;
                if (text.length() > 0) text.append(' ');
                text.append(piece);
            }

            captions.add(new TimedTextRequest.Cue(
                    cues.get(from).startMs, cues.get(to).endMs, text.toString()));
        }
        return captions;
    }

    /**
     * @return Which cues make one caption each: consecutive cues up to the one that ends
     *         a sentence, or that has run long enough, or that is followed by a silence.
     */
    private static List<int[]> captionSpans(List<TimedTextRequest.Cue> cues) {
        List<int[]> spans = new ArrayList<>();

        int from = 0;
        StringBuilder text = new StringBuilder();

        for (int index = 0; index < cues.size(); index++) {
            final TimedTextRequest.Cue cue = cues.get(index);
            if (text.length() > 0) text.append(' ');
            text.append(cue.text.trim());

            final boolean ended = endsASentence(text);
            final boolean runLong =
                    cue.endMs - cues.get(from).startMs > LONGEST_CAPTION_MILLISECONDS;
            final boolean silenceAfter = index + 1 < cues.size()
                    && cues.get(index + 1).startMs - cue.endMs > SILENCE_MILLISECONDS;

            if (ended || runLong || silenceAfter || index == cues.size() - 1) {
                spans.add(new int[]{from, index});
                from = index + 1;
                text.setLength(0);
            }
        }
        return spans;
    }

    private static boolean endsASentence(CharSequence text) {
        for (int index = text.length() - 1; index >= 0; index--) {
            final char character = text.charAt(index);
            if (Character.isWhitespace(character) || character == '"' || character == ')'
                    || character == ']' || character == '\u201d' || character == '\'') {
                continue;
            }
            return character == '.' || character == '?' || character == '!'
                    || character == '\u2026';
        }
        return false;
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
