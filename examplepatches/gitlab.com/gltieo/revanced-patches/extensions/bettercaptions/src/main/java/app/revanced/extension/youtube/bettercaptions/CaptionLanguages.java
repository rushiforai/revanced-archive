package app.revanced.extension.youtube.bettercaptions;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.revanced.extension.youtube.bettercaptions.requests.CaptionTracksRequest;

/**
 * The languages either caption line can be shown in.
 *
 * The list is the same whatever the video carries. YouTube translates any track into any
 * of these on demand, and answers a request for one it has not made yet with a refusal
 * until it is ready, so a language the video has no track for is a wait rather than a
 * refusal. Asking the video what it offers gives a different, much shorter list per
 * video: for a video with automatic captions the app is offered only the language of the
 * phone, which is no use to someone learning a third one.
 *
 * A track the video carries is listed the way YouTube names it ("German", "English
 * (auto-generated)") and is preferred over a translation of the spoken track, since it
 * was written rather than machine made. Everything else is named in the language of the
 * phone.
 */
public final class CaptionLanguages {

    /**
     * Every language YouTube will translate captions into.
     */
    private static final String[] CODES = {
            "ab", "af", "ak", "sq", "am", "ar", "hy", "as", "ay", "az", "bn", "ba", "eu",
            "be", "bho", "bs", "br", "bg", "my", "ca", "ceb", "zh-Hans", "zh-Hant", "co",
            "hr", "cs", "da", "dv", "doi", "nl", "en", "eo", "et", "ee", "fo", "fj",
            "fil", "fi", "fr", "gaa", "gl", "lg", "ka", "de", "el", "gn", "gu", "ht",
            "ha", "haw", "iw", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja",
            "jv", "kn", "kk", "km", "rw", "ko", "kri", "ku", "ky", "lo", "la", "lv",
            "ln", "lt", "lb", "mk", "mai", "mg", "ms", "ml", "mt", "mi", "mr", "mn",
            "ne", "nso", "no", "ny", "oc", "or", "om", "ps", "fa", "pl", "pt", "pa",
            "qu", "ro", "rn", "ru", "sm", "sg", "sa", "gd", "sr", "crs", "sn", "sd",
            "si", "sk", "sl", "so", "st", "es", "su", "sw", "sv", "tg", "ta", "tt",
            "te", "th", "ti", "ts", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy",
            "fy", "wo", "xh", "yi", "yo", "zu",
    };

    /**
     * Names for the languages Android cannot name itself, written the way YouTube writes
     * them.
     */
    private static String fallbackName(String code) {
        switch (code) {
            case "zh-Hans": return "Chinese (Simplified)";
            case "zh-Hant": return "Chinese (Traditional)";
            case "bho": return "Bhojpuri";
            case "ceb": return "Cebuano";
            case "crs": return "Seselwa Creole";
            case "doi": return "Dogri";
            case "ee": return "Ewe";
            case "fil": return "Filipino";
            case "gaa": return "Ga";
            case "haw": return "Hawaiian";
            case "hmn": return "Hmong";
            case "kri": return "Krio";
            case "lg": return "Luganda";
            case "mai": return "Maithili";
            case "nso": return "Northern Sotho";
            case "iw": return "Hebrew";
            default: return code;
        }
    }

    /**
     * One language to pick, whether it is a track of the video or a translation.
     */
    public static final class Choice {
        /** Language code, or empty for the entry that means no line at all. */
        public final String code;
        public final String label;

        Choice(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    /**
     * Value of the language settings meaning the line follows the video rather than a
     * language of its own: the upper line then shows what is spoken, and the lower line
     * shows nothing.
     */
    public static final String AUTOMATIC = "";

    /**
     * @param videoId The video playing, whose own tracks are named the way YouTube names
     *                them. May be empty, and its track list is used only if it has
     *                already arrived.
     * @param firstEntry What the entry meaning "no language of its own" is called.
     * @return Every language either line can be shown in, the first entry first and the
     *         rest by name.
     */
    public static List<Choice> choices(String videoId, String firstEntry) {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(AUTOMATIC, firstEntry));

        Set<String> named = new HashSet<>();
        Set<String> carried = new HashSet<>();
        List<Choice> languages = new ArrayList<>();

        if (!videoId.isEmpty()) {
            for (CaptionTracksRequest.Track track
                    : CaptionTracksRequest.getRequestForVideoId(videoId).getTracks(0)) {
                if (track.isTranslation || track.languageCode.isEmpty()) continue;
                if (!named.add(track.languageCode)) continue;
                carried.add(primary(track.languageCode));
                languages.add(new Choice(track.languageCode, track.name));
            }
        }

        for (String code : CODES) {
            // A video that carries German (Germany) is offered as that and not as
            // German again, which would be the same track under a plainer name.
            if (carried.contains(primary(code))) continue;
            if (!named.add(code)) continue;
            languages.add(new Choice(code, name(code)));
        }

        Collections.sort(languages, new Comparator<Choice>() {
            @Override
            public int compare(Choice one, Choice other) {
                return one.label.compareToIgnoreCase(other.label);
            }
        });

        choices.addAll(languages);
        return choices;
    }

    private static String primary(String languageCode) {
        final int dash = languageCode.indexOf('-');
        return dash < 0 ? languageCode : languageCode.substring(0, dash);
    }

    /**
     * @return The language named in the language of the phone.
     */
    public static String name(String code) {
        if (code.isEmpty()) return "";

        final String name = Locale.forLanguageTag(code).getDisplayName();
        // An unknown code is handed back unchanged, which is no name at all.
        if (!name.isEmpty() && !name.equalsIgnoreCase(code)) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return fallbackName(code);
    }

    /**
     * @return The name to show for a language that was chosen, taken from the video's own
     *         tracks where it has one, or null if nothing is chosen.
     */
    @Nullable
    public static String chosenName(String videoId, String code) {
        if (code.isEmpty()) return null;

        if (!videoId.isEmpty()) {
            for (CaptionTracksRequest.Track track
                    : CaptionTracksRequest.getRequestForVideoId(videoId).getTracks(0)) {
                if (!track.isTranslation && track.languageCode.equals(code)) return track.name;
            }
        }
        return name(code);
    }

    private CaptionLanguages() {
    }
}
