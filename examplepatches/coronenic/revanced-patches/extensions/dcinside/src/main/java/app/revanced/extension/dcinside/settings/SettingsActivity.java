package app.revanced.extension.dcinside.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * The ReVanced settings page, opened from the "ReVanced 패치 버전" row in 설정 &gt; 정보.
 *
 * One row per setting an applied patch registered ({@link Settings#entries()}). Built in code — the
 * patch adds no layout resources — and painted with the skin colors the caller passed in
 * ({@link Ui#putPalette}).
 */
public final class SettingsActivity extends Activity implements View.OnClickListener {

    private static final String TITLE = "ReVanced 설정";
    private static final String EMPTY = "설정할 수 있는 항목이 없습니다.";

    private int background;
    private int primary;
    private int text;
    private int textSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readPalette(getIntent());
        setContentView(buildPage());
    }

    /** Back arrow. */
    @Override
    public void onClick(View v) {
        finish();
    }

    private void readPalette(Intent intent) {
        background = intent == null ? 0 : intent.getIntExtra(Ui.EXTRA_BACKGROUND, 0);
        primary = intent == null ? 0 : intent.getIntExtra(Ui.EXTRA_PRIMARY, 0);
        text = intent == null ? 0 : intent.getIntExtra(Ui.EXTRA_TEXT, 0);
        textSub = intent == null ? 0 : intent.getIntExtra(Ui.EXTRA_TEXT_SUB, 0);
        // Launched without a palette (or from a skin that resolved nothing): use our own theme.
        if (background == 0) background = Ui.background(this);
        if (primary == 0) primary = Ui.primary(this);
        if (text == 0) text = Ui.text(this);
        if (textSub == 0) textSub = Ui.textSub(this);
    }

    private View buildPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(background);
        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));
        page.addView(buildDivider());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Setting> entries = Settings.entries();
        if (entries.isEmpty()) {
            list.addView(buildNotice());
        } else {
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) list.addView(buildDivider());
                list.addView(new SwitchRow(this, entries.get(i), text, textSub, primary),
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        ScrollView scroller = new ScrollView(this);
        scroller.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setId(android.R.id.home);
        back.setImageResource(Ui.drawableId(this, "back_vector"));
        back.setColorFilter(text);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setContentDescription(null);
        Drawable ripple = Ui.themeDrawable(this, android.R.attr.selectableItemBackground);
        if (ripple != null) back.setBackground(ripple);
        back.setOnClickListener(this);
        header.addView(back, new LinearLayout.LayoutParams(
                Ui.dp(this, 48), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText(TITLE);
        title.setTextSize(17);
        title.setTextColor(text);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private View buildNotice() {
        TextView notice = new TextView(this);
        notice.setText(EMPTY);
        notice.setTextSize(14);
        notice.setTextColor(textSub);
        notice.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 32);
        notice.setPadding(pad, pad, pad, pad);
        return notice;
    }

    private View buildDivider() {
        View divider = new View(this);
        divider.setBackgroundColor((textSub & 0x00FFFFFF) | 0x33000000);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.5f))));
        return divider;
    }

    /** Title + summary on the left, a switch on the right; a tap anywhere on the row toggles it. */
    private static final class SwitchRow extends LinearLayout implements View.OnClickListener {
        private final Setting setting;
        private final Switch toggle;

        SwitchRow(Context context, Setting setting, int text, int textSub, int primary) {
            super(context);
            this.setting = setting;

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(context, 64));
            int padX = Ui.dp(context, 16);
            int padY = Ui.dp(context, 12);
            setPadding(padX, padY, padX, padY);
            Drawable ripple = Ui.themeDrawable(context, android.R.attr.selectableItemBackground);
            if (ripple != null) setBackground(ripple);
            setOnClickListener(this);

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(VERTICAL);

            TextView title = new TextView(context);
            title.setText(setting.title);
            title.setTextSize(15);
            title.setTextColor(text);
            labels.addView(title);

            if (setting.summary != null && setting.summary.length() > 0) {
                TextView summary = new TextView(context);
                summary.setText(setting.summary);
                summary.setTextSize(12);
                summary.setTextColor(textSub);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = Ui.dp(context, 4);
                labels.addView(summary, params);
            }
            addView(labels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[0]};
            toggle = new Switch(context);
            toggle.setThumbTintList(new ColorStateList(states, new int[]{primary, textSub}));
            toggle.setTrackTintList(new ColorStateList(states,
                    new int[]{(primary & 0x00FFFFFF) | 0x66000000, (textSub & 0x00FFFFFF) | 0x40000000}));
            // The row handles the tap, so the switch itself must not compete for it.
            toggle.setClickable(false);
            toggle.setFocusable(false);
            toggle.setChecked(Settings.isEnabled(context, setting.key));
            addView(toggle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        @Override
        public void onClick(View v) {
            boolean value = !toggle.isChecked();
            toggle.setChecked(value);
            Settings.setEnabled(getContext(), setting.key, value);
        }
    }
}
