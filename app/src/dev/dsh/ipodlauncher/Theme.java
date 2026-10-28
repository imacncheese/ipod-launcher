package dev.dsh.ipodlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;

/**
 * Colour and material set for the whole UI.
 *
 * Themes are plain data plus a couple of shared drawing helpers so the screen
 * and the wheel render consistently. "Liquid Glass" is a material, not a
 * palette: panels are translucent, the backdrop is refracted inside them and
 * the edges carry a specular highlight.
 */
public class Theme {

    public String name = "Classic";
    public boolean glass;

    /** Panel corner radius in dp; 0 keeps the crisp rectangular iPod face. */
    public float cornerDp;

    // backdrop (also refracted inside glass panels)
    public int bgTop, bgMid, bgBottom, bgGlow;

    // panel / chrome
    public int frame;
    public int listBg;
    public int statusTop, statusBottom, statusIcon, statusText;
    public int titleTop, titleBottom, titleText;

    // list rows
    public int rowText, rowSep, chevron, scrollTrack, scrollThumb;
    public int selTop, selBottom, selText, selValue;

    // now playing / lyrics text
    public int npText, npSub, lyText, lyDim;

    // wheel
    public int wheelTop, wheelBottom, wheelRim, wheelGroove, wheelLabel, wheelLabelHot;
    public int centerTop, centerBottom;

    public int accent;

    // translucent tint layer for glass materials
    public int glassTint = 0x33FFFFFF;
    public int glassSheen = 0x45FFFFFF;
    public int rimStart = 0xE6FFFFFF;
    public int rimEnd = 0x33FFFFFF;
    public int wheelTint = 0x3DFFFFFF;
    public int glassScrim = 0x1A000000;

    /** Blurred artwork behind the glass, when a track is playing. */
    public Bitmap wallpaper;

    // ------------------------------------------------------------- palettes

    public static final Theme CLASSIC = new Theme();
    public static final Theme GRAPHITE = new Theme();
    public static final Theme GLASS = new Theme();
    public static final Theme RED = new Theme();

    /** Mutable glass palette rebuilt from the current album art. */
    public static final Theme ADAPTIVE = new Theme();

    public static final Theme[] ALL = {CLASSIC, GLASS, ADAPTIVE, GRAPHITE, RED};

