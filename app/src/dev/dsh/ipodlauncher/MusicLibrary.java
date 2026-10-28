package dev.dsh.ipodlauncher;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local music, read through MediaStore.
 *
 * If the system has not indexed a file yet (adb push, FTP copy, a file manager
 * that did not notify the scanner) nothing shows up, so scanCommonDirs() walks
 * the usual folders and asks the media scanner to index what it finds.
 */
public class MusicLibrary {

    public static class Track {
        public long id;
        public String title;
        public String artist;
        public String album;
        public long durationMs;
        public String path;
        public Uri uri;
    }

    public interface ScanDone {
        void done(int scanned);
    }

    private final Context ctx;
    private List<Track> cache;

    public MusicLibrary(Context c) {
        ctx = c.getApplicationContext();
    }

    public List<Track> tracks() {
        if (cache == null) cache = load();
        return cache;
    }

    public void invalidate() {
        cache = null;
    }

    private List<Track> load() {
        ArrayList<Track> out = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.MediaColumns.DATA,
        };
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND "
                + MediaStore.Audio.Media.DURATION + ">0";

        Cursor c = null;
        try {
            c = cr.query(base, proj, sel, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    Track t = new Track();
                    t.id = c.getLong(0);
                    t.title = clean(c.getString(1), "Untitled");
                    t.artist = clean(c.getString(2), "Unknown Artist");
                    t.album = clean(c.getString(3), "Unknown Album");
                    t.durationMs = c.getLong(4);
                    t.path = c.getString(5);
                    t.uri = ContentUris.withAppendedId(base, t.id);
                    out.add(t);
                }
            }
        } catch (Throwable ignored) {
            // keep whatever was collected; an empty library is handled by the UI
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
        }

        Collections.sort(out, new Comparator<Track>() {
            public int compare(Track a, Track b) {
                int r = a.title.compareToIgnoreCase(b.title);
                return r != 0 ? r : a.artist.compareToIgnoreCase(b.artist);
            }
        });
        return out;
    }

    private static String clean(String s, String fallback) {
        if (s == null) return fallback;
        String v = s.trim();
        if (v.length() == 0 || "<unknown>".equalsIgnoreCase(v)) return fallback;
        return v;
    }

    // ------------------------------------------------------------------ scan

    public void scanCommonDirs(final ScanDone cb) {
        ArrayList<String> paths = new ArrayList<>();
        File[] roots = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
        };
        for (int i = 0; i < roots.length; i++) {
            walk(roots[i], paths, 0);
        }

        if (paths.isEmpty()) {
            if (cb != null) cb.done(0);
            return;
        }

        final int total = paths.size();
        final int[] seen = {0};
        try {
            MediaScannerConnection.scanFile(ctx, paths.toArray(new String[0]), null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            boolean last;
                            synchronized (seen) {
                                seen[0]++;
                                last = seen[0] >= total;
                            }
                            if (last) {
                                invalidate();
                                if (cb != null) cb.done(total);
                            }
                        }
                    });
        } catch (Throwable t) {
            if (cb != null) cb.done(0);
        }
    }

    private static void walk(File dir, List<String> out, int depth) {
        if (dir == null || depth > 2 || out.size() >= 400) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (files == null) return;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                walk(f, out, depth + 1);
            } else if (isAudio(f.getName()) && f.length() > 0) {
                out.add(f.getAbsolutePath());
            }
            if (out.size() >= 400) return;
        }
    }

    private static boolean isAudio(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac")
                || n.endsWith(".ogg") || n.endsWith(".opus") || n.endsWith(".flac")
                || n.endsWith(".wav") || n.endsWith(".wma") || n.endsWith(".mp4")
                || n.endsWith(".3gp") || n.endsWith(".m4b");
    }
}
