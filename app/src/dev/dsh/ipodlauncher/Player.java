package dev.dsh.ipodlauncher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MediaPlayer wrapper with a simple queue, like one iPod playlist. */
public class Player {

    public interface Listener {
        void onChanged();
    }

    private final Context ctx;
    private final AudioManager am;
    private AudioFocusRequest focusRequest;

    private MediaPlayer mp;
    private List<MusicLibrary.Track> queue = new ArrayList<>();
    private int index = -1;
    private boolean prepared;

    public boolean playing;
    private boolean shuffle;
    private Listener listener;

    public Player(Context c) {
        ctx = c.getApplicationContext();
        am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private void fire() {
        if (listener != null) listener.onChanged();
    }

    public MusicLibrary.Track current() {
        if (index < 0 || index >= queue.size()) return null;
        return queue.get(index);
    }

    public int position() {
        try {
            if (mp != null && prepared) return mp.getCurrentPosition();
        } catch (Throwable ignored) {
            // fall through
        }
        return 0;
    }

    public int duration() {
        try {
            if (mp != null && prepared) {
                int d = mp.getDuration();
                if (d > 0) return d;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        MusicLibrary.Track t = current();
        return t == null ? 0 : (int) t.durationMs;
    }

    public void setShuffle(boolean on) {
        shuffle = on;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void play(List<MusicLibrary.Track> list, int idx) {
        if (shuffle) {
            // keep the picked track first, shuffle everything after it
            queue = new ArrayList<>(list);
            if (idx >= 0 && idx < queue.size()) {
                MusicLibrary.Track first = queue.remove(idx);
                Collections.shuffle(queue);
                queue.add(0, first);
            } else {
                Collections.shuffle(queue);
            }
            index = 0;
        } else {
            queue = new ArrayList<>(list);
            index = idx;
        }
        open();
        fire();
    }

    /** Shuffle Songs: the whole library in random order from the top. */
    public void playShuffled(List<MusicLibrary.Track> list) {
        if (list == null || list.isEmpty()) return;
        queue = new ArrayList<>(list);
        Collections.shuffle(queue);
        index = 0;
        open();
        fire();
    }

    public void toggle() {
        if (current() == null) return;
        if (mp == null || !prepared) {
            open();
            return;
        }
        try {
            if (mp.isPlaying()) {
                mp.pause();
                playing = false;
            } else {
                mp.start();
                playing = true;
                requestFocus();
            }
        } catch (Throwable ignored) {
            // leave state as-is
        }
        fire();
    }

    public void next() {
        if (queue.isEmpty()) return;
        index = (index + 1) % queue.size();
        open();
        fire();
    }

    /** Like the iPod: restart the track if we are past the first few seconds. */
    public void prev() {
        if (queue.isEmpty()) return;
        if (position() > 3000) {
            seek(0);
            return;
        }
        index = (index - 1 + queue.size()) % queue.size();
        open();
        fire();
    }

    public void seek(int ms) {
        try {
            if (mp != null && prepared) mp.seekTo(ms);
        } catch (Throwable ignored) {
            // nothing to do
        }
        fire();
    }

    public void pause() {
        try {
            if (mp != null && prepared && mp.isPlaying()) {
                mp.pause();
                playing = false;
                fire();
            }
        } catch (Throwable ignored) {
            // nothing to do
        }
    }

    private void open() {
        release();
        MusicLibrary.Track t = current();
        if (t == null) return;
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(ctx, t.uri);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer m) {
                    prepared = true;
                    m.start();
                    playing = true;
                    requestFocus();
                    fire();
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    next();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer m, int what, int extra) {
                    prepared = false;
                    playing = false;
                    fire();
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Throwable e) {
            release();
            playing = false;
        }
    }

    private void requestFocus() {
        if (am == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            public void onAudioFocusChange(int change) {
                                if (change == AudioManager.AUDIOFOCUS_LOSS) pause();
                            }
                        })
                        .build();
            }
            am.requestAudioFocus(focusRequest);
        } catch (Throwable ignored) {
            // playing without focus is better than not playing
        }
    }

    public void release() {
        try {
            if (mp != null) {
                mp.reset();
                mp.release();
            }
        } catch (Throwable ignored) {
            // nothing to do
        }
        mp = null;
        prepared = false;
    }

    public void stopAndRelease() {
        release();
        playing = false;
        queue.clear();
        index = -1;
        if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            try {
                am.abandonAudioFocusRequest(focusRequest);
            } catch (Throwable ignored) {
                // nothing to do
            }
        }
    }
}
