package dev.dsh.ipodlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * The iPod Classic click wheel.
 *
 * The whole wheel is one touch surface: dragging around the ring scrolls the
 * list one item per detent (with a haptic tick per item actually moved), and a
 * tap in one of the four quadrants acts as MENU / previous / next / play-pause.
 */
public class ClickWheelView extends View {

    public interface Listener {
        /** @return true when the selection actually moved. */
        boolean onRotate(int direction);
        void onCenter();
        void onMenu();
        void onPrev();
        void onNext();
        void onPlayPause();
    }

    private Listener listener;
    private Haptics haptics;

    /** Degrees of finger travel per list item. Lower = faster. */
    private double tickDeg = 18.0;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float d;
    private float cx, cy, outerR, centerR, labelR;

    private boolean centerPressed;
    private int activeZone = -1;
    private boolean tracking;
    private float lastAngle;
    private float downX, downY;
    private double accum, totalRot;
    private long lastActionAt;

    private static final int ZONE_MENU = 0;
    private static final int ZONE_NEXT = 1;
    private static final int ZONE_PREV = 2;
    private static final int ZONE_PLAY = 3;

    public ClickWheelView(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setHaptics(Haptics h) {
        haptics = h;
    }

    public void setTickDegrees(double deg) {
        tickDeg = deg;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        outerR = Math.min(w, h) / 2f - 1.5f * d;
        centerR = outerR * 0.375f;
        labelR = (centerR + outerR) / 2f;
    }

    // ---------------------------------------------------------------- geometry

    private float angleAt(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
    }

    private float distAt(float x, float y) {
        return (float) Math.hypot(x - cx, y - cy);
    }

    private static float norm(float a) {
        while (a <= -180f) a += 360f;
        while (a > 180f) a -= 360f;
        return a;
    }

    private int zoneAt(float angle) {
        float a = norm(angle);
        if (a >= -135f && a < -45f) return ZONE_MENU;
        if (a >= -45f && a < 45f) return ZONE_NEXT;
        if (a >= 45f && a < 135f) return ZONE_PLAY;
        return ZONE_PREV;
    }

    // ------------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        float dist = distAt(x, y);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (dist <= centerR * 1.10f) {
                    centerPressed = true;
                    tracking = false;
                    activeZone = -1;
                    invalidate();
                    return true;
                }
                if (dist <= outerR * 1.15f) {
                    centerPressed = false;
                    tracking = true;
                    downX = x;
                    downY = y;
                    lastAngle = angleAt(x, y);
                    activeZone = zoneAt(lastAngle);
                    accum = 0;
                    totalRot = 0;
                    invalidate();
                    return true;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (centerPressed) {
                    if (dist > centerR * 1.7f) {
                        centerPressed = false;
                        invalidate();
                    }
                    return true;
                }
                if (!tracking) return true;
                // Finger wandered onto the hub: the angle is meaningless there,
                // so stop tracking instead of accumulating garbage.
                if (dist < centerR * 0.85f) {
                    tracking = false;
                    activeZone = -1;
                    invalidate();
                    return true;
                }
                float ang = angleAt(x, y);
                float delta = norm(ang - lastAngle);
                // A jump this large is a re-seat or synthetic input, not a
                // finger movement; resync rather than spinning the list.
                if (Math.abs(delta) > 60f) {
                    lastAngle = ang;
                    return true;
                }
                lastAngle = ang;
                totalRot += delta;
                accum += delta;
                while (accum >= tickDeg) {
                    accum -= tickDeg;
                    fireRotate(1);
                }
                while (accum <= -tickDeg) {
                    accum += tickDeg;
                    fireRotate(-1);
                }
                int z = zoneAt(ang);
                if (z != activeZone) {
                    activeZone = z;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (centerPressed) {
                    centerPressed = false;
                    activeZone = -1;
                    if (dist <= centerR * 1.25f && allowAction()) {
                        if (haptics != null) haptics.click();
                        if (listener != null) listener.onCenter();
                    }
                    invalidate();
                    return true;
                }
                if (tracking) {
                    tracking = false;
                    boolean still = Math.abs(totalRot) < tickDeg * 0.75;
                    float travel = (float) Math.hypot(x - downX, y - downY);
                    boolean tapped = still && travel < 42 * d;
                    int zUp = activeZone;
                    activeZone = -1;
                    if (tapped && zUp >= 0 && allowAction()) {
                        if (haptics != null) haptics.click();
                        dispatchZone(zUp);
                    }
                    invalidate();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                centerPressed = false;
                tracking = false;
                activeZone = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /**
     * A button press can arrive twice (duplicate injection, a stray second UP);
     * without this a single press opened two menu levels. Real fingers cannot
     * meaningfully press twice inside 220 ms either.
     */
    private boolean allowAction() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastActionAt < 220L) return false;
        lastActionAt = now;
        return true;
    }

    private void fireRotate(int dir) {
        boolean moved = listener != null && listener.onRotate(dir);
        if (moved && haptics != null) haptics.tick();
    }

    private void dispatchZone(int zone) {
        if (listener == null) return;
        switch (zone) {
            case ZONE_MENU:
                listener.onMenu();
                break;
            case ZONE_NEXT:
                listener.onNext();
                break;
            case ZONE_PREV:
                listener.onPrev();
                break;
            case ZONE_PLAY:
                listener.onPlayPause();
                break;
        }
    }

    // ----------------------------------------------------------------- drawing

    private Theme theme = Theme.CLASSIC;

    public void setTheme(Theme t) {
        theme = t == null ? Theme.CLASSIC : t;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);

        // outer rim / contact shadow
        p.setColor(theme.wheelRim);
        c.drawCircle(cx, cy, outerR + 2.0f * d, p);

        // disc body
        shade(new LinearGradient(cx, cy - outerR, cx, cy + outerR,
                theme.wheelTop, theme.wheelBottom, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, outerR, p);
        p.setShader(null);

        if (theme.glass) {
            // frost sheen: brighter across the top of the disc
            shade(new LinearGradient(cx, cy - outerR, cx, cy + outerR,
                    theme.wheelTint, 0x0AFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outerR, p);
            p.setShader(null);
        } else {
            // recessed shading toward the center button
            shade(new RadialGradient(cx, cy - outerR * 0.35f, outerR * 1.30f,
                    0x00FFFFFF, 0x28000000, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outerR, p);
            p.setShader(null);
        }

        // groove between ring and center button
        p.setColor(theme.wheelGroove);
        c.drawCircle(cx, cy, centerR + 1.8f * d, p);

        // center button
        shade(new RadialGradient(cx, cy - centerR * 0.45f, centerR * 2.1f,
                theme.centerTop, theme.centerBottom, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, centerR, p);
        p.setShader(null);

        // specular rim for glass
        if (theme.glass) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.0f * d);
            shade(new LinearGradient(cx - outerR, cy - outerR, cx + outerR, cy + outerR,
                    theme.rimStart, theme.rimEnd, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, outerR - 1.0f * d, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.0f * d);
        p.setColor(theme.glass ? 0x40FFFFFF : 0x22000000);
        c.drawCircle(cx, cy, centerR, p);
        p.setStyle(Paint.Style.FILL);

        if (centerPressed) {
            p.setColor(theme.glass ? 0x33FFFFFF : 0x24000000);
            c.drawCircle(cx, cy, centerR, p);
        }

        drawLabels(c);
    }

    private void drawLabels(Canvas c) {
        int idle = theme.wheelLabel;
        int hot = theme.wheelLabelHot;

        drawArcText(c, "MENU", -90f, activeZone == ZONE_MENU, idle, hot);
        drawGlyph(c, 0f, ZONE_NEXT, activeZone == ZONE_NEXT, idle, hot);
        drawGlyph(c, 90f, ZONE_PLAY, activeZone == ZONE_PLAY, idle, hot);
        drawGlyph(c, 180f, ZONE_PREV, activeZone == ZONE_PREV, idle, hot);
    }

    /** Characters laid out along the wheel, tops pointing outward. */
    private void drawArcText(Canvas c, String text, float centerAngle, boolean active, int idle, int hot) {
        float size = 12f * d;
        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextSize(size);
        tp.setColor(active ? hot : idle);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setStyle(Paint.Style.FILL);

        float[] widths = new float[text.length()];
        tp.getTextWidths(text, widths);
        float total = 0f;
        for (float w : widths) total += w;

        double radius = labelR;
        double totalDeg = Math.toDegrees(total / radius);
        double ang = centerAngle - totalDeg / 2.0;

        for (int i = 0; i < text.length(); i++) {
            double charDeg = ang + Math.toDegrees((widths[i] / 2.0) / radius);
            double rad = Math.toRadians(charDeg);
            float px = (float) (cx + Math.cos(rad) * radius);
            float py = (float) (cy + Math.sin(rad) * radius);
            c.save();
            c.translate(px, py);
            c.rotate((float) (charDeg + 90.0));
            c.drawText(text, i, i + 1, -widths[i] / 2f, size * 0.36f, tp);
            c.restore();
            ang += Math.toDegrees(widths[i] / radius);
        }
    }

    /** Vector glyphs rather than font symbols, so they always render. */
    private void drawGlyph(Canvas c, float angleDeg, int zone, boolean active, int idle, int hot) {
        double rad = Math.toRadians(angleDeg);
        float px = (float) (cx + Math.cos(rad) * labelR);
        float py = (float) (cy + Math.sin(rad) * labelR);

        c.save();
        c.translate(px, py);
        c.rotate(angleDeg + 90f);

        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(active ? hot : idle);

        float h = 7.4f * d;      // half height
        float tw = 5.6f * d;     // triangle width
        float barW = 2.0f * d;   // bar width
        float gap = 1.2f * d;

        path.reset();

        if (zone == ZONE_NEXT) {
            // >>|
            float total = tw * 2 + gap * 2 + barW;
            float x = -total / 2f;
            tri(path, x, h, tw);
            tri(path, x + tw + gap, h, tw);
            c.drawPath(path, p);
            c.drawRect(x + tw * 2 + gap * 2, -h, x + total, h, p);
        } else if (zone == ZONE_PREV) {
            // |<<
            float total = tw * 2 + gap * 2 + barW;
            float x = -total / 2f;
            c.drawRect(x, -h, x + barW, h, p);
            triLeft(path, x + barW + gap, h, tw);
            triLeft(path, x + barW + gap + tw + gap, h, tw);
            c.drawPath(path, p);
        } else {
            // >||
            float total = tw + gap * 2 + barW * 2;
            float x = -total / 2f;
            tri(path, x, h, tw);
            c.drawPath(path, p);
            c.drawRect(x + tw + gap, -h, x + tw + gap + barW, h, p);
            c.drawRect(x + tw + gap + barW + gap, -h, x + total, h, p);
        }

        c.restore();
    }

    private void tri(Path path, float x, float h, float w) {
        path.moveTo(x, -h);
        path.lineTo(x, h);
        path.lineTo(x + w, 0);
        path.close();
    }

    private void triLeft(Path path, float x, float h, float w) {
        path.moveTo(x + w, -h);
        path.lineTo(x + w, h);
        path.lineTo(x, 0);
        path.close();
    }

    /** Shader draws must not inherit the alpha left behind by setColor(). */
    private void shade(Shader s) {
        p.setShader(s);
        p.setAlpha(255);
    }
}