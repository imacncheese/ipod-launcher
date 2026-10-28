package dev.dsh.ipodlauncher;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.Random;

/**
 * The click wheel's speaker, recreated.
 *
 * Two short synthesised clicks are played through a SoundPool so overlapping
 * detents do not cut each other off. Playback follows the media stream, which
 * is how a real iPod behaves: the wheel is part of the music experience, not a
 * system UI sound. Each click gets a small random pitch offset so fast spins do
 * not sound like a machine gun.
 */
public class Clicks {

    public boolean enabled = true;

    private final SoundPool pool;
    private final int tickId;
    private final int pressId;
    private final Random rnd = new Random();

    public Clicks(Context ctx) {
        SoundPool p = null;
        int tick = 0;
        int press = 0;
        try {
            p = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            tick = load(ctx, p, "wheel_tick");
            press = load(ctx, p, "wheel_press");
        } catch (Throwable t) {
            p = null;
        }
        pool = p;
        tickId = tick;
        pressId = press;
    }

    private static int load(Context ctx, SoundPool pool, String name) {
        AssetFileDescriptor afd = null;
        try {
            // Resolved by name so the build needs no generated R class.
            int res = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (res == 0) return 0;
            afd = ctx.getResources().openRawResourceFd(res);
            int id = pool.load(afd, 1);
            return id;
        } catch (Throwable t) {
            return 0;
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
        }
    }

    /** One detent of the wheel. */
    public void tick() {
        play(tickId, 0.55f);
    }

    /** A button press. */
    public void press() {
        play(pressId, 0.7f);
    }

    private void play(int id, float volume) {
        if (!enabled || id == 0 || pool == null) return;
        try {
            float rate = 0.96f + rnd.nextFloat() * 0.08f;
            pool.play(id, volume, volume, 1, 0, rate);
        } catch (Throwable ignored) {
            // silent failure is better than a crash on the home screen
        }
    }

    public void release() {
        try {
            if (pool != null) pool.release();
        } catch (Throwable ignored) {
            // nothing to do
        }
    }
}
