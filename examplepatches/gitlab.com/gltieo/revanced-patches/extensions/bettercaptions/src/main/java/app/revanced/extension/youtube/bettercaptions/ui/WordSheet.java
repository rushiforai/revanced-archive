package app.revanced.extension.youtube.bettercaptions.ui;

import static app.revanced.extension.youtube.videoplayer.PlayerControlButton.getDialogBackgroundColor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.ui.Dim;
import app.revanced.extension.shared.ui.SheetBottomDialog;
import app.revanced.extension.youtube.bettercaptions.requests.WordLookup;

/**
 * What a word in a caption means, shown the way a dictionary page reads: the word, what
 * it is, what it means, and a sentence or two using it.
 *
 * The entry is fetched while the sheet is already up, since a lookup takes a moment and a
 * sheet that appears late feels like a tap that did nothing.
 */
public final class WordSheet {

    private static final int ANIMATION_DURATION = 300;

    /**
     * Grey enough to read as a label beside the text it belongs to, on either theme.
     */
    private static final int MUTED_ALPHA = 0x99;

    public static void show(Context context, String word, String languageCode) {
        try {
            SheetBottomDialog.DraggableLinearLayout layout =
                    SheetBottomDialog.createMainLayout(context, getDialogBackgroundColor());

            LinearLayout page = new LinearLayout(context);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(Dim.dp20, Dim.dp8, Dim.dp20, Dim.dp20);

            page.addView(headword(context, word));
            TextView loading = paragraph(context, "Looking it up…", 14, true);
            page.addView(loading);

            ScrollView scroller = new ScrollView(context);
            scroller.addView(page);
            scroller.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Dim.pctHeight(55)));
            layout.addView(scroller);

            SheetBottomDialog.SlideDialog dialog =
                    SheetBottomDialog.createSlideDialog(context, layout, ANIMATION_DURATION);
            dialog.show();

            Utils.runOnBackgroundThread(() -> {
                WordLookup.Entry entry = WordLookup.look(word, languageCode);
                Utils.runOnMainThreadNowOrLater(() -> {
                    try {
                        page.removeView(loading);
                        fill(context, page, word, entry);
                    } catch (Exception ex) {
                        Logger.printException(() -> "Could not show the entry", ex);
                    }
                });
            });
        } catch (Exception ex) {
            Logger.printException(() -> "Could not open the dictionary", ex);
        }
    }

    private static void fill(Context context, LinearLayout page, String word,
                             WordLookup.Entry entry) {
        if (entry.isEmpty()) {
            page.addView(paragraph(context, "Wiktionary has no entry for this word in that "
                    + "language.", 14, true));
        } else {
            page.addView(paragraph(context, entry.language, 13, true));

            for (WordLookup.PartOfSpeech part : entry.partsOfSpeech) {
                page.addView(partHeading(context, part.name));

                int number = 1;
                for (WordLookup.Sense sense : part.senses) {
                    page.addView(sense(context, number++, sense));
                }
            }
        }

        page.addView(wiktionaryLink(context, word));
    }

    private static TextView headword(Context context, String word) {
        TextView view = new TextView(context);
        view.setText(word);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        view.setTextColor(Utils.getAppForegroundColor());
        view.setPadding(0, Dim.dp8, 0, Dim.dp2);
        return view;
    }

    /**
     * The part of speech, drawn as the pill a dictionary prints beside a headword.
     */
    private static TextView partHeading(Context context, String name) {
        TextView view = new TextView(context);
        view.setText(name.toLowerCase());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(Utils.getAppForegroundColor());
        view.setPadding(Dim.dp8, Dim.dp2, Dim.dp8, Dim.dp2);

        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(Dim.dp12);
        pill.setColor(withAlpha(Utils.getAppForegroundColor(), 0x1F));
        view.setBackground(pill);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Dim.dp16, 0, Dim.dp8);
        view.setLayoutParams(params);
        return view;
    }

    /**
     * One sense: its number, what it means, and the sentences printed under it.
     */
    private static View sense(Context context, int number, WordLookup.Sense sense) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, Dim.dp12);

        TextView definition = new TextView(context);
        definition.setText(number + ".  " + plain(sense.definition));
        definition.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        definition.setTextColor(Utils.getAppForegroundColor());
        definition.setLineSpacing(0, 1.15f);
        row.addView(definition);

        for (WordLookup.Example example : sense.examples) {
            row.addView(exampleView(context, example));
        }
        return row;
    }

    /**
     * A sentence using the word, set apart by a rule down its left the way a quotation is.
     */
    private static View exampleView(Context context, WordLookup.Example example) {
        LinearLayout quote = new LinearLayout(context);
        quote.setOrientation(LinearLayout.VERTICAL);
        quote.setPadding(Dim.dp12, Dim.dp8, 0, 0);

        View rule = new View(context);
        rule.setBackgroundColor(withAlpha(Utils.getAppForegroundColor(), 0x33));

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ruleParams =
                new LinearLayout.LayoutParams(Dim.dp2, LinearLayout.LayoutParams.MATCH_PARENT);
        ruleParams.setMargins(Dim.dp8, Dim.dp6, Dim.dp10, Dim.dp2);
        holder.addView(rule, ruleParams);

        LinearLayout lines = new LinearLayout(context);
        lines.setOrientation(LinearLayout.VERTICAL);

        TextView sentence = new TextView(context);
        sentence.setText(plain(example.sentence));
        sentence.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sentence.setTextColor(Utils.getAppForegroundColor());
        sentence.setTypeface(sentence.getTypeface(), android.graphics.Typeface.ITALIC);
        lines.addView(sentence);

        if (example.translation != null) {
            lines.addView(paragraph(context, plain(example.translation), 14, true));
        }

        holder.addView(lines);
        quote.addView(holder);
        return quote;
    }

    /**
     * The way to the page itself, which has the pronunciation, the forms and where the
     * word comes from.
     */
    private static View wiktionaryLink(Context context, String word) {
        TextView link = new TextView(context);
        link.setText("Read the full entry on Wiktionary");
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        link.setTextColor(Utils.getAppForegroundColor());
        link.setGravity(Gravity.CENTER);
        link.setPadding(Dim.dp16, Dim.dp12, Dim.dp16, Dim.dp12);

        GradientDrawable button = new GradientDrawable();
        button.setCornerRadius(Dim.dp12);
        button.setColor(withAlpha(Utils.getAppForegroundColor(), 0x1F));
        link.setBackground(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Dim.dp20, 0, 0);
        link.setLayoutParams(params);

        link.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(WordLookup.pageOf(word)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                Utils.showToastShort("No app to open the page with");
            }
        });
        return link;
    }

    private static TextView paragraph(Context context, String text, int textSizeSp,
                                      boolean muted) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        view.setTextColor(muted
                ? withAlpha(Utils.getAppForegroundColor(), MUTED_ALPHA)
                : Utils.getAppForegroundColor());
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    /**
     * Wiktionary writes its definitions as markup; this is what they say.
     */
    private static String plain(String html) {
        if (TextUtils.isEmpty(html)) return "";
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private WordSheet() {
    }
}