    static {
        // ---- Classic silver ------------------------------------------------
        CLASSIC.name = "Classic";
        CLASSIC.glass = false;
        CLASSIC.cornerDp = 0;
        CLASSIC.bgTop = 0xFF000000;
        CLASSIC.bgMid = 0xFF000000;
        CLASSIC.bgBottom = 0xFF000000;
        CLASSIC.bgGlow = 0;
        CLASSIC.frame = 0xFF0C0C0C;
        CLASSIC.listBg = 0xFFFFFFFF;
        CLASSIC.statusTop = 0xFFF5F5F3;
        CLASSIC.statusBottom = 0xFFCDCDC9;
        CLASSIC.statusIcon = 0xFF2A2A2A;
        CLASSIC.statusText = 0xFF1A1A1A;
        CLASSIC.titleTop = 0xFFF2F2F0;
        CLASSIC.titleBottom = 0xFFB4B4AF;
        CLASSIC.titleText = 0xFF161616;
        CLASSIC.rowText = 0xFF141414;
        CLASSIC.rowSep = 0xFFD6D6D2;
        CLASSIC.chevron = 0xFF9A9A96;
        CLASSIC.scrollTrack = 0xFFEDEDEA;
        CLASSIC.scrollThumb = 0xFF9C9C98;
        CLASSIC.selTop = 0xFF8FC4F7;
        CLASSIC.selBottom = 0xFF1D5EB6;
        CLASSIC.selText = 0xFFFFFFFF;
        CLASSIC.selValue = 0xFFDCEBFF;
        CLASSIC.npText = 0xFF111111;
        CLASSIC.npSub = 0xFF6A6A6A;
        CLASSIC.lyText = 0xFF1A1A1A;
        CLASSIC.lyDim = 0xFF8A8A8A;
        CLASSIC.wheelTop = 0xFFFCFCFA;
        CLASSIC.wheelBottom = 0xFFBEBEBA;
        CLASSIC.wheelRim = 0xFF141414;
        CLASSIC.wheelGroove = 0xFFA7A7A3;
        CLASSIC.wheelLabel = 0xFF6E6E6A;
        CLASSIC.wheelLabelHot = 0xFF1A1A1A;
        CLASSIC.centerTop = 0xFFFFFFFF;
        CLASSIC.centerBottom = 0xFFD6D6D2;
        CLASSIC.accent = 0xFF4A96DD;

        // ---- Liquid Glass --------------------------------------------------
        GLASS.name = "Liquid Glass";
        GLASS.glass = true;
        GLASS.cornerDp = 26;
        GLASS.bgTop = 0xFF101826;
        GLASS.bgMid = 0xFF1C2636;
        GLASS.bgBottom = 0xFF31405A;
        GLASS.bgGlow = 0x4459C2FF;
        GLASS.frame = 0x00000000;
        GLASS.listBg = 0x00000000;
        GLASS.statusTop = 0x1FFFFFFF;
        GLASS.statusBottom = 0x0AFFFFFF;
        GLASS.statusIcon = 0xFFFFFFFF;
        GLASS.statusText = 0xFFFFFFFF;
        GLASS.titleTop = 0x2EFFFFFF;
        GLASS.titleBottom = 0x14FFFFFF;
        GLASS.titleText = 0xFFFFFFFF;
        GLASS.rowText = 0xFFFFFFFF;
        GLASS.rowSep = 0x26FFFFFF;
        GLASS.chevron = 0x99FFFFFF;
        GLASS.scrollTrack = 0x1FFFFFFF;
        GLASS.scrollThumb = 0xCCFFFFFF;
        GLASS.selTop = 0x4DFFFFFF;
        GLASS.selBottom = 0x33FFFFFF;
        GLASS.selText = 0xFFFFFFFF;
        GLASS.selValue = 0xE6FFFFFF;
        GLASS.npText = 0xFFFFFFFF;
        GLASS.npSub = 0xCCFFFFFF;
        GLASS.lyText = 0xFFFFFFFF;
        GLASS.lyDim = 0xAAFFFFFF;
        GLASS.wheelTop = 0x40FFFFFF;
        GLASS.wheelBottom = 0x14FFFFFF;
        GLASS.wheelRim = 0x33000000;
        GLASS.wheelGroove = 0x40FFFFFF;
        GLASS.wheelLabel = 0xCCFFFFFF;
        GLASS.wheelLabelHot = 0xFFFFFFFF;
        GLASS.centerTop = 0x59FFFFFF;
        GLASS.centerBottom = 0x26FFFFFF;
        GLASS.accent = 0xFF6EE7FF;
        GLASS.glassTint = 0x1FFFFFFF;
        GLASS.glassSheen = 0x38FFFFFF;
        GLASS.rimEnd = 0x2EFFFFFF;
        GLASS.glassScrim = 0x12000000;
        GLASS.wheelTint = 0x26FFFFFF;

        // ---- Graphite ------------------------------------------------------
        GRAPHITE.name = "Graphite";
        GRAPHITE.glass = false;
        GRAPHITE.cornerDp = 0;
        GRAPHITE.bgTop = 0xFF000000;
        GRAPHITE.bgMid = 0xFF000000;
        GRAPHITE.bgBottom = 0xFF000000;
        GRAPHITE.bgGlow = 0;
        GRAPHITE.frame = 0xFF050505;
        GRAPHITE.listBg = 0xFF141416;
        GRAPHITE.statusTop = 0xFF3A3A3E;
        GRAPHITE.statusBottom = 0xFF242427;
        GRAPHITE.statusIcon = 0xFFEDEDED;
        GRAPHITE.statusText = 0xFFEDEDED;
        GRAPHITE.titleTop = 0xFF3E3E44;
        GRAPHITE.titleBottom = 0xFF222226;
        GRAPHITE.titleText = 0xFFF2F2F2;
        GRAPHITE.rowText = 0xFFEDEDED;
        GRAPHITE.rowSep = 0xFF2A2A2E;
        GRAPHITE.chevron = 0xFF8A8A90;
        GRAPHITE.scrollTrack = 0xFF2A2A2E;
        GRAPHITE.scrollThumb = 0xFF6E6E76;
        GRAPHITE.selTop = 0xFF3C6FA8;
        GRAPHITE.selBottom = 0xFF1B3B63;
        GRAPHITE.selText = 0xFFFFFFFF;
        GRAPHITE.selValue = 0xFFD6E6F7;
        GRAPHITE.npText = 0xFFF2F2F2;
        GRAPHITE.npSub = 0xFFA0A0A8;
        GRAPHITE.lyText = 0xFFEDEDED;
        GRAPHITE.lyDim = 0xFF8A8A90;
        GRAPHITE.wheelTop = 0xFF4A4A50;
        GRAPHITE.wheelBottom = 0xFF1E1E22;
        GRAPHITE.wheelRim = 0xFF0A0A0A;
        GRAPHITE.wheelGroove = 0xFF151517;
        GRAPHITE.wheelLabel = 0xFFB9B9BF;
        GRAPHITE.wheelLabelHot = 0xFFF7F7F7;
        GRAPHITE.centerTop = 0xFF5A5A62;
        GRAPHITE.centerBottom = 0xFF2A2A30;
        GRAPHITE.accent = 0xFF5FA8E8;

        // ---- Product Red ---------------------------------------------------
        RED.name = "Product Red";
        RED.glass = false;
        RED.cornerDp = 0;
        RED.bgTop = 0xFF000000;
        RED.bgMid = 0xFF000000;
        RED.bgBottom = 0xFF000000;
        RED.bgGlow = 0;
        RED.frame = 0xFF0C0C0C;
        RED.listBg = 0xFFFFFFFF;
        RED.statusTop = 0xFFF5F5F3;
        RED.statusBottom = 0xFFCDCDC9;
        RED.statusIcon = 0xFF2A2A2A;
        RED.statusText = 0xFF1A1A1A;
        RED.titleTop = 0xFFF2F2F0;
        RED.titleBottom = 0xFFB4B4AF;
        RED.titleText = 0xFF161616;
        RED.rowText = 0xFF141414;
        RED.rowSep = 0xFFD6D6D2;
        RED.chevron = 0xFF9A9A96;
        RED.scrollTrack = 0xFFEDEDEA;
        RED.scrollThumb = 0xFF9C9C98;
        RED.selTop = 0xFFE8604F;
        RED.selBottom = 0xFF9E1B12;
        RED.selText = 0xFFFFFFFF;
        RED.selValue = 0xFFFFDCD6;
        RED.npText = 0xFF111111;
        RED.npSub = 0xFF6A6A6A;
        RED.lyText = 0xFF1A1A1A;
        RED.lyDim = 0xFF8A8A8A;
        RED.wheelTop = 0xFFD8402F;
        RED.wheelBottom = 0xFF8C1A12;
        RED.wheelRim = 0xFF3A0A06;
        RED.wheelGroove = 0xFF6E1410;
        RED.wheelLabel = 0xFFFFD9D3;
        RED.wheelLabelHot = 0xFFFFFFFF;
        RED.centerTop = 0xFFE85A48;
        RED.centerBottom = 0xFF95231A;
        RED.accent = 0xFFFF8A72;

        // ---- Adaptive: starts as glass, repainted from album art -----------
        copy(GLASS, ADAPTIVE);
        ADAPTIVE.name = "Adaptive";
    }

