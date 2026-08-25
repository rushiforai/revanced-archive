package app.revanced.extension.youtube.bettercaptions.requests;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.requests.Requester;

/**
 * What a word means, from Wiktionary.
 *
 * Wiktionary writes an entry per word with a section per language it exists in, and
 * hands that out as JSON: a list of senses per part of speech, each with the definition
 * and the examples printed under it. The page it came from is worth linking to, since it
 * carries the pronunciation, the inflections and the etymology this does not.
 */
public final class WordLookup {

    private static final String DEFINITION_URL =
            "https://en.wiktionary.org/api/rest_v1/page/definition/";
    public static final String PAGE_URL = "https://en.wiktionary.org/wiki/";

    private static final int HTTP_TIMEOUT_MILLISECONDS = 8 * 1000;

    /**
     * Looked up words, so that reading a caption twice costs one request.
     */
    private static final Map<String, Entry> looked =
            Collections.synchronizedMap(Utils.createSizeRestrictedMap(50));

    /**
     * A sentence using the word, and what it says in English where Wiktionary gives a
     * translation, which is what makes it worth reading in a language being learnt.
     */
    public static final class Example {
        public final String sentence;
        @Nullable
        public final String translation;

        Example(String sentence, @Nullable String translation) {
            this.sentence = sentence;
            this.translation = translation;
        }
    }

    /**
     * One sense of a word: what it means, and the sentences printed under it.
     */
    public static final class Sense {
        public final String definition;
        public final List<Example> examples;

        Sense(String definition, List<Example> examples) {
            this.definition = definition;
            this.examples = examples;
        }
    }

    /**
     * The senses of a word under one part of speech.
     */
    public static final class PartOfSpeech {
        public final String name;
        public final List<Sense> senses;

        PartOfSpeech(String name, List<Sense> senses) {
            this.name = name;
            this.senses = senses;
        }
    }

    /**
     * What Wiktionary has on a word in one language.
     */
    public static final class Entry {
        public final String word;
        public final String language;
        public final List<PartOfSpeech> partsOfSpeech;

        Entry(String word, String language, List<PartOfSpeech> partsOfSpeech) {
            this.word = word;
            this.language = language;
            this.partsOfSpeech = partsOfSpeech;
        }

        public boolean isEmpty() {
            return partsOfSpeech.isEmpty();
        }
    }

    /**
     * @param word         The word as it appeared, which is looked up as written and, if
     *                     that finds nothing, in lower case: a word at the start of a
     *                     sentence is capitalised and Wiktionary is not.
     * @param languageCode The language the caption is in, which decides which section of
     *                     the entry is the right one.
     */
    public static Entry look(String word, String languageCode) {
        final String key = word + "|" + languageCode;

        Entry kept = looked.get(key);
        if (kept != null) return kept;

        Entry entry = fetch(word, languageCode);
        if (entry == null && !word.equals(word.toLowerCase())) {
            entry = fetch(word.toLowerCase(), languageCode);
        }
        if (entry == null) entry = new Entry(word, languageCode, Collections.emptyList());

        looked.put(key, entry);
        return entry;
    }

    @Nullable
    private static Entry fetch(String word, String languageCode) {
        try {
            final String url = DEFINITION_URL
                    + URLEncoder.encode(word, StandardCharsets.UTF_8.name()).replace("+", "%20");

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            // Wikimedia asks callers to say who they are.
            connection.setRequestProperty("User-Agent",
                    "ReVanced Better captions (https://gitlab.com/gltieo/revanced-patches)");
            connection.setConnectTimeout(HTTP_TIMEOUT_MILLISECONDS);
            connection.setReadTimeout(HTTP_TIMEOUT_MILLISECONDS);

            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printDebug(() -> "No entry for " + word + ": " + responseCode);
                return null;
            }

            return parse(word, languageCode, Requester.parseJSONObject(connection));
        } catch (IOException | org.json.JSONException ex) {
            Logger.printDebug(() -> "Could not look up " + word + ": " + ex);
            return null;
        }
    }

    /**
     * The answer is keyed by language, so the section of the language being read is taken
     * where there is one and the rest is left alone.
     */
    @Nullable
    private static Entry parse(String word, String languageCode, JSONObject answer) {
        final String wanted = primary(languageCode);

        String section = null;
        for (java.util.Iterator<String> keys = answer.keys(); keys.hasNext(); ) {
            final String key = keys.next();
            if (primary(key).equals(wanted)) {
                section = key;
                break;
            }
        }
        if (section == null) return null;

        JSONArray parts = answer.optJSONArray(section);
        if (parts == null) return null;

        List<PartOfSpeech> partsOfSpeech = new ArrayList<>();
        for (int index = 0; index < parts.length(); index++) {
            JSONObject part = parts.optJSONObject(index);
            if (part == null) continue;

            List<Sense> senses = new ArrayList<>();
            JSONArray definitions = part.optJSONArray("definitions");
            if (definitions != null) {
                for (int which = 0; which < definitions.length(); which++) {
                    JSONObject definition = definitions.optJSONObject(which);
                    if (definition == null) continue;

                    final String text = definition.optString("definition", "").trim();
                    if (text.isEmpty()) continue;

                    List<Example> examples = new ArrayList<>();
                    JSONArray parsedExamples = definition.optJSONArray("parsedExamples");
                    if (parsedExamples != null) {
                        for (int example = 0; example < parsedExamples.length(); example++) {
                            JSONObject sentence = parsedExamples.optJSONObject(example);
                            if (sentence == null) continue;

                            final String written = sentence.optString("example", "").trim();
                            if (written.isEmpty()) continue;
                            final String meaning = sentence.optString("translation", "").trim();
                            examples.add(new Example(written, meaning.isEmpty() ? null : meaning));
                        }
                    }
                    JSONArray plainExamples = definition.optJSONArray("examples");
                    if (examples.isEmpty() && plainExamples != null) {
                        for (int example = 0; example < plainExamples.length(); example++) {
                            final String written = plainExamples.optString(example, "").trim();
                            if (!written.isEmpty()) examples.add(new Example(written, null));
                        }
                    }

                    senses.add(new Sense(text, examples));
                }
            }

            if (!senses.isEmpty()) {
                partsOfSpeech.add(new PartOfSpeech(part.optString("partOfSpeech", ""), senses));
            }
        }

        if (partsOfSpeech.isEmpty()) return null;

        // The section is keyed by code; the name it prints itself under reads better.
        JSONObject first = parts.optJSONObject(0);
        final String name = first == null ? section : first.optString("language", section);
        return new Entry(word, name, partsOfSpeech);
    }

    private static String primary(String languageCode) {
        final int dash = languageCode.indexOf('-');
        return (dash < 0 ? languageCode : languageCode.substring(0, dash)).toLowerCase();
    }

    /**
     * @return The address of the page the entry was taken from, which has everything this
     *         does not.
     */
    public static String pageOf(String word) {
        try {
            return PAGE_URL + URLEncoder.encode(word, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (Exception ex) {
            return PAGE_URL + word;
        }
    }

    private WordLookup() {
    }
}
