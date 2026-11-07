package io.github.mmarkimark.llartv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * LlarTV's own settings, navigable with the TV remote.
 *
 * It writes to the same home.conf as scripts/config.sh, through the same atomic write, so both
 * ways keep working side by side and the files, the unknown keys and the untouched values are
 * preserved. The screen is only built when it is opened; Home never constructs it.
 *
 * It opens as a side panel on the right, like the Android TV settings: the panel slides in over the
 * dimmed home screen, which stays visible behind it.
 *
 * D-pad: up/down move between the settings, left/right change the value of the focused one, OK
 * opens its list of values inside the panel, Back closes that list first and then the panel.
 */
public class SettingsActivity extends Activity {
    /** One line of the menu: either a value to pick or an action to run. */
    static final class Row {
        final String label;
        final String key;            // home.conf key, null for an action
        String[] options;            // the values shown (the hidden row fills them when it opens)
        String[] values;             // what is written to home.conf, null for an action
        final Runnable action;       // for an action row
        int selected;
        boolean custom;              // a custom feed or image is in effect, so the presets do not apply
        String customLabel, customTip;   // what the row shows instead, and why it cannot be changed here
        LinearLayout view;
        TextView value;

        Row(String label, String key, String[] options, String[] values, Runnable action) {
            this.label = label;
            this.key = key;
            this.options = options;
            this.values = values;
            this.action = action;
        }

        boolean isAction() { return values == null; }
    }

    // The colours of the Android TV settings panel, sampled on the reference TV.
    static final int PANEL_BG = 0xFF202124, FOCUS_BG = 0xFF35363A, SCRIM = 0x99000000;
    static final int INK = 0xFFE8EAED, INK_DIM = 0xFF9AA0A6, ACCENT = 0xFF8AB4F8;
    static final long SLIDE_MS = 220;

    /** The third choice of the weather location panel: it searches for a city instead of being a saved value. */
    static final int WEATHER_SEARCH = 2;
    /** The request code of the location permission; only its answer is read. */
    static final int LOCATION_PERMISSION = 1;

