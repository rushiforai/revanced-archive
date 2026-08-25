package app.revanced.extension.youtube.bettercaptions;

import static app.revanced.extension.shared.settings.preference.CustomDialogListPreference.ID_REVANCED_CHECK_ICON;
import static app.revanced.extension.shared.settings.preference.CustomDialogListPreference.ID_REVANCED_CHECK_ICON_PLACEHOLDER;
import static app.revanced.extension.shared.settings.preference.CustomDialogListPreference.ID_REVANCED_ITEM_TEXT;
import static app.revanced.extension.shared.settings.preference.CustomDialogListPreference.LAYOUT_REVANCED_CUSTOM_LIST_ITEM_CHECKED;
import static app.revanced.extension.youtube.videoplayer.PlayerControlButton.getDialogBackgroundColor;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.ResourceType;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.StringSetting;
import app.revanced.extension.shared.ui.Dim;
import app.revanced.extension.shared.ui.SheetBottomDialog;
import app.revanced.extension.youtube.patches.VideoInformation;

/**
 * The two language rows this patch puts in YouTube's captions menu.
 *
 * The app has two of those menus and shows whichever the video's player asks for: a list
 * built out of views, which the caption tracks of the video are put into directly, and a
 * bottom sheet whose rows are Litho components built from what the server sends. Both end
 * up here, so both read the same.
 *
 * The sheet is recognised by what it renders: Litho keeps its own class names through
 * obfuscation and hands back the text it drew, and the app's own word for "Turn off
 * captions" appears in the captions menu and in no other sheet. The list needs no
 * recognising, since only the captions menu is built from that class.
 *
 * Both menus end in a paragraph pointing at the phone's caption settings, and the sheet
 * has a switch above it for showing captions while muted. Neither has anything to say
 * about captions this patch draws itself, so both go while it is turned on.
 */
@SuppressWarnings("unused")
public final class BetterCaptionsMenu {

    private static final String COMPONENT_HOST_CLASS = "com.facebook.litho.ComponentHost";

    /**
     * Matches the slide of the app's own menus.
     */
    private static final int ANIMATION_DURATION = 300;

    /**
     * Injection point. Called for every element renderer bottom sheet, the captions menu
     * among them.
     */
    public static void onBottomSheetCreated(View sheetView) {
        try {
            if (sheetView == null || !BetterCaptionsSettings.ENABLED.get()) return;

            // The rows are rendered after the sheet view exists, so look once laid out.
            sheetView.post(() -> {
                final String title = sheetTitle(sheetView);
                final boolean captionsMenu = title.equals(appString("subtitles"));
                final boolean translateMenu = title.equals(appString("auto_translate_subtitles"));
                if (!captionsMenu && !translateMenu) return;

                ViewGroup rows = findRowContainer(sheetView);
                if (rows != null) {
                    hideTheAppsCaptionSettings(rows);
                    // Dragging the sheet open lays the rows out again, and Litho puts
                    // back what it drew, so the same has to be done after every pass.
                    rows.addOnLayoutChangeListener(
                            (view, left, top, right, bottom,
                             wasLeft, wasTop, wasRight, wasBottom) ->
                                    hideTheAppsCaptionSettings(rows));
                }

                // The languages to translate into are a menu of the captions menu, so
                // the lines are chosen in the one they were opened from.
                if (!captionsMenu) return;

                ViewGroup container = findListContainer(sheetView);
                if (container == null) {
                    Logger.printDebug(() -> "Captions sheet has no view group to add a row to");
                    return;
                }

                container.addView(createCaptionsRow(container));
                container.addView(createRow(container, BetterCaptionsSettings.FIRST_LANGUAGE));
                container.addView(createRow(container, BetterCaptionsSettings.LANGUAGE));
                Logger.printDebug(() -> "Added the language rows to the captions sheet");
            });
        } catch (Exception ex) {
            Logger.printException(() -> "onBottomSheetCreated failure", ex);
        }
    }

