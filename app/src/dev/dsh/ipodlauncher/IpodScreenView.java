package dev.dsh.ipodlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The iPod Classic screen: status bar, title bar, and either a menu list or a
 * Now Playing pane.
 */
public class IpodScreenView extends View {

    public static class Row {
        public String label;
        public String value;
        public boolean chevron;
        public boolean selectable = true;
        public Runnable action;

        public Row(String label) {
            this.label = label;
        }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final SimpleDateFormat fmt = new SimpleDateFormat("h:mm", Locale.getDefault());

    private float d = 2f;
    private float statusH, titleH, rowH;
    private int left, top, right, bottom;
    private int listTop, listBottom;
    private int visibleRows = 5;

    private String title = "iPod";
    private List<Row> rows = new ArrayList<>();
    private int selected;
    private int scrollTop;
    private boolean nowPlaying;
    private boolean playing;
    private int battery = -1;

    private String npTitle;
    private String npArtist;
    private String npAlbum;
    private int npPos;
    private int npDur;
    private int volPct = 50;
    private boolean volVisible;

    private Bitmap artwork;
    private List<String> rawLyrics;
    private List<String> lyricLines;
    private int lyricTop;
    private boolean lyricsMode;

    public IpodScreenView(Context ctx) {
        super(ctx);
        setClickable(true);
        d = ctx.getResources().getDisplayMetrics().density;
        statusH = 21 * d;
        titleH = 29 * d;
        rowH = 38 * d;
    }

    // -------------------------------------------------------------- public API

    public void setData(String title, List<Row> rows, int selected, boolean nowPlaying) {
        this.title = title == null ? "iPod" : title;
        this.rows = rows == null ? new ArrayList<Row>() : rows;
        this.nowPlaying = nowPlaying;
        this.selected = selected;
        int n = this.rows.size();
        if (this.selected < 0) this.selected = 0;
        if (this.selected > n - 1) this.selected = Math.max(0, n - 1);
        invalidate();
    }

    public void setBattery(int level) {
        battery = level;
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        invalidate();
    }

    /** Real playback state for the Now Playing pane. */
    public void setNowPlaying(String title, String artist, String album,
                              int posMs, int durMs, int volumePct, boolean volumeVisible) {
        npTitle = title;
        npArtist = artist;
        npAlbum = album;
        npPos = posMs;
        npDur = durMs;
        volPct = volumePct;
        volVisible = volumeVisible;
        invalidate();
    }

    public void setArtwork(Bitmap b) {
        artwork = b;
        invalidate();
    }

    public boolean hasLyrics() {
        return rawLyrics != null && !rawLyrics.isEmpty();
    }

    public void setLyrics(List<String> lines) {
        rawLyrics = lines;
        lyricTop = 0;
        lyricLines = wrap(lines);
        invalidate();
    }

    public void setLyricsMode(boolean on) {
        if (lyricsMode != on) {
            lyricsMode = on;
            lyricTop = 0;
        }
        invalidate();
    }

    public void scrollLyrics(int delta) {
        if (lyricLines == null) return;
        int visible = lyricVisibleCount();
        int max = Math.max(0, lyricLines.size() - visible);
        int next = lyricTop + delta;
        if (next < 0) next = 0;
        if (next > max) next = max;
        if (next == lyricTop) return;
        lyricTop = next;
        invalidate();
    }

    private int lyricVisibleCount() {
        float areaH = (listBottom - 12 * d) - (listTop + 12 * d);
        float lh = 18 * d;
        return Math.max(1, (int) (areaH / lh));
    }

    /** Greedy wrap so long lyric lines do not run off the panel. */
    private List<String> wrap(List<String> lines) {
        if (lines == null) return null;
        float maxW = right - left - 30 * d;
        if (maxW <= 0) return new ArrayList<>(lines);

        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(12.5f * d);

        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() == 0) {
                out.add("");
                continue;
            }
            String rest = line;
            int guard = 0;
            while (rest.length() > 0 && guard++ < 12) {
                int n = tp.breakText(rest, true, maxW, null);
                if (n <= 0) break;
                // break on a word boundary where possible instead of mid-word
                if (n < rest.length()) {
                    int sp = rest.lastIndexOf(' ', n);
                    if (sp > 0) n = sp;
                }
                String part = rest.substring(0, n).trim();
                if (part.length() > 0) out.add(part);
                rest = rest.substring(n).trim();
            }
        }
        return out;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getRowCount() {
        return rows.size();
    }

