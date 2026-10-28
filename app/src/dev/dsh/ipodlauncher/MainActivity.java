package dev.dsh.ipodlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * iPod Classic style home screen with local music playback.
 *
 * Menus are navigated entirely with the click wheel: rotating scrolls, the
 * center button selects, MENU goes back, the skip buttons page up/down (or
 * change track while playing) and play/pause opens Now Playing.
 */
public class MainActivity extends Activity implements ClickWheelView.Listener, IpodScreenView.TouchListener {

    private static final int REQ_AUDIO = 41;

    private IpodScreenView screen;
    private ClickWheelView wheelView;
    private FrameLayout rootView;
    private Theme theme;
    private Haptics haptics;
    private Clicks clicks;
    private SharedPreferences prefs;
    private MusicLibrary library;
    private Player player;
    private TrackMeta meta;
    private AudioManager audio;

    private Bitmap currentArt;
    private long metaTrackId = -1;
    private int npMode; // 0 = artwork + progress, 1 = lyrics

    private final ArrayList<Page> stack = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int volumePct = 50;
    private boolean volumeVisible;
    private boolean autoScanned;
    private boolean shuffleOn;

    private static final String[] SPEED_NAMES = {"Slow", "Normal", "Fast"};
    private static final float[] SPEED_DEG = {11f, 17f, 24f};
    private static final String[] STRENGTH_NAMES = {"Soft", "Normal", "Strong"};