    final List<Row> rows = new ArrayList<>();
    Ui.Fonts fonts;
    View scrim;
    LinearLayout side, list, panel;
    ScrollView scroll, panelScroll;
    TextView panelTitle, panelNote;   // the note: the reset scope, shown only on its confirmation
    LinearLayout panelList;
    int focus = 0;
    int panelIdx = 0;
    Row panelRow;
    Row hiddenRow;                 // the hidden apps: OK opens the list, to unhide one
    Row resetRow;                  // the settings reset: OK opens its confirmation, Cancel first
    Row weatherRow;                // the weather location: Automatic, the city, or a search for another one
    Row citiesRow;                 // the results of a city search: OK on one writes it to home.conf
    List<CitySearch.City> cities = new ArrayList<>();   // the places the results row lists
    Strings tx;                    // the texts of the interface language; rebuilt when the language row changes
    TextView title;
    boolean closing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);   // the panel slides in by itself
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.density = dm.density;
        fonts = new Ui.Fonts(this);

        FrameLayout root = new FrameLayout(this);   // transparent: the home screen shows through, dimmed
        scrim = new View(this);
        scrim.setBackgroundColor(SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(PANEL_BG);
        side.setPadding(0, dp(48), 0, dp(24));
        title = text(500, 20, INK);
        title.setPadding(dp(24), 0, dp(24), dp(20));
        side.addView(title, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        side.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // The values of one setting replace the list inside the panel, like a page of the system settings.
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panelTitle = text(500, 15, ACCENT);
        panelTitle.setPadding(dp(24), 0, dp(24), dp(8));
        panelList = new LinearLayout(this);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        panelNote = text(400, 14, INK_DIM);
        panelNote.setSingleLine(false);
        panelNote.setPadding(dp(24), 0, dp(24), dp(12));
        panelNote.setVisibility(View.GONE);
        panel.addView(panelNote, new LinearLayout.LayoutParams(-1, -2));
        panelScroll = new ScrollView(this);   // a custom value adds one more choice than the panel can hold
        panelScroll.setFocusable(false);
        panelScroll.setVerticalScrollBarEnabled(false);
        panelScroll.addView(panelList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(panelScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        side.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1f));

        int width = SidePanel.width(dm.widthPixels);
        root.addView(side, new FrameLayout.LayoutParams(width, -1, Gravity.END));
        setContentView(root);
        buildRows();
        applyFocus();

        scrim.setAlpha(0f);
        side.setTranslationX(width);
        scrim.animate().alpha(1f).setDuration(SLIDE_MS).start();
        side.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Slides the panel out before the activity goes, so Back leaves the way the panel came in. */
    @Override public void finish() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(SLIDE_MS).start();
        side.animate().translationX(side.getWidth()).setDuration(SLIDE_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    SettingsActivity.super.finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    /** A language set while the panel was away (scripts/config.sh) shows at once when it comes back. */
    @Override protected void onResume() {
        super.onResume();
        if (tx != null && !tx.language.equals(Config.load(this).language)) rebuild();
    }

    /** The menu again, in the language just chosen: every label, value and later message, focus kept in place. */
    void rebuild() {
        rows.clear();
        list.removeAllViews();
        buildRows();
        focus = Math.min(focus, rows.size() - 1);
        applyFocus();
    }

    /** The settings the menu shows. Each one writes to home.conf and nothing else. */
    void buildRows() {
        Config conf = Config.load(this);
        tx = Strings.of(this, conf.language);
        title.setText(tx.get(R.string.settings_title));
        // The language comes first, and each one is named in its own language, so it can be found in any of them.
        Row lang = new Row(tx.get(R.string.language), "language", Language.NAMES, Language.OPTIONS, null);
        lang.selected = Math.max(0, indexOf(lang.values, conf.language));
        rows.add(lang);
        Row clock = new Row(tx.get(R.string.clock), "clock",
                new String[]{tx.get(R.string.clock_system), tx.get(R.string.clock_24), tx.get(R.string.clock_12)},
                new String[]{Clock.SYSTEM, Clock.H24, Clock.H12}, null);
        clock.selected = Math.max(0, indexOf(clock.values, conf.clock));
        rows.add(clock);
        addMinimalRow(conf);
        addWallpaperRows(conf);
        addNightRow(conf);
        addWeatherLocationRow(conf);   // the city the weather is for (S2, S3), right before the two weather rows
        addPolicyRow(tx.get(R.string.weather_home), "weather_home", conf.weatherHome);
        addPolicyRow(tx.get(R.string.weather_screen), "weather_screen", conf.weatherScreen);
        addPolicyRow(tx.get(R.string.news_home), "news_home", conf.newsHome);
        addQuoteRow(conf);
        addSourceRow(conf);
        addScreensaverRows(conf);
        addMusicRow(conf);
        rows.add(new Row(tx.get(R.string.music_credits), null, null, null, () ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle(tx.get(R.string.music_credits))
                        .setMessage(tx.get(R.string.music_credits_body))
                        .setPositiveButton(tx.get(R.string.close), (dialog, which) -> dialog.dismiss()).show()));
        rows.add(new Row(tx.get(R.string.start_screen), null, null, null, new Runnable() {
            @Override public void run() { startScreensaver(); }
        }));
        addHiddenRow();
        resetRow = new Row(tx.get(R.string.reset_settings), null,
                new String[]{tx.get(R.string.reset_cancel), tx.get(R.string.reset_confirm)}, null, null);
        rows.add(resetRow);
        rows.add(new Row(tx.get(R.string.system_settings), null, null, null, new Runnable() {
            @Override public void run() { openSystemSettings(); }
        }));
        for (Row r : rows) addRowView(r);
        updateHiddenRow(conf.hidden.size());   // the row's value view exists from here on
    }

    /** The news source. A custom "news_rss" wins over the presets, so the row shows that instead. */
    void addSourceRow(Config conf) {
        Row r = new Row(tx.get(R.string.news_source), "news_source", Ca.newsSources(tx), NewsSource.OPTIONS, null);
        r.selected = Math.max(0, indexOf(r.values, conf.newsSource));
        r.custom = conf.newsRssIsCustom();
        r.customLabel = tx.get(R.string.news_source_custom);
        r.customTip = tx.get(R.string.news_custom_tip);
        rows.add(r);
    }

    /**
     * The wallpaper (Bing photos or a plain background) and how often the photo changes. An image URL set by the
     * scripts wins over both choices, so the row shows it and leaves it alone, like a custom news feed.
     */
    void addWallpaperRows(Config conf) {
        Row w = new Row(tx.get(R.string.wallpaper), "wallpaper", new String[]{tx.get(R.string.wallpaper_bing), tx.get(R.string.wallpaper_plain)},
                new String[]{"bing", "off"}, null);
        Appearance.Wallpaper kind = Appearance.wallpaper(conf.wallpaper);
        w.selected = kind == Appearance.Wallpaper.PLAIN ? 1 : 0;
        w.custom = kind == Appearance.Wallpaper.CUSTOM;
        w.customLabel = tx.get(R.string.wallpaper_custom);
        w.customTip = tx.get(R.string.wallpaper_custom_tip);
        rows.add(w);

        int[] minutes = Appearance.minutes(conf.wallpaperMinutes);
        String[] labels = new String[minutes.length], values = new String[minutes.length];
        for (int i = 0; i < minutes.length; i++) {
            labels[i] = Appearance.minutesLabel(tx, minutes[i]);
            values[i] = String.valueOf(minutes[i]);
        }
        Row m = new Row(tx.get(R.string.photo_change), "wallpaper_minutes", labels, values, null);
        m.selected = Appearance.indexOf(minutes, conf.wallpaperMinutes);
        rows.add(m);
    }

    /** Night mode: off, or a range of the dark warm palette on Home and the screensaver. */
    void addNightRow(Config conf) {
        String[] nights = Appearance.nights(conf.night);
        String[] labels = new String[nights.length];
        for (int i = 0; i < nights.length; i++) labels[i] = Appearance.nightLabel(tx, nights[i]);
        Row r = new Row(tx.get(R.string.night_mode), "night", labels, nights, null);
        r.selected = Appearance.indexOf(nights, Appearance.night(conf.night));
        rows.add(r);
    }

    /** A row with the three content states: never, only in the morning band, all day. */
    void addPolicyRow(String label, String key, int policy) {
        Row r = new Row(label, key,
                new String[]{tx.get(R.string.never), tx.get(R.string.morning_only), tx.get(R.string.all_day)},
                new String[]{ContentPolicy.value(ContentPolicy.NEVER), ContentPolicy.value(ContentPolicy.MORNING),
                        ContentPolicy.value(ContentPolicy.ALL)}, null);
        r.selected = policy;   // NEVER, MORNING and ALL are 0, 1 and 2, the same order as the options
        rows.add(r);
    }

    /**
     * The weather location (S2, S3): Automatic first, then the configured city, then the search for another one.
     * Left/right picks and saves the first two; OK opens all three, and the third opens the search.
     */
    void addWeatherLocationRow(Config conf) {
        weatherRow = new Row(tx.get(R.string.weather_location), "location_auto",
                new String[]{tx.get(R.string.location_auto), cityLabel(conf), tx.get(R.string.location_search)},
                new String[]{"on", "off"}, null);
        weatherRow.selected = conf.locationAuto ? 0 : 1;
        rows.add(weatherRow);
    }

    /** The city of a home.conf that does not name it: the coordinates the weather is fetched for (Barcelona by default). */
    static String cityLabel(Config conf) {
        String name = conf.locationLabel();
        return name.isEmpty() ? String.format(Locale.ROOT, "%.4f,%.4f", conf.lat, conf.lon) : name;
    }

    /**
     * The time and the slow zoom of the screensaver, each on or off; both on by default. Then the quiet night
     * screensaver and the night music silence (NightQuiet), each on or off; both off by default.
     */
    void addScreensaverRows(Config conf) {
        Row c = new Row(tx.get(R.string.clock_screen), "clock_screen", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        c.selected = conf.clockScreen ? 0 : 1;
        rows.add(c);
        Row m = new Row(tx.get(R.string.motion_screen), "motion_screen", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        m.selected = conf.motionScreen ? 0 : 1;
        rows.add(m);
        Row n = new Row(tx.get(R.string.night_screen), "night_screen", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        n.selected = conf.nightScreen ? 0 : 1;
        rows.add(n);
        Row q = new Row(tx.get(R.string.night_mute), "night_mute", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        q.selected = conf.nightMute ? 0 : 1;
        rows.add(q);
    }

    /**
     * Minimal mode (MinimalMode): on or off, off by default. It overrides the content rows under it without
     * rewriting them, so they stay editable and keep their values for when it is switched off again.
     */
    void addMinimalRow(Config conf) {
        Row r = new Row(tx.get(R.string.minimal), "minimal", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        r.selected = conf.minimal ? 0 : 1;
        rows.add(r);
    }

    /** The quote of the day under the date on Home (#69): on (the default) or off. */
    void addQuoteRow(Config conf) {
        Row r = new Row(tx.get(R.string.quote), "quote", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        r.selected = conf.quote ? 0 : 1;
        rows.add(r);
    }

    /** The screensaver music: on or off. The stream itself comes from the configuration, not from the remote. */
    void addMusicRow(Config conf) {
        Row r = new Row(tx.get(R.string.music_screen), "music_screen", new String[]{tx.get(R.string.on), tx.get(R.string.off)}, new String[]{"on", "off"}, null);
        r.selected = conf.musicScreen ? 0 : 1;
        rows.add(r);
    }

    /** The hidden apps, with how many there are as the value; OK opens the list, to unhide one. */
    void addHiddenRow() {
        hiddenRow = new Row(tx.get(R.string.hidden_apps_title), "hidden", new String[]{tx.get(R.string.none)}, new String[]{""}, null);
        rows.add(hiddenRow);
    }

    void updateHiddenRow(int count) {
        hiddenRow.selected = 0;
        hiddenRow.value.setText(Ca.hiddenApps(tx, count));
    }

    /** The hidden apps by label (the package name when it is not installed); OK on one writes the rest. */
    void openHiddenPanel() {
        Config conf = Config.load(this);
        if (conf.hidden.isEmpty()) { Toast.makeText(this, tx.get(R.string.no_hidden_apps), Toast.LENGTH_SHORT).show(); return; }
        List<String[]> items = new ArrayList<>();   // {label, package}, alphabetical by label
        for (String pkg : conf.hidden) items.add(new String[]{appLabel(pkg), pkg});
        Collections.sort(items, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[items.size()], values = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            List<String> rest = new ArrayList<>();   // the hidden line with this app taken out
            for (int j = 0; j < items.size(); j++) if (j != i) rest.add(items.get(j)[1]);
            labels[i] = items.get(i)[0];
            values[i] = String.join(",", rest);
        }
        hiddenRow.options = labels;
        hiddenRow.values = values;
        openPanel(hiddenRow);
    }

    /** The app's own label, or its package name when it is not installed any more. */
    String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (l != null && l.length() > 0) return l.toString();
        } catch (Exception ignored) {}
        return pkg;
    }

    /** A row of the panel: the setting on the first line and its value under it, like the system settings. */
    void addRowView(Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), 0, dp(24), 0);
        TextView label = text(400, 16, INK);
        label.setText(r.label);
        row.addView(label, new LinearLayout.LayoutParams(-1, -2));
        TextView value = text(400, 14, INK_DIM);
        value.setText(r.isAction() ? "" : r.custom ? r.customLabel : r.options[Math.min(r.selected, r.options.length - 1)]);
        value.setVisibility(r.isAction() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(2);
        row.addView(value, vp);
        r.view = row;
        r.value = value;
        list.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
    }

    void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, tx.get(R.string.not_available), Toast.LENGTH_SHORT).show(); }
    }

    /**
     * Starts LlarTV's screensaver now through SystemUI's exported Somnambulator, only when LlarTV is the
     * screensaver Android would start. It reads the settings and writes none. The dream covering the screen is
     * the success; every case it cannot start is reported, and nothing claims a start it cannot see.
     */
    void startScreensaver() {
        ScreenSaverStart.Check check;
        try {
            check = ScreenSaverStart.check(
                    Settings.Secure.getString(getContentResolver(), ScreenSaverStart.COMPONENTS_KEY),
                    Settings.Secure.getString(getContentResolver(), ScreenSaverStart.ENABLED_KEY));
        } catch (SecurityException ex) {   // a newer Android may not let apps read these keys
            toast(tx.get(R.string.screen_unchecked));
            return;
        }
        if (check == ScreenSaverStart.Check.NOT_SELECTED) { toast(tx.get(R.string.screen_not_selected)); return; }
        if (check == ScreenSaverStart.Check.DISABLED) { toast(tx.get(R.string.screen_disabled)); return; }
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(ScreenSaverStart.LAUNCHER_PACKAGE, ScreenSaverStart.LAUNCHER_CLASS));
        if (i.resolveActivityInfo(getPackageManager(), 0) == null) { toast(tx.get(R.string.screen_cant_start)); return; }
        try { startActivity(i); }
        catch (ActivityNotFoundException | SecurityException ex) { toast(tx.get(R.string.screen_cant_start)); }
    }

    Toast shownToast;   // the settings message on screen; the next one replaces it instead of waiting behind it (#118)

    void toast(String msg) {
        if (shownToast != null) shownToast.cancel();
        shownToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        shownToast.show();
    }

    /** The reset's confirmation: its scope above Cancel and Reset, with the focus on Cancel. */
    void openResetPanel() {
        resetRow.selected = SettingsReset.DEFAULT_CHOICE;
        openPanel(resetRow);
        panelNote.setText(tx.get(R.string.reset_scope));
        panelNote.setVisibility(View.VISIBLE);
    }

    /**
     * Runs the choice of the reset's confirmation. Cancel writes nothing; a failed write says so and leaves the
     * panel as it is. After a reset the panel is built again from home.conf, in the default language.
     */
    void resetChoice(int choice) {
        if (choice != SettingsReset.CONFIRM) return;
        File dir = ConfigLocation.dir(this);
        SettingsReset.Result result;
        try {
            if (dir == null) throw new IOException();
            result = SettingsReset.choose(dir, choice);
        } catch (IOException e) {
            toast(tx.get(R.string.reset_failed));
            return;
        }
        rebuild();   // the language (back to Catalan), every value and the hidden apps count, as read again
        toast(tx.get(result == SettingsReset.Result.RESET ? R.string.reset_done : R.string.reset_already));
    }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- values

    /** Writes one key to home.conf, keeping every other line, and shows the new value; false when it cannot. */
    boolean persist(Row r, int index) {
        if (r.isAction()) return false;
        File dir = ConfigLocation.dir(this);
        if (dir == null) {
            Toast.makeText(this, tx.get(R.string.cant_save), Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            String text = Config.read(dir);
            Config.write(dir, Config.setInText(text, r.key, r.values[index]));
        } catch (IOException e) {
            Toast.makeText(this, tx.get(R.string.cant_save), Toast.LENGTH_SHORT).show();
            return false;
        }
        r.selected = index;
        r.value.setText(r.options[index]);
        if ("language".equals(r.key)) rebuild();   // the whole panel follows the new language at once
        return true;
    }

    void openPanel(Row r) {
        panelRow = r;
        panelIdx = r.selected;
        panelTitle.setText(r.label);
        panelList.removeAllViews();
        for (String o : r.options) {
            TextView t = text(400, 16, INK);
            t.setText(o);
            t.setPadding(dp(24), 0, dp(24), 0);
            panelList.addView(t, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        scroll.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        applyPanelFocus();
    }

    void closePanel() {
        panel.setVisibility(View.GONE);
        panelNote.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        panelRow = null;
        applyFocus();
    }

    // ---------------------------------------------------------------- the weather location (S2, S3)

    /** A weather location picked: Automatic asks Android for the TV's location and says which city is used without it. */
    void weatherLocationChosen(int index) {
        if (index != 0) return;   // a city needs nothing from Android
        if (!TvLocation.allowed(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        if (TvLocation.last(this) == null) toastNoTvLocation();
    }

    /** The owner's answer: a granted permission still may leave a TV without a provider or a known location. */
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == LOCATION_PERMISSION && TvLocation.last(this) == null) toastNoTvLocation();
    }

    /** Automatic without a TV location: the weather falls back to the city, and the message names it. */
    void toastNoTvLocation() { toast(tx.get(R.string.location_no_tv, cityLabel(Config.load(this)))); }

    /**
     * The city search: one text field over the panel; its Search runs the search and Cancel writes nothing. After a
     * refused search it opens again holding what was typed, so a typo or a missing connection costs no retyping, with
     * why it was refused written in the dialog itself: a toast would sit under the TV keyboard, unseen (#118).
     */
    void openSearchDialog(String typed, String refusal) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(tx.get(R.string.location_search_hint));
        input.setText(typed);
        input.setSelection(input.length());
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(24), dp(8), dp(24), dp(8));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(tx.get(R.string.location_search))
                .setMessage(refusal)   // null on the first opening: no message line
                .setView(input)
                .setPositiveButton(tx.get(R.string.location_search_go), (d, which) -> searchCity(input.getText().toString()))
                .setNegativeButton(tx.get(R.string.reset_cancel), null)
                .create();
        input.setOnEditorActionListener((v, action, event) -> {   // the search key of the TV keyboard
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    /** Searches Open-Meteo for the typed city; every refusal says so and writes nothing. */
    void searchCity(String typed) {
        final String query = typed == null ? "" : typed.trim();
        if (!CitySearch.searchable(query)) { openSearchDialog(query, tx.get(R.string.location_short)); return; }
        toast(tx.get(R.string.location_searching));
        final String language = tx.language;
        new Thread(() -> {
            List<CitySearch.City> found;
            try { found = CitySearch.search(query, language); }
            catch (Exception e) { found = null; }   // offline, a refused request, or an answer that does not parse
            final List<CitySearch.City> results = found;
            runOnUiThread(() -> {
                if (isFinishing()) return;   // the panel is going away: nothing left to show
                if (shownToast != null) shownToast.cancel();   // the answer is here: "Searching…" has nothing left to say
                if (results == null) { openSearchDialog(query, tx.get(R.string.location_failed)); return; }
                if (results.isEmpty()) { openSearchDialog(query, tx.get(R.string.location_none, query)); return; }
                showCities(query, results);
            });
        }).start();
    }

    /** The matches of a search as a panel: OK on one writes that city to home.conf. */
    void showCities(String query, List<CitySearch.City> found) {
        cities = found;
        String[] labels = new String[found.size()];
        for (int i = 0; i < found.size(); i++) labels[i] = found.get(i).label();
        citiesRow = new Row(tx.get(R.string.location_results, query), SidePanel.CITIES_KEY, labels, null, null);
        openPanel(citiesRow);
    }

    /** Writes the city's coordinates, its name and location_auto=off in one write; a failure writes nothing. */
    void chooseCity(int index) {
        if (index < 0 || index >= cities.size()) return;
        CitySearch.City city = cities.get(index);
        File dir = ConfigLocation.dir(this);
        if (dir == null) { toast(tx.get(R.string.cant_save)); return; }
        String text = Config.read(dir);
        text = Config.setInText(text, "location", city.location());
        text = Config.setInText(text, "location_name", city.locationName());
        text = Config.setInText(text, "location_auto", "off");
        try { Config.write(dir, text); }
        catch (IOException e) { toast(tx.get(R.string.cant_save)); return; }
        rebuild();   // the row shows the city just written, with Automatic off
    }

    // ---------------------------------------------------------------- navigation

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ours = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || isOk(k) || k == KeyEvent.KEYCODE_BACK;
        if (!ours) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) onNav(k);
        return true;
    }

    static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    void onNav(int k) {
        final int U = KeyEvent.KEYCODE_DPAD_UP, D = KeyEvent.KEYCODE_DPAD_DOWN,
                L = KeyEvent.KEYCODE_DPAD_LEFT, R = KeyEvent.KEYCODE_DPAD_RIGHT, B = KeyEvent.KEYCODE_BACK;
        if (panelRow != null) {
            if (k == U) { if (panelIdx == 0) return; panelIdx--; }
            else if (k == D) { if (panelIdx >= panelRow.options.length - 1) return; panelIdx++; }
            else if (isOk(k)) {
                Row r = panelRow; int i = panelIdx; closePanel();
                if (r == resetRow) { resetChoice(i); return; }
                if (r == citiesRow) { chooseCity(i); return; }   // a place to choose, not a value of the row
                if (r == weatherRow && i == WEATHER_SEARCH) { openSearchDialog("", null); return; }
                boolean saved = persist(r, i);
                if (r == hiddenRow) updateHiddenRow(Config.load(this).hidden.size());
                if (saved && r == weatherRow) weatherLocationChosen(i);
                return;
            }
            else if (k == B) { closePanel(); return; }
            else return;
            applyPanelFocus();
            return;
        }
        Row r = rows.get(focus);
        if (k == U) { if (focus == 0) return; focus--; }
        else if (k == D) { if (focus >= rows.size() - 1) return; focus++; }
        else if (k == L || k == R) {
            if (r.isAction() || r == hiddenRow) return;
            if (r.custom) { toast(r.customTip); return; }
            int next = r.selected + (k == R ? 1 : -1);
            if (next < 0) next = r.values.length - 1;
            if (next >= r.values.length) next = 0;
            if (persist(r, next) && r == weatherRow) weatherLocationChosen(next);
        }
        else if (isOk(k)) {
            if (r == resetRow) { openResetPanel(); return; }
            if (r.isAction()) { r.action.run(); return; }
            if (r == hiddenRow) { openHiddenPanel(); return; }
            if (r.custom) { toast(r.customTip); return; }
            openPanel(r);
            return;
        }
        else if (k == B) { finish(); return; }
        else return;
        applyFocus();
    }

    void applyFocus() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            r.view.setBackgroundColor(i == focus ? FOCUS_BG : Color.TRANSPARENT);
        }
        // The panel is shorter than the screen, so the list scrolls to keep the focused row in view.
        final View v = rows.get(focus).view;
        scroll.post(() -> {
            int y = SidePanel.scrollFor(v.getTop(), v.getBottom(), scroll.getScrollY(), scroll.getHeight());
            if (y != scroll.getScrollY()) scroll.smoothScrollTo(0, y);
        });
    }

    void applyPanelFocus() {
        // The focus moves while the list is open, so the saved value keeps its own mark (#94).
        int marked = panelRow == null ? -1
                : SidePanel.markedOption(panelRow.key, panelRow.isAction(), panelRow.selected);
        for (int i = 0; i < panelList.getChildCount(); i++) {
            TextView t = (TextView) panelList.getChildAt(i);
            boolean on = i == panelIdx, saved = i == marked;
            t.setBackgroundColor(on ? FOCUS_BG : Color.TRANSPARENT);
            t.setTextColor(on ? Color.WHITE : saved ? ACCENT : INK);
            t.setTypeface(fonts.get(saved ? 500 : 400));
        }
        final View v = panelList.getChildAt(panelIdx);
        panelScroll.post(() -> {
            int y = SidePanel.scrollFor(v.getTop(), v.getBottom(), panelScroll.getScrollY(), panelScroll.getHeight());
            if (y != panelScroll.getScrollY()) panelScroll.smoothScrollTo(0, y);
        });
    }

    // ---------------------------------------------------------------- look

    TextView text(int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(fonts.get(weight));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    int dp(float v) { return Ui.dp(v); }
}