    /**
     * Injection point. Called with the view of the captions menu the app builds out of a
     * list, right after the app has put its own footer in it.
     */
    public static void onCaptionsListCreated(View menuView) {
        try {
            if (menuView == null || !BetterCaptionsSettings.ENABLED.get()) return;

            menuView.post(() -> {
                ListView list = menuView.findViewById(
                        Utils.getResourceIdentifier(ResourceType.ID, "bottom_sheet_list_view"));
                if (list == null) {
                    Logger.printDebug(() -> "The captions list has no list view");
                    return;
                }

                removeTheAppsFooter(list);
                hideTheAppsTrackRows(list);
                list.addFooterView(createCaptionsRow(list), null, false);
                list.addFooterView(createRow(list, BetterCaptionsSettings.FIRST_LANGUAGE), null, false);
                list.addFooterView(createRow(list, BetterCaptionsSettings.LANGUAGE), null, false);
                Logger.printDebug(() -> "Added the language rows to the captions list");
            });
        } catch (Exception ex) {
            Logger.printException(() -> "onCaptionsListCreated failure", ex);
        }
    }

    /**
     * @return What the sheet calls itself, which is the only thing that tells one sheet
     *         from another: the rows themselves are whatever the server sent, and a list
     *         that has just been scrolled away from still holds the rows it last showed.
     *         The title is drawn above the list, so it is the text before it.
     */
    private static String sheetTitle(View sheetView) {
        ViewGroup container = findListContainer(sheetView);
        if (container == null) return "";

        List<CharSequence> rendered = new ArrayList<>();
        for (int index = 0; index < container.getChildCount(); index++) {
            View child = container.getChildAt(index);
            if (child.getClass().getName().endsWith("RecyclerView")) break;
            collectText(child, rendered);
        }
        return rendered.isEmpty() ? "" : rendered.get(0).toString();
    }

    /**
     * Leaves the sheet with the row that turns captions off and nothing else of the
     * app's own.
     *
     * The rows above it are the video's caption tracks and the languages to translate
     * them into, which is a second and a third way of choosing what the two rows this
     * patch adds already choose, in a shorter list. Below it are a switch for showing
     * captions while muted and a paragraph pointing at the phone's caption settings,
     * neither of which has anything to say about captions this patch draws.
     *
     * Litho lays its own children out and pays no attention to a hidden one, so hiding
     * alone leaves the gap where the row was. The group the rows sit in is an ordinary
     * view, though, and cutting it short at the last row left closes the gap.
     */
    private static void hideTheAppsCaptionSettings(ViewGroup rows) {
        // The rows are all the app's own, so the list holding them goes rather than each
        // of them: Litho lays its children out itself and leaves the room of a hidden one
        // behind, while the list around them is an ordinary view that takes none when it
        // is gone.
        View list = rows;
        while (list.getParent() instanceof View
                && !list.getClass().getName().endsWith("RecyclerView")) {
            list = (View) list.getParent();
        }

        if (list.getVisibility() != View.GONE) {
            list.setVisibility(View.GONE);
            Logger.printDebug(() -> "Took out the app's own caption rows");
        }
    }

    private static void shrinkTo(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        // Laying out again on a height that is already set would never end.
        if (params == null || params.height == height) return;

        params.height = height;
        view.setLayoutParams(params);
    }

    /**
     * Leaves the list menu with the row that turns captions off and nothing else, the
     * way the sheet is left.
     *
     * Its rows come from an adapter rather than from the view tree, and the app reads
     * what was tapped by where it is in that list, so the rows stay where they are and
     * are given no height instead of being taken out.
     */
    private static void hideTheAppsTrackRows(ListView list) {
        ListAdapter adapter = list.getAdapter();
        if (adapter == null || adapter instanceof OnlyTheOffRowAdapter) return;

        list.setAdapter(new OnlyTheOffRowAdapter(adapter, appString("turn_off_subtitles")));
    }

    /**
     * Takes the app's paragraph about the phone's caption settings out of the list menu,
     * where it is a footer of the list rather than a row of it.
     */
    private static void removeTheAppsFooter(ListView list) {
        View text = list.findViewById(
                Utils.getResourceIdentifier(ResourceType.ID, "bottom_sheet_footer_text"));
        if (text == null) return;

        final View footer = text.getParent() instanceof View ? (View) text.getParent() : text;
        list.removeFooterView(footer);
    }

