package dev.dsh.ipodlauncher;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Haptics for the click wheel.
 *
 * Deliberately uses raw one-shot waveforms rather than the OEM's predefined
 * EFFECT_TICK: predefined effects are scaled by the system haptic-intensity
 * slider and are silent when that slider sits at zero, which is the usual
 * reason a wheel "feels dead". A one-shot with an explicit duration and
 * amplitude always plays, provided the device has a vibrator at all.
 *
 * Durations are well above the ~10 ms spin-up of a linear actuator; the old
 * 8 ms pulses were frequently imperceptible.
 */
public class Haptics {

    public static final int SOFT = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    public boolean enabled = true;
    public int strength = NORMAL;

    private final Vibrator vib;
    private final boolean hasVibrator;
    private final boolean hasAmplitude;

    public Haptics(Context ctx) {
        Context app = ctx.getApplicationContext();

        Vibrator v = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm =
                        (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) v = vm.getDefaultVibrator();
            }
        } catch (Throwable ignored) {
            // fall back to the legacy service below
        }
        if (v == null) {
            try {
                v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            } catch (Throwable ignored) {
                v = null;
            }
        }
        vib = v;

        boolean hv = false;
        boolean ha = false;
        if (vib != null) {
            try {
                hv = vib.hasVibrator();
            } catch (Throwable ignored) {
                hv = false;
            }
            try {
                ha = vib.hasAmplitudeControl();
            } catch (Throwable ignored) {
                ha = false;
            }
        }
        hasVibrator = hv;
        hasAmplitude = ha;
    }

    public boolean hasVibrator() {
        return hasVibrator;
    }

    public boolean hasAmplitudeControl() {
        return hasAmplitude;
    }

    /**
     * One detent of the wheel.
     *
     * Devices without amplitude control ignore the amplitude argument entirely,
     * so duration is the only lever there: pulses are made longer than on an
     * amplitude-capable actuator, where a short sharp pulse already reads well.
     */
    public void tick() {
        switch (strength) {
            case SOFT:
                pulse(hasAmplitude ? 10 : 18, hasAmplitude ? 110 : 255);
                break;
            case STRONG:
                pulse(hasAmplitude ? 18 : 32, 255);
                break;
            default:
                pulse(hasAmplitude ? 14 : 24, hasAmplitude ? 180 : 255);
                break;
        }
    }

    /** A button press: firmer and slightly longer. */
    public void click() {
        switch (strength) {
            case SOFT:
                pulse(hasAmplitude ? 18 : 28, hasAmplitude ? 150 : 255);
                break;
            case STRONG:
                pulse(hasAmplitude ? 30 : 48, 255);
                break;
            default:
                pulse(hasAmplitude ? 24 : 36, hasAmplitude ? 220 : 255);
                break;
        }
    }

    private void pulse(int ms, int amplitude) {
        one(ms, amplitude);
    }

    private void one(int ms, int amplitude) {
        if (!enabled || !hasVibrator || vib == null) return;
        try {
            int amp = hasAmplitude ? amplitude : VibrationEffect.DEFAULT_AMPLITUDE;
            vib.vibrate(VibrationEffect.createOneShot(ms, amp));
        } catch (Throwable t) {
            try {
                vib.vibrate(ms);
            } catch (Throwable ignored) {
                // nothing else to try
            }
        }
    }
}