    // ---------------------------------------------------------------- measure

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        d = getResources().getDisplayMetrics().density;
        statusH = 21 * d;
        titleH = 29 * d;
        rowH = 38 * d;
        float f = 1.5f * d;
        left = (int) f;
        top = (int) f;
        right = (int) (w - f);
        bottom = (int) (h - f);
        listTop = (int) (top + statusH + titleH);
        listBottom = bottom;
        visibleRows = Math.max(1, (int) ((listBottom - listTop) / rowH));
        if (rawLyrics != null) lyricLines = wrap(rawLyrics);
    }

    // ------------------------------------------------------------------- draw

    private Theme theme = Theme.CLASSIC;

    public void setTheme(Theme t) {
        theme = t == null ? Theme.CLASSIC : t;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = theme.cornerDp * d;

        if (theme.glass) {
            drawGlassPanel(c, r);
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(theme.frame);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            p.setColor(theme.listBg);
            c.drawRect(left, top, right, bottom, p);
        }

        drawStatus(c);
        drawTitleBar(c);
        if (nowPlaying) {
            drawNowPlaying(c);
        } else {
            drawList(c);
            postInvalidateDelayed(30000);
        }
    }

    /** Translucent panel: refracted backdrop, frost, specular rim, soft shadow. */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int[] loc = new int[2];
        getLocationInWindow(loc);
        windowX = loc[0];
        windowY = loc[1];
        android.view.View root = getRootView();
        windowW = root.getWidth();
        windowH = root.getHeight();
    }

    /** Paints the window backdrop magnified about the panel centre. */
    private void drawWindow(Canvas c, float zoom) {
        c.save();
        c.scale(zoom, zoom, (left + right) / 2f, (top + bottom) / 2f);
        c.translate(-windowX, -windowY);
        theme.drawBackdrop(c, windowW, windowH);
        c.restore();
        p.setShader(null);
        p.setAlpha(255);
    }

    /** Translucent panel: refracted backdrop, edge lensing, frost, specular rim. */
    private void drawGlassPanel(Canvas c, float r) {
        rf.set(left, top, right, bottom);
        path.reset();
        path.addRoundRect(rf, r, r, Path.Direction.CW);

        // soft drop shadow: the material floats above the content
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0x2E000000);
        c.drawRoundRect(left + d, top + 9 * d, right + d, bottom + 11 * d, r, r, p);

        c.save();
        c.clipPath(path);

        // 1. refracted backdrop
        drawWindow(c, 1.05f);

        // 2. lensing band: stronger magnification only near the rim, which is
        //    what makes the edge look like curved glass rather than a tint
        Path inner = new Path();
        float inset = 10 * d;
        inner.addRoundRect(new RectF(left + inset, top + inset, right - inset, bottom - inset),
                Math.max(0f, r - inset), Math.max(0f, r - inset), Path.Direction.CW);
        Path ring = new Path();
        ring.op(path, inner, Path.Op.DIFFERENCE);
        c.save();
        c.clipPath(ring);
        drawWindow(c, 1.24f);
        p.setColor(0x12000000);
        c.drawRect(rf, p);
        c.restore();

        // 3. frost + tint
        p.setColor(theme.glassTint);
        c.drawRect(rf, p);
        if ((theme.glassScrim >>> 24) != 0) {
            p.setColor(theme.glassScrim);
            c.drawRect(rf, p);
        }

        // 4. specular sheen across the top
        shade(new LinearGradient(0, top, 0, top + (bottom - top) * 0.42f,
                theme.glassSheen, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(rf, p);
        p.setShader(null);

        // 5. inner shadow along the bottom edge gives the sheet thickness
        shade(new LinearGradient(0, bottom - 28 * d, 0, bottom,
                0x00000000, 0x38000000, Shader.TileMode.CLAMP));
        c.drawRect(rf, p);
        p.setShader(null);

        c.restore();

        // 6. rim: bright where light lands, fading towards the opposite corner
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * d);
        shade(new LinearGradient(left, top, right, bottom,
                theme.rimStart, theme.rimEnd, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf, r, r, p);
        p.setShader(null);

        // crisp top highlight, like a lit bevel
        p.setStrokeWidth(1.1f * d);
        p.setColor(0xB3FFFFFF);
        c.drawLine(left + r * 0.55f, top + 0.6f * d, right - r * 0.55f, top + 0.6f * d, p);
        p.setStyle(Paint.Style.FILL);
    }
    private void drawStatus(Canvas c) {
        if (theme.glass) {
            p.setShader(null);
            p.setColor(theme.statusTop);
            c.drawRect(left, top, right, top + statusH, p);
        } else {
            shade(new LinearGradient(0, top, 0, top + statusH,
                    theme.statusTop, theme.statusBottom, Shader.TileMode.CLAMP));
            c.drawRect(left, top, right, top + statusH, p);
            p.setShader(null);
        }

        p.setColor(theme.glass ? 0x26FFFFFF : 0xFF9C9C98);
        c.drawRect(left, top + statusH - 1f, right, top + statusH, p);

        // play / pause indicator on the left
        float icx = left + 11 * d;
        float icy = top + statusH / 2f;
        p.setColor(theme.statusIcon);
        path.reset();
        if (playing) {
            c.drawRect(icx - 4.2f * d, icy - 4.2f * d, icx - 1.4f * d, icy + 4.2f * d, p);
            c.drawRect(icx + 1.4f * d, icy - 4.2f * d, icx + 4.2f * d, icy + 4.2f * d, p);
        } else {
            path.moveTo(icx - 3.6f * d, icy - 4.4f * d);
            path.lineTo(icx - 3.6f * d, icy + 4.4f * d);
            path.lineTo(icx + 4.2f * d, icy);
            path.close();
            c.drawPath(path, p);
        }

        // battery on the right
        float bw = 21 * d;
        float bh = 9.5f * d;
        float bx = right - 5 * d - bw;
        float by = top + (statusH - bh) / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f * d);
        p.setColor(theme.statusIcon);
        rf.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rf, 1.8f * d, 1.8f * d, p);
        c.drawRect(bx + bw + 0.4f * d, by + bh * 0.32f, bx + bw + 2.2f * d, by + bh * 0.68f, p);
        p.setStyle(Paint.Style.FILL);
        if (battery >= 0) {
            p.setColor(battery <= 15 ? 0xFFB02020 : theme.statusIcon);
            float fillW = (bw - 2.4f * d) * Math.min(100, Math.max(0, battery)) / 100f;
            if (fillW > 0.6f * d) {
                rf.set(bx + 1.2f * d, by + 1.2f * d, bx + 1.2f * d + fillW, by + bh - 1.2f * d);
                c.drawRect(rf, p);
            }
        }

        // clock, left of the battery
        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextSize(9.8f * d);
        tp.setColor(theme.statusText);
        tp.setTextAlign(Paint.Align.RIGHT);
        c.drawText(fmt.format(new Date()), bx - 5 * d, top + statusH * 0.5f + 3.4f * d, tp);
    }

    private void drawTitleBar(Canvas c) {
        float ty = top + statusH;
        if (theme.glass) {
            shade(new LinearGradient(0, ty, 0, ty + titleH,
                    theme.titleTop, theme.titleBottom, Shader.TileMode.CLAMP));
            c.drawRect(left, ty, right, ty + titleH, p);
            p.setShader(null);
        } else {
            shade(new LinearGradient(0, ty, 0, ty + titleH,
                    theme.titleTop, theme.titleBottom, Shader.TileMode.CLAMP));
            c.drawRect(left, ty, right, ty + titleH, p);
            p.setShader(null);
        }

        p.setColor(theme.glass ? 0x26FFFFFF : 0xFF8C8C88);
        c.drawRect(left, ty + titleH - 1f, right, ty + titleH, p);

        tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        tp.setTextSize(16f * d);
        tp.setColor(theme.titleText);
        tp.setTextAlign(Paint.Align.CENTER);
        CharSequence t = TextUtils.ellipsize(title, tp, right - left - 16 * d, TextUtils.TruncateAt.END);
        c.drawText(t.toString(), (left + right) / 2f, ty + titleH * 0.5f + 5.2f * d, tp);
    }

    private void drawList(Canvas c) {
        int n = rows.size();
        if (n == 0) {
            tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tp.setTextSize(13 * d);
            tp.setColor(theme.lyDim);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText("Empty", (left + right) / 2f, (listTop + listBottom) / 2f, tp);
            return;
        }

        if (selected < 0) selected = 0;
        if (selected > n - 1) selected = n - 1;
        if (selected < scrollTop) scrollTop = selected;
        if (selected >= scrollTop + visibleRows) scrollTop = selected - visibleRows + 1;
        int maxTop = Math.max(0, n - visibleRows);
        if (scrollTop > maxTop) scrollTop = maxTop;
        if (scrollTop < 0) scrollTop = 0;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollTop + i;
            if (idx >= n) break;
            Row r = rows.get(idx);
            float ry = listTop + i * rowH;
            boolean sel = idx == selected;

            if (sel) {
                shade(new LinearGradient(0, ry, 0, ry + rowH,
                        theme.selTop, theme.selBottom, Shader.TileMode.CLAMP));
                c.drawRect(left, ry, right, ry + rowH, p);
                p.setShader(null);
                p.setColor(theme.glass ? 0x26FFFFFF : 0x30FFFFFF);
                c.drawRect(left, ry, right, ry + rowH * 0.42f, p);
            } else {
                if (!theme.glass) {
                    p.setColor(theme.listBg);
                    c.drawRect(left, ry, right, ry + rowH, p);
                }
                p.setColor(theme.rowSep);
                c.drawRect(left, ry + rowH - 1f, right, ry + rowH, p);
            }

            float tx = left + 9 * d;
            float baseline = ry + rowH * 0.5f + 5.2f * d;

            tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            tp.setTextSize(15.5f * d);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setColor(sel ? theme.selText : theme.rowText);

            float reserve = 9 * d;
            if (r.value != null) reserve += 68 * d;
            if (r.chevron) reserve += 14 * d;
            float maxW = (right - tx) - reserve;
            if (maxW < 12 * d) maxW = 12 * d;

            CharSequence label = TextUtils.ellipsize(r.label, tp, maxW, TextUtils.TruncateAt.END);
            c.drawText(label.toString(), tx, baseline, tp);

            if (r.value != null) {
                tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                tp.setTextSize(11.5f * d);
                tp.setColor(sel ? theme.selValue : theme.npSub);
                tp.setTextAlign(Paint.Align.RIGHT);
                float vx = right - 8 * d - (r.chevron ? 13 * d : 0);
                CharSequence value = TextUtils.ellipsize(r.value, tp, 62 * d, TextUtils.TruncateAt.END);
                c.drawText(value.toString(), vx, baseline, tp);
            }

            if (r.chevron) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f * d);
                p.setColor(sel ? theme.selText : theme.chevron);
                float ax = right - 9 * d;
                float ay = ry + rowH * 0.5f;
                float s = 3.4f * d;
                path.reset();
                path.moveTo(ax - s, ay - s);
                path.lineTo(ax, ay);
                path.lineTo(ax - s, ay + s);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        // scrollbar, only when the list overflows
        if (n > visibleRows) {
            float trackX = right - 3.4f * d;
            float t0 = listTop + 2 * d;
            float t1 = listBottom - 2 * d;
            p.setColor(theme.scrollTrack);
            c.drawRect(trackX - 1.4f * d, t0, trackX + 1.4f * d, t1, p);
            float frac = visibleRows / (float) n;
            float thumb = Math.max(12 * d, (t1 - t0) * frac);
            int maxScroll = n - visibleRows;
            float t = maxScroll <= 0 ? 0f : scrollTop / (float) maxScroll;
            float y0 = t0 + (t1 - t0 - thumb) * t;
            p.setColor(theme.scrollThumb);
            c.drawRect(trackX - 1.4f * d, y0, trackX + 1.4f * d, y0 + thumb, p);
        }
    }

    private void drawNowPlaying(Canvas c) {
        if (lyricsMode) {
            drawLyrics(c);
            return;
        }
        float pad = 4 * d;
        boolean has = npTitle != null;
        float area = listBottom - listTop - 2 * pad;
        float maxW = right - left - 2 * pad;
        float progressBlock = 28 * d;        // progress row sits just under the cover
        float art = Math.min(maxW, area - progressBlock);
        float ax = (left + right) / 2f - art / 2f;
        float ay = listTop + pad;

        rf.set(ax, ay, ax + art, ay + art);
        artRect.set(rf);
        // a blurred copy of the same cover sits behind it, so the feathered
        // edge dissolves into its own colours rather than into the glass
        if (theme.wallpaper != null && !theme.wallpaper.isRecycled()) {
            float halo = art * 1.18f;
            float hx = ax - (halo - art) / 2f;
            float hy = ay - (halo - art) / 2f;
            rf.set(hx, hy, hx + halo, hy + halo);
            int hl = c.saveLayer(rf, null);
            BitmapShader hs = new BitmapShader(theme.wallpaper,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix hm = new Matrix();
            float hsc = Math.max(halo / theme.wallpaper.getWidth(),
                    halo / theme.wallpaper.getHeight());
            hm.setScale(hsc, hsc);
            hm.postTranslate(hx, hy);
            hs.setLocalMatrix(hm);
            shade(hs);
            p.setAlpha(0x9A);
            c.drawRect(rf, p);
            p.setAlpha(255);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            shade(new RadialGradient(rf.centerX(), rf.centerY(), halo * 0.58f,
                    new int[]{0x00000000, 0xFF000000}, new float[]{0.55f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawRect(rf, p);
            p.setShader(null);
            p.setXfermode(null);
            c.restoreToCount(hl);
            rf.set(ax, ay, ax + art, ay + art);
        }

        int artLayer = c.saveLayer(ax, ay, ax + art, ay + art, null);

        if (artwork != null && artwork.getWidth() > 0 && artwork.getHeight() > 0) {
            BitmapShader sh = new BitmapShader(artwork,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float scale = Math.max(art / artwork.getWidth(), art / artwork.getHeight());
            m.setScale(scale, scale);
            m.postTranslate(ax + (art - artwork.getWidth() * scale) / 2f,
                    ay + (art - artwork.getHeight() * scale) / 2f);
            sh.setLocalMatrix(m);
            shade(sh);
            c.drawRoundRect(rf, 4 * d, 4 * d, p);
            p.setShader(null);
        } else {
            shade(new LinearGradient(ax, ay, ax + art, ay + art,
                    theme.glass ? 0x40FFFFFF : 0xFFA8A8A5,
                    theme.glass ? 0x14FFFFFF : 0xFF57575A, Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, 4 * d, 4 * d, p);
            p.setShader(null);

            tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            tp.setTextSize(art * 0.30f);
            tp.setColor(0x66FFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText("\u266B", ax + art / 2f, ay + art * 0.52f, tp);
        }

        // Title block sits on the cover under a scrim, so the art can fill the panel.
        shade(new LinearGradient(0, ay + art * 0.52f, 0, ay + art,
                0x00000000, 0xC8000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf, 4 * d, 4 * d, p);
        p.setShader(null);

        float textW = art - 26 * d;
        float cx = ax + art / 2f;
        float baseY = ay + art - 12 * d;

        tp.setShadowLayer(3 * d, 0, 1 * d, 0xB0000000);
        tp.setTextAlign(Paint.Align.CENTER);

        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(10.5f * d);
        tp.setColor(0xCCFFFFFF);
        CharSequence line3 = TextUtils.ellipsize(has ? npAlbum : "", tp, textW,
                TextUtils.TruncateAt.END);
        c.drawText(line3.toString(), cx, baseY, tp);

        tp.setTextSize(12.5f * d);
        tp.setColor(0xE6FFFFFF);
        CharSequence line2 = TextUtils.ellipsize(has ? npArtist : "Nothing playing", tp, textW,
                TextUtils.TruncateAt.END);
        c.drawText(line2.toString(), cx, baseY - 18 * d, tp);

        tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        tp.setTextSize(16.5f * d);
        tp.setColor(0xFFFFFFFF);
        CharSequence line1 = TextUtils.ellipsize(has ? npTitle : "No Music", tp, textW,
                TextUtils.TruncateAt.END);
        c.drawText(line1.toString(), cx, baseY - 39 * d, tp);
        tp.clearShadowLayer();
        // feather the border so the cover dissolves into the blurred backdrop
        float f = art * 0.075f;
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        shade(new LinearGradient(0, ay, 0, ay + f, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(ax, ay, ax + art, ay + f, p);
        shade(new LinearGradient(0, ay + art, 0, ay + art - f, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(ax, ay + art - f, ax + art, ay + art, p);
        shade(new LinearGradient(ax, 0, ax + f, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(ax, ay, ax + f, ay + art, p);
        shade(new LinearGradient(ax + art, 0, ax + art - f, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(ax + art - f, ay, ax + art, ay + art, p);
        p.setShader(null);
        p.setXfermode(null);
        c.restoreToCount(artLayer);

        float bx = left + 13 * d;
        float bw = right - left - 26 * d;
        float barY = ay + art + 22 * d;
        float labelY = barY - 9 * d;

        tp.setTextSize(9.5f * d);
        if (volVisible) {
            float frac = Math.max(0, Math.min(100, volPct)) / 100f;
            p.setColor(theme.glass ? 0x33FFFFFF : 0xFFD8D8D4);
            rf.set(bx, barY, bx + bw, barY + 4 * d);
            c.drawRoundRect(rf, 2 * d, 2 * d, p);
            p.setColor(theme.accent);
            rf.set(bx, barY, bx + bw * frac, barY + 4 * d);
            c.drawRoundRect(rf, 2 * d, 2 * d, p);

            tp.setColor(theme.npSub);
            tp.setTextAlign(Paint.Align.LEFT);
            c.drawText("Volume", bx, labelY, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(volPct + "%", bx + bw, labelY, tp);
        } else {
            float frac = npDur > 0 ? Math.min(1f, npPos / (float) npDur) : 0f;
            p.setColor(theme.glass ? 0x33FFFFFF : 0xFFD8D8D4);
            rf.set(bx, barY, bx + bw, barY + 4 * d);
            c.drawRoundRect(rf, 2 * d, 2 * d, p);
            if (frac > 0.001f) {
                p.setColor(theme.accent);
                rf.set(bx, barY, bx + bw * frac, barY + 4 * d);
                c.drawRoundRect(rf, 2 * d, 2 * d, p);
            }

            tp.setColor(theme.npSub);
            tp.setTextAlign(Paint.Align.LEFT);
            c.drawText(fmtTime(npPos), bx, labelY, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("-" + fmtTime(Math.max(0, npDur - npPos)), bx + bw, labelY, tp);
        }
    }

    private void drawLyrics(Canvas c) {
        float pad = 12 * d;
        float topY = listTop + pad;
        float bottomY = listBottom - pad;

        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(12.5f * d);
        tp.setTextAlign(Paint.Align.CENTER);

        if (lyricLines == null || lyricLines.isEmpty()) {
            tp.setColor(theme.lyDim);
            c.drawText("No lyrics for this track", (left + right) / 2f,
                    (topY + bottomY) / 2f, tp);
            tp.setTextSize(10 * d);
            tp.setColor(theme.glass ? 0x88FFFFFF : 0xFFAFAFAF);
            c.drawText("Center returns to artwork", (left + right) / 2f,
                    (topY + bottomY) / 2f + 22 * d, tp);
            return;
        }

        float lh = 18 * d;
        int visible = lyricVisibleCount();
        int max = Math.max(0, lyricLines.size() - visible);
        if (lyricTop > max) lyricTop = max;
        if (lyricTop < 0) lyricTop = 0;

        tp.setColor(theme.lyText);
        c.save();
        c.clipRect(left, topY, right, bottomY);
        float y = topY + lh;
        for (int i = lyricTop; i < lyricLines.size() && i <= lyricTop + visible; i++) {
            c.drawText(lyricLines.get(i), (left + right) / 2f, y, tp);
            y += lh;
        }
        c.restore();

        if (lyricLines.size() > visible) {
            float trackX = right - 3.4f * d;
            float t0 = topY;
            float t1 = bottomY;
            float frac = visible / (float) lyricLines.size();
            float thumb = Math.max(14 * d, (t1 - t0) * frac);
            float t = max <= 0 ? 0f : lyricTop / (float) max;
            p.setColor(theme.scrollTrack);
            c.drawRect(trackX - 1.2f * d, t0, trackX + 1.2f * d, t1, p);
            p.setColor(theme.scrollThumb);
            c.drawRect(trackX - 1.2f * d, t0 + (t1 - t0 - thumb) * t,
                    trackX + 1.2f * d, t0 + (t1 - t0 - thumb) * t + thumb, p);
        }
    }

    private static String fmtTime(int ms) {
        if (ms < 0) ms = 0;
        int total = ms / 1000;
        int s = total % 60;
        return (total / 60) + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    /** Shader draws must not inherit the alpha left behind by setColor(). */
    private void shade(Shader s) {
        p.setShader(s);
        p.setAlpha(255);
    }
    // ------------------------------------------------------------ direct touch

    public interface TouchListener {
        void onRowTap(int index);
        void onArtTap();
        void onBack();
        void onScroll(int dir);
    }

    private TouchListener touchListener;
    private final RectF artRect = new RectF();
    private int windowX, windowY, windowW, windowH;
    private float touchDownX, touchDownY;
    private boolean dragging;

    public void setTouchListener(TouchListener l) {
        touchListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (touchListener == null) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = e.getX();
                touchDownY = e.getY();
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dy = e.getY() - touchDownY;
                if (!dragging && Math.abs(dy) > 12 * d) dragging = true;
                if (dragging && !nowPlaying) {
                    float per = rowH * 0.55f;
                    if (per > 0 && Math.abs(dy) >= per) {
                        int steps = (int) (dy / per);
                        for (int i = 0; i < Math.abs(steps); i++) {
                            touchListener.onScroll(dy > 0 ? 1 : -1);
                        }
                        touchDownY += steps * per;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                float dx = e.getX() - touchDownX;
                float dy = e.getY() - touchDownY;
                if (!dragging && Math.abs(dx) < 14 * d && Math.abs(dy) < 14 * d) {
                    handleTap(e.getX(), e.getY());
                }
                dragging = false;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return true;
    }

    private void handleTap(float x, float y) {
        if (y < top + statusH) return;                  // status strip: no-op
        if (y < listTop) {                              // title bar: back
            touchListener.onBack();
            return;
        }
        if (nowPlaying) {
            if (lyricsMode || artRect.contains(x, y)) touchListener.onArtTap();
            return;
        }
        int row = rowAt(y);
        if (row >= 0) touchListener.onRowTap(row);
    }

    private int rowAt(float y) {
        if (rows.isEmpty() || y < listTop || y > listBottom) return -1;
        int idx = scrollTop + (int) ((y - listTop) / rowH);
        return (idx >= 0 && idx < rows.size()) ? idx : -1;
    }}