    /**
     * @return The group holding the rows themselves, which the app puts inside its list.
     */
    @Nullable
    private static ViewGroup findRowContainer(View view) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;

        if (group.getClass().getName().endsWith("RecyclerView")) {
            // The rows are wrapped in one group of their own inside the list.
            if (group.getChildCount() == 1 && group.getChildAt(0) instanceof ViewGroup) {
                return (ViewGroup) group.getChildAt(0);
            }
            return group;
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findRowContainer(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /**
     * @return The view group holding the list of rows, which is the last ordinary view
     *         group on the way down before Litho takes over.
     */
    @Nullable
    private static ViewGroup findListContainer(View view) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().endsWith("RecyclerView")) return group;

            ViewGroup found = findListContainer(child);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * @return The row for one of the two lines, built from the layout the app's own
     *         menus end with so that it reads as part of the menu it is added to.
     */
    private static View createRow(ViewGroup parent, StringSetting language) {
        View row = inflateRow(parent);
        TextView text = rowText(row);
        text.setText(rowLabel(language));
        row.setOnClickListener(view -> showLanguageChooser(view.getContext(), text, language));
        return row;
    }

    /**
     * @return An empty row, built from the layout the app's own menus end with so that it
     *         reads as part of the menu it is added to.
     */
    private static View inflateRow(ViewGroup parent) {
        View row = LayoutInflater.from(parent.getContext()).inflate(
                Utils.getResourceIdentifierOrThrow(
                        ResourceType.LAYOUT, "bottom_sheet_list_fragment_footer"),
                parent, false);

        // That layout is meant for a note under a menu rather than for a row of one, so
        // it is drawn the way rows are: full strength, their size, and starting where
        // their names start rather than where their marks do.
        TextView text = rowText(row);
        text.setTextColor(Utils.getAppForegroundColor());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setPaddingRelative(Dim.dp48 + Dim.dp16, Dim.dp16, Dim.dp16, Dim.dp16);
        return row;
    }

    private static TextView rowText(View row) {
        return row.findViewById(
                Utils.getResourceIdentifier(ResourceType.ID, "bottom_sheet_footer_text"));
    }

    /**
     * The row that says whether the video is showing captions at all, and turns them on
     * and off. It is the patch's own rather than the app's, whose row for it says only
     * what it would do and never what is happening.
     */
    private static View createCaptionsRow(ViewGroup parent) {
        View row = inflateRow(parent);
        TextView text = rowText(row);
        text.setText(captionsLabel());

        row.setOnClickListener(view -> {
            final boolean on = !BetterCaptionsOverlay.areCaptionsOn();
            BetterCaptionsOverlay.toggleCaptions(on);
            text.setText(captionsLabel(on));
        });
        return row;
    }

    private static String captionsLabel() {
        return captionsLabel(BetterCaptionsOverlay.areCaptionsOn());
    }

    private static String captionsLabel(boolean on) {
        return "Captions: " + (on ? "on" : "off");
    }

    private static boolean isSecondLine(StringSetting language) {
        return language == BetterCaptionsSettings.LANGUAGE;
    }

    private static String title(StringSetting language) {
        return isSecondLine(language) ? "Second language" : "First language";
    }

    /**
     * @return What the entry that chooses no language of its own is called: the upper
     *         line then follows the video, and the lower line is not shown at all.
     */
    private static String automaticLabel(StringSetting language) {
        return isSecondLine(language) ? "None" : "As spoken";
    }

    private static String rowLabel(StringSetting language) {
        final String code = language.get();
        final String chosen = CaptionLanguages.chosenName(VideoInformation.getVideoId(), code);
        return title(language) + ": " + (chosen == null ? automaticLabel(language) : chosen);
    }

    /**
     * Litho draws its text itself, so a row's text is not in the view tree. It is
     * reachable through Litho's own API, whose names survive obfuscation, but which is
     * not on the compile classpath.
     */
    private static void collectText(View view, List<CharSequence> into) {
        Class<?> type = view.getClass();
        final boolean isComponentHost = COMPONENT_HOST_CLASS.equals(type.getName())
                || (type.getSuperclass() != null
                && COMPONENT_HOST_CLASS.equals(type.getSuperclass().getName()));

        if (isComponentHost) {
            try {
                Object content = type.getMethod("getTextContent").invoke(view);
                if (content != null) {
                    Object items = content.getClass().getMethod("getTextItems").invoke(content);
                    if (items instanceof List) {
                        for (Object item : (List<?>) items) {
                            if (item != null) into.add(item.toString());
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.printDebug(() -> "Could not read Litho text: " + ex);
            }
        }

        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) into.add(text);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), into);
            }
        }
    }

    /**
     * @return One of the app's own words, or an empty string if this build has no such
     *         word.
     */
    private static String appString(String name) {
        try {
            final int id = Utils.getResourceIdentifier(ResourceType.STRING, name);
            return id == 0 ? "" : Utils.getResourceString(id);
        } catch (Exception ex) {
            Logger.printDebug(() -> "The app has no string named " + name);
            return "";
        }
    }

    /**
     * Opens the chooser as a bottom sheet, built the way the video quality menu of the
     * other patches is, so it slides up and reads like the sheet it was opened from
     * rather than like a system dialog.
     *
     * Both lines are chosen from the same list, which is every language YouTube
     * translates into rather than only what the video carries.
     */
    private static void showLanguageChooser(Context context, TextView row, StringSetting language) {
        try {
            final String videoId = VideoInformation.getVideoId();
            List<CaptionLanguages.Choice> choices =
                    CaptionLanguages.choices(videoId, automaticLabel(language));

            List<String> labels = new ArrayList<>(choices.size());
            for (CaptionLanguages.Choice choice : choices) labels.add(choice.label);

            int selected = 0;
            for (int index = 0; index < choices.size(); index++) {
                if (choices.get(index).code.equals(language.get())) selected = index;
            }

            SheetBottomDialog.DraggableLinearLayout layout =
                    SheetBottomDialog.createMainLayout(context, getDialogBackgroundColor());

            TextView title = new TextView(context);
            title.setText(title(language));
            title.setTextSize(16);
            title.setTextColor(Utils.getAppForegroundColor());
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(Dim.dp12, Dim.dp16, 0, Dim.dp16);
            title.setLayoutParams(titleParams);
            layout.addView(title);

            ListView list = new ListView(context);
            CheckedListAdapter adapter = new CheckedListAdapter(context, labels);
            adapter.setSelectedPosition(selected);
            list.setAdapter(adapter);
            list.setDivider(null);
            list.setSelection(Math.max(0, selected - 2));

            SheetBottomDialog.SlideDialog dialog =
                    SheetBottomDialog.createSlideDialog(context, layout, ANIMATION_DURATION);

            list.setOnItemClickListener((parent, view, which, id) -> {
                language.save(choices.get(which).code);
                row.setText(rowLabel(language));

                // Choosing a language is asking for captions.
                BetterCaptionsOverlay.toggleCaptions(true);

                BetterCaptionsOverlay.refreshNow();
                dialog.dismiss();
            });

            layout.addView(list);
            dialog.show();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not show the language chooser", ex);
        }
    }

    /**
     * The app's own list of caption tracks with everything but the row that turns
     * captions off given no height, so that what is left reads like the sheet does.
     */
    private static final class OnlyTheOffRowAdapter extends BaseAdapter implements ListAdapter {

        private final ListAdapter wrapped;
        private final String turnOff;

        OnlyTheOffRowAdapter(ListAdapter wrapped, String turnOff) {
            this.wrapped = wrapped;
            this.turnOff = turnOff;
        }

        @Override
        public int getCount() {
            return wrapped.getCount();
        }

        @Override
        public Object getItem(int position) {
            return wrapped.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return wrapped.getItemId(position);
        }

        @Override
        public boolean isEnabled(int position) {
            return keeps(position) && wrapped.isEnabled(position);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = wrapped.getView(position, null, parent);
            if (keeps(position)) return row;

            View empty = new View(parent.getContext());
            empty.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT, 0));
            return empty;
        }

        private boolean keeps(int position) {
            if (turnOff.isEmpty()) return true;

            Object item = wrapped.getItem(position);
            return item != null && turnOff.contentEquals(labelOf(item));
        }

        /**
         * The rows are caption tracks, which name themselves in the language of the app
         * when asked for a plain string.
         */
        private static String labelOf(Object item) {
            final String label = String.valueOf(item);
            return label == null ? "" : label;
        }
    }

    /**
     * The object the app picks caption tracks with, which is what turns captions on.
     */
    private static WeakReference<Object> subtitleTracks = new WeakReference<>(null);

    /**
     * Injection point. Called with the object that holds the caption tracks of the video.
     */
    public static void rememberSubtitleTracks(Object tracks) {
        if (tracks != null) subtitleTracks = new WeakReference<>(tracks);
    }

    /**
     * Turns the captions of the video on, as picking a track in the app's own list would.
     *
     * The app draws nothing and reports nothing until a track is chosen, and this patch
     * follows it, so choosing a language here has to choose a track there as well. The
     * object that does it has a list of what the menu would show, the row that turns
     * captions off first and the tracks after it, and a way to pick one.
     */
    /**
     * Picks one of the app's caption tracks, as tapping a row of its own menu would.
     *
     * The list is the one that menu is built from: the entry that turns captions off
     * first, the tracks of the video after it.
     */
    static void chooseTrack(int which) {
        try {
            final Object tracks = subtitleTracks.get();
            if (tracks == null) {
                Logger.printDebug(() -> "No caption tracks to turn on with");
                return;
            }

            List<?> menu = null;
            for (Method method : tracks.getClass().getMethods()) {
                if (method.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(method.getReturnType())) continue;

                Object answer = method.invoke(tracks);
                if (!(answer instanceof List) || ((List<?>) answer).isEmpty()) continue;

                // The list of the menu is the one that starts with the row that turns
                // captions off; the others are lists of something else.
                final String first = String.valueOf(((List<?>) answer).get(0));
                if (first.contains(appString("turn_off_subtitles"))) {
                    menu = (List<?>) answer;
                    break;
                }
            }

            if (menu == null || menu.size() <= which) {
                Logger.printDebug(() -> "The video has no caption track to pick");
                return;
            }

            final Object track = menu.get(which);
            for (Method method : tracks.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 2) continue;
                if (!parameters[0].isInstance(track)) continue;
                if (parameters[1] != boolean.class) continue;

                method.invoke(tracks, track, true);
                Logger.printDebug(() -> "Picked the caption track " + track);
                // Picking a track from here does not go through the button the app tells
                // its caption state with, so the answer is taken from what was picked.
                return;
            }

            Logger.printDebug(() -> "Found no way to pick a caption track");
        } catch (Exception ex) {
            Logger.printException(() -> "Could not pick a caption track", ex);
        }
    }

    /**
     * Rows with a check mark on the chosen one, the same layout the other patches use
     * for their menus.
     */
    private static class CheckedListAdapter extends ArrayAdapter<String> {

        private int selectedPosition = -1;

        CheckedListAdapter(@NonNull Context context, @NonNull List<String> items) {
            super(context, 0, items);
        }

        void setSelectedPosition(int position) {
            selectedPosition = position;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(getContext())
                        .inflate(LAYOUT_REVANCED_CUSTOM_LIST_ITEM_CHECKED, parent, false);
            }

            ImageView check = view.findViewById(ID_REVANCED_CHECK_ICON);
            View placeholder = view.findViewById(ID_REVANCED_CHECK_ICON_PLACEHOLDER);
            TextView text = view.findViewById(ID_REVANCED_ITEM_TEXT);

            text.setText(getItem(position));
            final boolean selected = position == selectedPosition;
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
            placeholder.setVisibility(selected ? View.GONE : View.INVISIBLE);

            return view;
        }
    }

    private BetterCaptionsMenu() {
    }
}