    private final Runnable hideVolume = new Runnable() {
        public void run() {
            volumeVisible = false;
            refresh();
        }
    };

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (!stack.isEmpty() && page().nowPlaying) refresh();
            ui.postDelayed(this, 500);
        }
    };

    static class Page {
        String title;
        ArrayList<IpodScreenView.Row> rows = new ArrayList<>();
        int selected;
        boolean nowPlaying;
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ipod", MODE_PRIVATE);
        haptics = new Haptics(this);
        haptics.enabled = prefs.getBoolean("haptics", true);
        haptics.strength = prefs.getInt("strength", Haptics.NORMAL);
        clicks = new Clicks(this);
        clicks.enabled = prefs.getBoolean("clicks", true);
        shuffleOn = prefs.getBoolean("shuffle", false);
        theme = Theme.ALL[prefs.getInt("theme", 0) % Theme.ALL.length];

        library = new MusicLibrary(this);
        player = new Player(this);
        meta = new TrackMeta(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        player.setShuffle(shuffleOn);
        player.setListener(new Player.Listener() {
            public void onChanged() {
                refresh();
            }
        });

        Window win = getWindow();
        win.setStatusBarColor(Color.BLACK);
        win.setNavigationBarColor(Color.BLACK);

        if (getIntent() != null && getIntent().getBooleanExtra("wake", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    setShowWhenLocked(true);
                    setTurnScreenOn(true);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }

        buildUi();
        immerse();
        readVolume();
        resetToRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        readVolume();
        refresh();
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(ticker);
        ui.removeCallbacks(hideVolume);
        if (player != null) player.stopAndRelease();
        if (clicks != null) clicks.release();
        super.onDestroy();
    }

    /** Full-screen: the iPod panel owns the whole display. */
    private void immerse() {
        Window win = getWindow();
        if (win == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            win.setDecorFitsSystemWindows(false);
            WindowInsetsController c = win.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immerse();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
                    top = cut.top;
                } else {
                    top = insets.getSystemWindowInsetTop();
                }
                v.setPadding(0, top, 0, 0);
                return insets;
            }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;

        int side = (int) (w * 0.018f);
        int contentW = w - 2 * side;
        int gap = (int) (contentW * 0.030f);
        int bottomPad = (int) (h * 0.012f);

        int wheel = Math.min(contentW, (int) (h * 0.46f));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, 0, 0, bottomPad);

        screen = new IpodScreenView(this);
        screen.setTouchListener(this);
        col.addView(screen, new LinearLayout.LayoutParams(contentW, 0, 1f));

        wheelView = new ClickWheelView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(wheel, wheel);
        wlp.topMargin = gap;
        wlp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(wheelView, wlp);

        root.addView(col, new FrameLayout.LayoutParams(
                contentW,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        setContentView(root);
        rootView = root;

        wheelView.setListener(this);
        wheelView.setHaptics(haptics);
        wheelView.setTickDegrees(SPEED_DEG[prefs.getInt("speed", 1)]);
        applyTheme();
    }

    /** Pushes the current theme into the views and the window backdrop. */
    private void applyTheme() {
        if (screen != null) screen.setTheme(theme);
        if (wheelView != null) wheelView.setTheme(theme);
        if (rootView != null) {
            if (theme.glass) {
                rootView.setBackground(new Backdrop());
            } else {
                rootView.setBackgroundColor(Color.BLACK);
            }
        }
        refresh();
    }

    /** Window backdrop drawn with the same code the glass panels refract. */
    private class Backdrop extends Drawable {
        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            canvas.save();
            canvas.translate(b.left, b.top);
            theme.drawBackdrop(canvas, b.width(), b.height());
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resetToRoot();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            pop();
            if (haptics != null) haptics.click();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == REQ_AUDIO) {
            library.invalidate();
            boolean granted = hasAudioPermission();
            if (granted) {
                toast("Music access granted");
                scanMusic(false);
            } else {
                toast("Music access denied");
            }
            refresh();
        }
    }

    // ------------------------------------------------------------------ data

    private Page page() {
        return stack.get(stack.size() - 1);
    }

    private void resetToRoot() {
        stack.clear();
        stack.add(rootPage());
        refresh();
    }

    private void push(Page p) {
        p.selected = 0;
        stack.add(p);
        refresh();
    }

    private void pop() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            refresh();
        }
    }

    private void replaceTop(Page p) {
        if (stack.isEmpty()) stack.add(p);
        else stack.set(stack.size() - 1, p);
        refresh();
    }

    private void refresh() {
        if (screen == null || stack.isEmpty()) return;
        Page p = page();
        boolean isPlaying = player != null && player.playing;
        screen.setPlaying(isPlaying);
        screen.setBattery(batteryLevel());

        if (p.nowPlaying) {
            MusicLibrary.Track t = player == null ? null : player.current();
            if (t != null && t.id != metaTrackId) {
                metaTrackId = t.id;
                startMetaLoad(t);
            }
            screen.setLyricsMode(npMode == 1);
            screen.setNowPlaying(
                    t == null ? null : t.title,
                    t == null ? null : t.artist,
                    t == null ? null : t.album,
                    player == null ? 0 : player.position(),
                    player == null ? 0 : player.duration(),
                    volumePct,
                    volumeVisible);
        }
        screen.setData(p.title, p.rows, p.selected, p.nowPlaying);
    }

    /** Artwork and lyrics for the track that just became current. */
    private void startMetaLoad(MusicLibrary.Track t) {
        npMode = 0;
        screen.setLyrics(null);
        screen.setArtwork(null);

        meta.loadArt(t, new TrackMeta.ArtCb() {
            public void onArt(long id, Bitmap b) {
                if (id != metaTrackId) return;
                screen.setArtwork(b);
                currentArt = b;
                Bitmap wp = blurArt(b);
                if (wp != null) { Theme.GLASS.wallpaper = wp; Theme.ADAPTIVE.wallpaper = wp; }
                if (theme == Theme.ADAPTIVE) {
                    Theme.ADAPTIVE.applyArtworkPalette(Theme.palette(b));
                }
                applyTheme();
            }
        });
        meta.loadLyrics(t, new TrackMeta.LyricsCb() {
            public void onLyrics(long id, List<String> lines) {
                if (id != metaTrackId) return;
                screen.setLyrics(lines);
                refresh();
            }
        });
    }

    private int batteryLevel() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                int lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (lvl >= 0 && lvl <= 100) return lvl;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return -1;
    }

    // ----------------------------------------------------------------- pages

    private IpodScreenView.Row menuRow(String label, Runnable action) {
        IpodScreenView.Row r = new IpodScreenView.Row(label);
        r.chevron = true;
        r.action = action;
        return r;
    }

    private Page rootPage() {
        Page p = new Page();
        p.title = "iPod";

        p.rows.add(menuRow("Apps", new Runnable() {
            public void run() {
                push(appsPage());
            }
        }));
        p.rows.add(menuRow("Music", new Runnable() {
            public void run() {
                push(musicPage());
            }
        }));
        p.rows.add(menuRow("Shuffle Songs", new Runnable() {
            public void run() {
                shuffleSongs();
            }
        }));
        p.rows.add(menuRow("Now Playing", new Runnable() {
            public void run() {
                push(nowPlayingPage());
            }
        }));
        p.rows.add(menuRow("Settings", new Runnable() {
            public void run() {
                push(settingsPage());
            }
        }));
        p.rows.add(menuRow("About", new Runnable() {
            public void run() {
                push(aboutPage());
            }
        }));
        return p;
    }

    // ----------------------------------------------------------------- music

    private boolean hasAudioPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void requestAudioPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_AUDIO);
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_AUDIO);
            }
        } catch (Throwable t) {
            toast("Can't request permission");
        }
    }

    private Page musicPage() {
        Page p = new Page();
        p.title = "Music";

        if (!hasAudioPermission()) {
            p.rows.add(menuRow("Allow Music Access", new Runnable() {
                public void run() {
                    requestAudioPermission();
                }
            }));
            p.rows.add(menuRow("App Permissions", new Runnable() {
                public void run() {
                    openAppSettings();
                }
            }));
            return p;
        }

        final List<MusicLibrary.Track> all = library.tracks();
        if (all.isEmpty() && !autoScanned) {
            autoScanned = true;
            scanMusic(false);
        }

        p.rows.add(menuRow("Songs", new Runnable() {
            public void run() {
                push(trackListPage("Songs", all));
            }
        }));
        p.rows.add(menuRow("Artists", new Runnable() {
            public void run() {
                push(artistListPage(all));
            }
        }));
        p.rows.add(menuRow("Albums", new Runnable() {
            public void run() {
                push(albumListPage(all));
            }
        }));
        p.rows.add(menuRow("Scan for Music", new Runnable() {
            public void run() {
                scanMusic(true);
            }
        }));

        IpodScreenView.Row count = new IpodScreenView.Row("Tracks");
        count.selectable = false;
        count.value = all.isEmpty() ? "none" : String.valueOf(all.size());
        p.rows.add(count);
        return p;
    }

    private Page trackListPage(String title, final List<MusicLibrary.Track> list) {
        Page p = new Page();
        p.title = title;
        if (list.isEmpty()) {
            IpodScreenView.Row r = new IpodScreenView.Row("No songs found");
            r.selectable = false;
            p.rows.add(r);
            return p;
        }
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            MusicLibrary.Track t = list.get(i);
            IpodScreenView.Row r = new IpodScreenView.Row(t.title);
            r.value = fmtTime((int) t.durationMs);
            r.action = new Runnable() {
                public void run() {
                    playFrom(list, idx);
                }
            };
            p.rows.add(r);
        }
        return p;
    }

    private Page artistListPage(final List<MusicLibrary.Track> all) {
        Page p = new Page();
        p.title = "Artists";
        TreeMap<String, List<MusicLibrary.Track>> by =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < all.size(); i++) {
            MusicLibrary.Track t = all.get(i);
            List<MusicLibrary.Track> list = by.get(t.artist);
            if (list == null) {
                list = new ArrayList<>();
                by.put(t.artist, list);
            }
            list.add(t);
        }
        for (final String artist : by.keySet()) {
            final List<MusicLibrary.Track> list = by.get(artist);
            IpodScreenView.Row r = new IpodScreenView.Row(artist);
            r.chevron = true;
            r.value = String.valueOf(list.size());
            r.action = new Runnable() {
                public void run() {
                    push(trackListPage(artist, list));
                }
            };
            p.rows.add(r);
        }
        if (p.rows.isEmpty()) {
            IpodScreenView.Row r = new IpodScreenView.Row("No artists");
            r.selectable = false;
            p.rows.add(r);
        }
        return p;
    }

    private Page albumListPage(final List<MusicLibrary.Track> all) {
        Page p = new Page();
        p.title = "Albums";
        TreeMap<String, List<MusicLibrary.Track>> by =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < all.size(); i++) {
            MusicLibrary.Track t = all.get(i);
            List<MusicLibrary.Track> list = by.get(t.album);
            if (list == null) {
                list = new ArrayList<>();
                by.put(t.album, list);
            }
            list.add(t);
        }
        for (final String album : by.keySet()) {
            final List<MusicLibrary.Track> list = by.get(album);
            IpodScreenView.Row r = new IpodScreenView.Row(album);
            r.chevron = true;
            r.value = String.valueOf(list.size());
            r.action = new Runnable() {
                public void run() {
                    push(trackListPage(album, list));
                }
            };
            p.rows.add(r);
        }
        if (p.rows.isEmpty()) {
            IpodScreenView.Row r = new IpodScreenView.Row("No albums");
            r.selectable = false;
            p.rows.add(r);
        }
        return p;
    }

    private void scanMusic(final boolean feedback) {
        if (feedback) toast("Scanning for music\u2026");
        library.scanCommonDirs(new MusicLibrary.ScanDone() {
            public void done(int count) {
                finishScan(count, feedback);
            }
        });
        // The scanner does not call back for every path in practice (skipped or
        // failed files never report), so refresh on a timer regardless.
        ui.postDelayed(new Runnable() {
            public void run() {
                finishScan(-1, feedback);
            }
        }, 2500);
    }

    private void finishScan(final int count, final boolean feedback) {
        ui.post(new Runnable() {
            public void run() {
                library.invalidate();
                if (feedback && count >= 0) {
                    toast(count == 0 ? "No files found" : count + " files scanned");
                }
                if (!stack.isEmpty() && "Music".equals(page().title)) {
                    replaceTop(musicPage());
                } else {
                    refresh();
                }
            }
        });
    }

    private void playFrom(List<MusicLibrary.Track> list, int idx) {
        player.play(list, idx);
        push(nowPlayingPage());
    }

    // -------------------------------------------------------------- settings

    private Page settingsPage() {
        Page p = new Page();
        p.title = "Settings";

        final IpodScreenView.Row th = new IpodScreenView.Row("Theme");
        th.value = theme.name;
        th.chevron = true;
        th.action = new Runnable() {
            public void run() {
                int idx = 0;
                for (int i = 0; i < Theme.ALL.length; i++) {
                    if (Theme.ALL[i] == theme) {
                        idx = i;
                        break;
                    }
                }
                idx = (idx + 1) % Theme.ALL.length;
                theme = Theme.ALL[idx];
                if (theme == Theme.ADAPTIVE && currentArt != null) {
                    Theme.ADAPTIVE.applyArtworkPalette(Theme.palette(currentArt));
                }
                prefs.edit().putInt("theme", idx).apply();
                th.value = theme.name;
                applyTheme();
            }
        };
        p.rows.add(th);

        final IpodScreenView.Row h = new IpodScreenView.Row("Haptics");
        h.value = haptics.enabled ? "On" : "Off";
        h.action = new Runnable() {
            public void run() {
                haptics.enabled = !haptics.enabled;
                prefs.edit().putBoolean("haptics", haptics.enabled).apply();
                h.value = haptics.enabled ? "On" : "Off";
                refresh();
                haptics.click();
            }
        };
        p.rows.add(h);

        final IpodScreenView.Row cs = new IpodScreenView.Row("Click Sound");
        cs.value = clicks.enabled ? "On" : "Off";
        cs.action = new Runnable() {
            public void run() {
                clicks.enabled = !clicks.enabled;
                prefs.edit().putBoolean("clicks", clicks.enabled).apply();
                cs.value = clicks.enabled ? "On" : "Off";
                refresh();
                clicks.press();
            }
        };
        p.rows.add(cs);

        final IpodScreenView.Row sh = new IpodScreenView.Row("Shuffle");
        sh.value = shuffleOn ? "On" : "Off";
        sh.action = new Runnable() {
            public void run() {
                shuffleOn = !shuffleOn;
                player.setShuffle(shuffleOn);
                prefs.edit().putBoolean("shuffle", shuffleOn).apply();
                sh.value = shuffleOn ? "On" : "Off";
                refresh();
                clicks.press();
            }
        };
        p.rows.add(sh);
        final IpodScreenView.Row s = new IpodScreenView.Row("Haptic Strength");
        s.value = STRENGTH_NAMES[haptics.strength];
        s.action = new Runnable() {
            public void run() {
                haptics.strength = (haptics.strength + 1) % 3;
                prefs.edit().putInt("strength", haptics.strength).apply();
                s.value = STRENGTH_NAMES[haptics.strength];
                refresh();
                haptics.click();
            }
        };
        p.rows.add(s);

        final IpodScreenView.Row speed = new IpodScreenView.Row("Wheel Speed");
        speed.value = SPEED_NAMES[prefs.getInt("speed", 1)];
        speed.action = new Runnable() {
            public void run() {
                int idx = (prefs.getInt("speed", 1) + 1) % SPEED_NAMES.length;
                prefs.edit().putInt("speed", idx).apply();
                wheelView.setTickDegrees(SPEED_DEG[idx]);
                speed.value = SPEED_NAMES[idx];
                refresh();
            }
        };
        p.rows.add(speed);

        IpodScreenView.Row test = new IpodScreenView.Row("Test Haptics");
        test.chevron = true;
        test.action = new Runnable() {
            public void run() {
                testHaptics();
            }
        };
        p.rows.add(test);

        IpodScreenView.Row vib = new IpodScreenView.Row("Vibrator");
        vib.selectable = false;
        vib.value = haptics.hasVibrator()
                ? (haptics.hasAmplitudeControl() ? "Yes \u00b7 amp" : "Yes \u00b7 fixed")
                : "None";
        p.rows.add(vib);

        IpodScreenView.Row home = new IpodScreenView.Row("Default Home");
        home.value = "Set";
        home.action = new Runnable() {
            public void run() {
                openSystem(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS);
            }
        };
        p.rows.add(home);

        IpodScreenView.Row sys = new IpodScreenView.Row("System Settings");
        sys.chevron = true;
        sys.action = new Runnable() {
            public void run() {
                openSystem(Settings.ACTION_SETTINGS, null);
            }
        };
        p.rows.add(sys);

        return p;
    }

    private Page aboutPage() {
        Page p = new Page();
        p.title = "About";
        infoRow(p, "Model", Build.MODEL);
        infoRow(p, "Android", Build.VERSION.RELEASE);
        infoRow(p, "API", String.valueOf(Build.VERSION.SDK_INT));
        infoRow(p, "Tracks", hasAudioPermission()
                ? String.valueOf(library.tracks().size()) : "no access");
        infoRow(p, "Apps", String.valueOf(appsPage().rows.size()));
        infoRow(p, "Launcher", "iPod 3.9");
        return p;
    }

    private void infoRow(Page p, String label, String value) {
        IpodScreenView.Row r = new IpodScreenView.Row(label);
        r.value = value == null ? "-" : value;
        r.selectable = false;
        p.rows.add(r);
    }

    private Page nowPlayingPage() {
        Page p = new Page();
        p.title = "Now Playing";
        p.nowPlaying = true;
        return p;
    }

    // -------------------------------------------------------------- actions

    private void testHaptics() {
        for (int i = 0; i < 3; i++) {
            final int n = i;
            ui.postDelayed(new Runnable() {
                public void run() {
                    haptics.tick();
                }
            }, n * 180L);
        }
        ui.postDelayed(new Runnable() {
            public void run() {
                haptics.click();
            }
        }, 3 * 180L + 80L);
    }

    private void readVolume() {
        try {
            if (audio == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return;
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumePct = Math.round(cur * 100f / max);
        } catch (Throwable ignored) {
            // keep last known value
        }
    }

    private void adjustVolume(int delta) {
        if (audio == null) return;
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return;
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int next = Math.max(0, Math.min(max, cur + delta));
            if (next != cur) audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
            volumePct = Math.round(next * 100f / max);
        } catch (Throwable ignored) {
            // nothing to show
        }
        volumeVisible = true;
        ui.removeCallbacks(hideVolume);
        ui.postDelayed(hideVolume, 1500);
        refresh();
    }

    private void launch(String pkg, String label) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                toast("Can't open " + label);
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            toast("Can't open " + label);
        }
    }

    private void openSystem(String action, String fallback) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            if (fallback != null) {
                try {
                    Intent i = new Intent(fallback);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return;
                } catch (Throwable t2) {
                    // ignore
                }
            }
            toast("No settings app");
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("No settings app");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    static String fmtTime(int ms) {
        if (ms < 0) ms = 0;
        int total = ms / 1000;
        int s = total % 60;
        return (total / 60) + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    // ----------------------------------------------------- click wheel events

    @Override
    public boolean onRotate(int direction) {
        Page p = page();
        if (p.nowPlaying) {
            if (npMode == 1) {
                screen.scrollLyrics(direction);
            } else {
                adjustVolume(direction);
            }
            if (clicks != null) clicks.tick();
            return true;
        }
        int n = p.rows.size();
        if (n == 0) return false;
        int next = p.selected + direction;
        if (next < 0) next = 0;
        if (next > n - 1) next = n - 1;
        if (next == p.selected) return false;
        p.selected = next;
        refresh();
        if (clicks != null) clicks.tick();
        return true;
    }

    @Override
    public void onCenter() {
        if (clicks != null) clicks.press();
        Page p = page();
        if (p.nowPlaying) {
            if (npMode == 1) {
                npMode = 0;
                screen.setLyricsMode(false);
            } else if (screen.hasLyrics()) {
                npMode = 1;
                screen.setLyricsMode(true);
            } else {
                // no lyrics on this track, so the center keeps its usual job
                player.toggle();
            }
            return;
        }
        if (p.selected < 0 || p.selected >= p.rows.size()) return;
        IpodScreenView.Row r = p.rows.get(p.selected);
        if (r.selectable && r.action != null) r.action.run();
    }

    @Override
    public void onMenu() {
        if (clicks != null) clicks.press();
        if (stack.size() > 1) pop();
    }

    @Override
    public void onPrev() {
        if (clicks != null) clicks.press();
        if (page().nowPlaying) {
            player.prev();
            return;
        }
        jump(-1);
    }

    @Override
    public void onNext() {
        if (clicks != null) clicks.press();
        if (page().nowPlaying) {
            player.next();
            return;
        }
        jump(1);
    }

    @Override
    public void onPlayPause() {
        if (clicks != null) clicks.press();
        if (page().nowPlaying) player.toggle();
        else push(nowPlayingPage());
    }

    private void jump(int dir) {
        Page p = page();
        int n = p.rows.size();
        if (n == 0) return;
        int step = Math.max(1, screen.getVisibleRows() - 1);
        int next = p.selected + dir * step;
        if (next < 0) next = 0;
        if (next > n - 1) next = n - 1;
        if (next == p.selected) return;
        p.selected = next;
        refresh();
    }

    // ------------------------------------------------------------- app list

    private Page appsPage() {
        Page p = new Page();
        p.title = "Apps";
        String self = getPackageName();
        PackageManager pm = getPackageManager();

        ArrayList<String[]> apps = new ArrayList<>();
        try {
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            for (int i = 0; i < list.size(); i++) {
                ResolveInfo ri = list.get(i);
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(self)) continue;
                CharSequence lbl = ri.loadLabel(pm);
                String label = (lbl == null || lbl.length() == 0) ? pkg : lbl.toString();
                apps.add(new String[]{label, pkg});
            }
        } catch (Throwable ignored) {
            // empty list handled below
        }

        Collections.sort(apps, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                return a[0].compareToIgnoreCase(b[0]);
            }
        });

        for (int i = 0; i < apps.size(); i++) {
            final String label = apps.get(i)[0];
            final String pkg = apps.get(i)[1];
            IpodScreenView.Row r = new IpodScreenView.Row(label);
            r.action = new Runnable() {
                public void run() {
                    launch(pkg, label);
                }
            };
            p.rows.add(r);
        }
        return p;
    }
    // --------------------------------------------------------- direct touch

    @Override
    public void onRowTap(int index) {
        if (clicks != null) clicks.press();
        Page p = page();
        if (index < 0 || index >= p.rows.size()) return;
        p.selected = index;
        refresh();
        if (haptics != null) haptics.click();
        IpodScreenView.Row r = p.rows.get(index);
        if (r.selectable && r.action != null) r.action.run();
    }

    @Override
    public void onArtTap() {
        if (clicks != null) clicks.press();
        Page p = page();
        if (!p.nowPlaying) return;
        // tapping the cover always opens lyrics, even when the track has none
        npMode = npMode == 1 ? 0 : 1;
        screen.setLyricsMode(npMode == 1);
        if (haptics != null) haptics.click();
    }

    @Override
    public void onBack() {
        if (clicks != null) clicks.press();
        if (stack.size() > 1) {
            pop();
            if (haptics != null) haptics.click();
        }
    }

    @Override
    public void onScroll(int dir) {
        onRotate(dir);
    }
    /** Cheap blur: shrink hard, then scale back up with filtering. */
    private Bitmap blurArt(Bitmap src) {
        if (src == null || src.getWidth() <= 0) return null;
        try {
            // Blur at a moderate resolution first: upscaling a 26 px thumbnail
            // is what made the backdrop look blocky, not the blur itself.
            int w = 200;
            int h = Math.max(8, Math.round(200f * src.getHeight() / src.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            Bitmap blurred = boxBlur(small, 9);
            if (blurred != small) small.recycle();

            Bitmap big = Bitmap.createScaledBitmap(blurred, 720,
                    Math.max(8, Math.round(720f * h / w)), true);
            if (big != blurred) blurred.recycle();
            return big;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Three box passes approximate a gaussian well enough and cost nothing here. */
    private static Bitmap boxBlur(Bitmap src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            blurH(a, b, w, h, radius);
            blurV(b, a, w, h, radius);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void blurH(int[] in, int[] out, int w, int h, int r) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            long sa = 0, sr = 0, sg = 0, sb = 0;
            int count = 0;
            for (int x = -r; x <= r; x++) {
                int c = in[row + clampI(x, 0, w - 1)];
                sa += (c >>> 24) & 0xFF;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                count++;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((int) (sa / count) << 24) | ((int) (sr / count) << 16)
                        | ((int) (sg / count) << 8) | (int) (sb / count);
                int add = in[row + clampI(x + r + 1, 0, w - 1)];
                int sub = in[row + clampI(x - r, 0, w - 1)];
                sa += ((add >>> 24) & 0xFF) - ((sub >>> 24) & 0xFF);
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static void blurV(int[] in, int[] out, int w, int h, int r) {
        for (int x = 0; x < w; x++) {
            long sa = 0, sr = 0, sg = 0, sb = 0;
            int count = 0;
            for (int y = -r; y <= r; y++) {
                int c = in[clampI(y, 0, h - 1) * w + x];
                sa += (c >>> 24) & 0xFF;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                count++;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((int) (sa / count) << 24) | ((int) (sr / count) << 16)
                        | ((int) (sg / count) << 8) | (int) (sb / count);
                int add = in[clampI(y + r + 1, 0, h - 1) * w + x];
                int sub = in[clampI(y - r, 0, h - 1) * w + x];
                sa += ((add >>> 24) & 0xFF) - ((sub >>> 24) & 0xFF);
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }


    /** Shuffle Songs: everything in the library, randomised. */
    private void shuffleSongs() {
        if (!hasAudioPermission()) {
            toast("Allow music access first");
            push(musicPage());
            return;
        }
        List<MusicLibrary.Track> all = library.tracks();
        if (all.isEmpty()) {
            toast("No music found");
            return;
        }
        player.playShuffled(all);
        push(nowPlayingPage());
        if (haptics != null) haptics.click();
    }
}