    private static void copy(Theme s, Theme t) {
        t.name = s.name;
        t.glass = s.glass;
        t.cornerDp = s.cornerDp;
        t.bgTop = s.bgTop;
        t.bgMid = s.bgMid;
        t.bgBottom = s.bgBottom;
        t.bgGlow = s.bgGlow;
        t.frame = s.frame;
        t.listBg = s.listBg;
        t.statusTop = s.statusTop;
        t.statusBottom = s.statusBottom;
        t.statusIcon = s.statusIcon;
        t.statusText = s.statusText;
        t.titleTop = s.titleTop;
        t.titleBottom = s.titleBottom;
        t.titleText = s.titleText;
        t.rowText = s.rowText;
        t.rowSep = s.rowSep;
        t.chevron = s.chevron;
        t.scrollTrack = s.scrollTrack;
        t.scrollThumb = s.scrollThumb;
        t.selTop = s.selTop;
        t.selBottom = s.selBottom;
        t.selText = s.selText;
        t.selValue = s.selValue;
        t.npText = s.npText;
        t.npSub = s.npSub;
        t.lyText = s.lyText;
        t.lyDim = s.lyDim;
        t.wheelTop = s.wheelTop;
        t.wheelBottom = s.wheelBottom;
        t.wheelRim = s.wheelRim;
        t.wheelGroove = s.wheelGroove;
        t.wheelLabel = s.wheelLabel;
        t.wheelLabelHot = s.wheelLabelHot;
        t.centerTop = s.centerTop;
        t.centerBottom = s.centerBottom;
        t.accent = s.accent;
        t.glassTint = s.glassTint;
        t.glassSheen = s.glassSheen;
        t.rimStart = s.rimStart;
        t.rimEnd = s.rimEnd;
        t.wheelTint = s.wheelTint;
    }

    // ------------------------------------------------------ adaptive palette

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Pulls a usable scheme out of album art: quantises to 4 bits per channel,
     * keeps the most common cluster as the base hue and the most vivid cluster
     * as the accent, then builds a dark backdrop from them.
     */
    public static int[] palette(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() < 4 || bmp.getHeight() < 4) return null;

        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(bmp, 32, 32, true);
        } catch (Throwable t) {
            return null;
        }
        int w = small.getWidth();
        int h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != bmp) small.recycle();

        HashMap<Integer, Integer> counts = new HashMap<>();
        for (int c : px) {
            if (((c >>> 24) & 0xFF) < 96) continue;
            int r4 = ((c >> 16) & 0xF0) >> 4;
            int g4 = ((c >> 8) & 0xF0) >> 4;
            int b4 = (c & 0xF0) >> 4;
            int key = (r4 << 8) | (g4 << 4) | b4;
            Integer n = counts.get(key);
            counts.put(key, n == null ? 1 : n + 1);
        }
        if (counts.isEmpty()) return null;

        int bestKey = 0;
        int bestCount = 0;
        int vividKey = 0;
        float vividScore = -1f;
        float[] hsv = new float[3];

        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            int key = e.getKey();
            int n = e.getValue();
            int r = ((key >> 8) & 0xF) * 17;
            int g = ((key >> 4) & 0xF) * 17;
            int b = (key & 0xF) * 17;
            Color.RGBToHSV(r, g, b, hsv);

            if (n > bestCount) {
                bestCount = n;
                bestKey = key;
            }
            float score = hsv[1] * 0.9f + Math.min(1f, n / 48f) * 0.7f + hsv[2] * 0.2f;
            if (score > vividScore) {
                vividScore = score;
                vividKey = key;
            }
        }

        float[] dh = new float[3];
        Color.RGBToHSV(((bestKey >> 8) & 0xF) * 17, ((bestKey >> 4) & 0xF) * 17,
                (bestKey & 0xF) * 17, dh);
        float[] vh = new float[3];
        Color.RGBToHSV(((vividKey >> 8) & 0xF) * 17, ((vividKey >> 4) & 0xF) * 17,
                (vividKey & 0xF) * 17, vh);

        float hue = vh[1] > 0.14f ? vh[0] : dh[0];
        float sat = Math.max(0.34f, Math.min(0.86f, vh[1]));

        int bgTop = Color.HSVToColor(new float[]{hue, sat * 0.80f, 0.12f});
        int bgMid = Color.HSVToColor(new float[]{(hue + 26f) % 360f, sat * 0.78f, 0.24f});
        int bgBottom = Color.HSVToColor(new float[]{(hue + 334f) % 360f, sat * 0.72f, 0.36f});
        int glow = Color.HSVToColor(0x66, new float[]{hue, Math.min(0.92f, sat + 0.14f), 0.97f});
        int accent = Color.HSVToColor(new float[]{hue, Math.min(0.96f, sat + 0.08f), 0.98f});

        return new int[]{bgTop, bgMid, bgBottom, glow, accent};
    }

    /** Repaints this theme (and its glass tint) from a palette() result. */
    public void applyArtworkPalette(int[] p) {
        if (p == null || p.length < 5) return;
        bgTop = p[0];
        bgMid = p[1];
        bgBottom = p[2];
        bgGlow = p[3];
        accent = p[4];

        glassTint = withAlpha(p[4], 0x22);
        glassScrim = 0x3E000000;
        wheelTint = withAlpha(p[4], 0x2E);
        glassSheen = withAlpha(0xFFFFFFFF, 0x40);
        rimStart = 0xE6FFFFFF;
        rimEnd = withAlpha(p[4], 0x70);
        selTop = withAlpha(p[4], 0x70);
        selBottom = withAlpha(p[4], 0x4A);
        centerTop = withAlpha(0xFFFFFFFF, 0x50);
        centerBottom = withAlpha(p[4], 0x33);
        scrollTrack = 0x1FFFFFFF;
        scrollThumb = withAlpha(p[4], 0xCC);
        statusTop = withAlpha(p[4], 0x22);
        titleTop = withAlpha(p[4], 0x30);
        titleBottom = withAlpha(p[4], 0x14);
    }

    // -------------------------------------------------------------- drawing

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Fills rect (0,0)-(w,h) with the theme backdrop. */
    public void drawBackdrop(Canvas c, float w, float h) {
        if (wallpaper != null && !wallpaper.isRecycled()) {
            BitmapShader sh = new BitmapShader(wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float scale = Math.max(w / wallpaper.getWidth(), h / wallpaper.getHeight());
            m.setScale(scale, scale);
            m.postTranslate((w - wallpaper.getWidth() * scale) / 2f,
                    (h - wallpaper.getHeight() * scale) / 2f);
            sh.setLocalMatrix(m);
            P.setShader(sh);
            P.setAlpha(255);
            c.drawRect(0, 0, w, h, P);
            P.setShader(new LinearGradient(0, 0, 0, h,
                    withAlpha(bgTop, 0x3C), withAlpha(bgBottom, 0x52), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, P);
            P.setShader(null);
        } else {
            P.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{bgTop, bgMid, bgBottom},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, P);
        }
        if (bgGlow != 0) {
            P.setShader(new RadialGradient(w * 0.78f, h * 0.16f, h * 0.62f,
                    bgGlow, bgGlow & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, P);
        }
        P.setShader(null);
    }

    /** Backdrop mapped onto an arbitrary rect, used inside glass panels. */
    public void drawBackdropIn(Canvas c, float left, float top, float right, float bottom) {
        P.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{bgTop, bgMid, bgBottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(left, top, right, bottom, P);
        if (bgGlow != 0) {
            P.setShader(new RadialGradient(
                    left + (right - left) * 0.78f, top + (bottom - top) * 0.16f,
                    Math.max(right - left, bottom - top) * 0.62f,
                    bgGlow, bgGlow & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(left, top, right, bottom, P);
        }
        P.setShader(null);
    }

    public boolean isGlass() {
        return glass;
    }
}
