package dev.dsh.ipodlauncher;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-track artwork and lyrics.
 *
 * Artwork comes from the embedded picture (MediaMetadataRetriever handles APIC
 * in MP3, PICTURE in FLAC and covr in MP4). Lyrics are not exposed by any
 * framework API, so the container tags are parsed here: FLAC Vorbis comments
 * and ID3v2 USLT. A ".lrc" file sitting next to the track is also tried, though
 * scoped storage blocks that on most Android 11+ devices.
 */
public class TrackMeta {

    public interface ArtCb {
        void onArt(long trackId, Bitmap bitmap);
    }

    public interface LyricsCb {
        void onLyrics(long trackId, List<String> lines);
    }

    private final Context ctx;
    private final ContentResolver cr;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private final LruCache<Long, Bitmap> artCache =
            new LruCache<Long, Bitmap>(12 * 1024 * 1024) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** Absent = not loaded yet; empty list = loaded and there are none. */
    private final Map<Long, List<String>> lyricsCache = new HashMap<>();

    public TrackMeta(Context context) {
        this.ctx = context.getApplicationContext();
        this.cr = context.getContentResolver();
    }

    // ------------------------------------------------------------------- art

    public void loadArt(final MusicLibrary.Track track, final ArtCb cb) {
        final long id = track.id;
        Bitmap cached = artCache.get(id);
        if (cached != null) {
            cb.onArt(id, cached);
            return;
        }
        pool.execute(new Runnable() {
            public void run() {
                final Bitmap bmp = decodeArt(track);
                if (bmp != null) artCache.put(id, bmp);
                ui.post(new Runnable() {
                    public void run() {
                        cb.onArt(id, bmp);
                    }
                });
            }
        });
    }

    private Bitmap decodeArt(MusicLibrary.Track track) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, track.uri);
            byte[] data = r.getEmbeddedPicture();
            if (data == null || data.length == 0) return null;
            return decodeScaled(data, 900);
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
                // nothing to do
            }
        }
    }

    private static Bitmap decodeScaled(byte[] data, int maxSide) {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;

        int sample = 1;
        while (probe.outWidth / sample > maxSide || probe.outHeight / sample > maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    // ---------------------------------------------------------------- lyrics

    public void loadLyrics(final MusicLibrary.Track track, final LyricsCb cb) {
        final long id = track.id;
        List<String> cached = lyricsCache.get(id);
        if (cached != null) {
            cb.onLyrics(id, cached);
            return;
        }
        pool.execute(new Runnable() {
            public void run() {
                List<String> lines;
                try {
                    lines = extractLyrics(track);
                } catch (Throwable t) {
                    lines = Collections.emptyList();
                }
                lyricsCache.put(id, lines);
                final List<String> out = lines;
                ui.post(new Runnable() {
                    public void run() {
                        cb.onLyrics(id, out);
                    }
                });
            }
        });
    }

    private List<String> extractLyrics(MusicLibrary.Track track) {
        String text = readSidecar(track.path);
        if (text == null) {
            text = readContainerLyrics(track);
        }
        if (text == null) return Collections.emptyList();
        return toLines(text);
    }

    private String readContainerLyrics(MusicLibrary.Track track) {
        InputStream in = null;
        try {
            in = cr.openInputStream(track.uri);
            if (in == null) return null;

            // Only the 4-byte magic here: each parser needs the stream to start
            // exactly where its own structure begins.
            byte[] magic = new byte[4];
            if (readFully(in, magic) < 4) return null;

            if (magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') {
                return flacLyrics(in);
            }
            if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') {
                // ID3v2 header is 10 bytes: 3 magic + major + minor + flags + size
                byte[] rest = new byte[6];
                if (readFully(in, rest) < 6) return null;
                return id3Lyrics(magic[3] & 0xFF, rest, in);
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
        }
    }

    /** Covers both ".lrc" and plain ".txt" next to the audio file. */
    private String readSidecar(String path) {
        if (path == null || path.length() == 0) return null;
        int dot = path.lastIndexOf('.');
        String base = dot > 0 ? path.substring(0, dot) : path;
        String[] candidates = {base + ".lrc", base + ".LRC", base + ".txt"};
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (!f.isFile() || f.length() == 0 || f.length() > 256 * 1024) continue;
            InputStream in = null;
            try {
                in = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                int n = readFully(in, buf);
                if (n > 0) {
                    return new String(buf, 0, n, StandardCharsets.UTF_8);
                }
            } catch (Throwable ignored) {
                // scoped storage will usually deny this; fall through to tags
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                        // nothing to do
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ FLAC tags

    private String flacLyrics(InputStream in) throws IOException {
        // stream is positioned right after "fLaC"
        boolean last = false;
        while (!last) {
            int h = in.read();
            if (h < 0) break;
            last = (h & 0x80) != 0;
            int type = h & 0x7F;
            int b0 = in.read();
            int b1 = in.read();
            int b2 = in.read();
            if (b2 < 0) break;
            int len = (b0 << 16) | (b1 << 8) | b2;
            byte[] block = new byte[len];
            if (readFully(in, block) < len) break;
            if (type == 4) { // VORBIS_COMMENT
                String s = vorbisLyrics(block);
                if (s != null && s.trim().length() > 0) return s;
            }
            if (type == 127) break;
        }
        return null;
    }

    private static String vorbisLyrics(byte[] b) {
        int pos = 0;
        if (b.length < 8) return null;
        int vendorLen = readU32LE(b, pos);
        pos += 4;
        if (vendorLen < 0 || pos + vendorLen > b.length) return null;
        pos += vendorLen;
        if (pos + 4 > b.length) return null;
        int count = readU32LE(b, pos);
        pos += 4;

        for (int i = 0; i < count && pos + 4 <= b.length; i++) {
            int clen = readU32LE(b, pos);
            pos += 4;
            if (clen < 0 || pos + clen > b.length) break;
            String entry = new String(b, pos, clen, StandardCharsets.UTF_8);
            pos += clen;
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String key = entry.substring(0, eq).toUpperCase(Locale.US);
            if (key.equals("LYRICS") || key.equals("UNSYNCEDLYRICS")
                    || key.equals("SYNCEDLYRICS") || key.equals("LYRIC")) {
                return entry.substring(eq + 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------- ID3 tags

    private String id3Lyrics(int version, byte[] rest, InputStream in) throws IOException {
        int flags = rest[1] & 0xFF;
        int size = ((rest[2] & 0x7F) << 21) | ((rest[3] & 0x7F) << 14)
                | ((rest[4] & 0x7F) << 7) | (rest[5] & 0x7F);
        if (size <= 0 || size > 16 * 1024 * 1024) return null;

        byte[] tag = new byte[size];
        int got = readFully(in, tag);
        if (got <= 0) return null;
        if (got < size) {
            byte[] shorter = new byte[got];
            System.arraycopy(tag, 0, shorter, 0, got);
            tag = shorter;
        }
        if ((flags & 0x80) != 0) tag = deUnsync(tag);

        int pos = 0;
        if ((flags & 0x40) != 0 && version >= 3 && tag.length >= 4) {
            int ext = version == 4
                    ? (((tag[0] & 0x7F) << 21) | ((tag[1] & 0x7F) << 14)
                        | ((tag[2] & 0x7F) << 7) | (tag[3] & 0x7F))
                    : readU32BE(tag, 0) + 4;
            pos = ext;
        }

        while (pos + 10 <= tag.length) {
            String id = new String(tag, pos, 4, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) break;
            int frameSize = version == 4
                    ? (((tag[pos + 4] & 0x7F) << 21) | ((tag[pos + 5] & 0x7F) << 14)
                        | ((tag[pos + 6] & 0x7F) << 7) | (tag[pos + 7] & 0x7F))
                    : readU32BE(tag, pos + 4);
            if (frameSize <= 0 || pos + 10 + frameSize > tag.length) break;
            if ("USLT".equals(id) || "ULT".equals(id)) {
                String text = decodeUslt(tag, pos + 10, frameSize);
                if (text != null && text.trim().length() > 0) return text;
            }
            pos += 10 + frameSize;
        }
        return null;
    }

    private static String decodeUslt(byte[] b, int off, int len) {
        int end = off + len;
        if (end > b.length || len < 4) return null;
        int enc = b[off] & 0xFF;
        int p = off + 4; // encoding + 3-byte language
        Charset cs = charsetFor(enc);

        // skip the null-terminated content descriptor
        if (enc == 1 || enc == 2) {
            while (p + 1 < end && !(b[p] == 0 && b[p + 1] == 0)) p += 2;
            p += 2;
        } else {
            while (p < end && b[p] != 0) p++;
            p++;
        }
        if (p >= end) p = off + 4;
        return new String(b, p, end - p, cs);
    }

    private static Charset charsetFor(int enc) {
        switch (enc) {
            case 1:
                return StandardCharsets.UTF_16;
            case 2:
                return StandardCharsets.UTF_16BE;
            case 3:
                return StandardCharsets.UTF_8;
            default:
                return StandardCharsets.ISO_8859_1;
        }
    }

    private static byte[] deUnsync(byte[] in) {
        byte[] out = new byte[in.length];
        int o = 0;
        for (int i = 0; i < in.length; i++) {
            out[o++] = in[i];
            if ((in[i] & 0xFF) == 0xFF && i + 1 < in.length && in[i + 1] == 0x00) {
                i++;
            }
        }
        byte[] trimmed = new byte[o];
        System.arraycopy(out, 0, trimmed, 0, o);
        return trimmed;
    }

    // ------------------------------------------------------------- utilities

    /** Drops LRC timestamps and blank runs, keeps readable lines. */
    private static List<String> toLines(String text) {
        ArrayList<String> out = new ArrayList<>();
        String[] raw = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : raw) {
            String s = line;
            // strip [mm:ss.xx] / [ar:...] style markers
            while (s.startsWith("[")) {
                int close = s.indexOf(']');
                if (close <= 0) break;
                s = s.substring(close + 1);
            }
            s = s.trim();
            if (s.length() == 0) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty()) out.add("");
            } else {
                out.add(s);
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static int readU32LE(byte[] b, int pos) {
        if (pos + 4 > b.length) return -1;
        return (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8)
                | ((b[pos + 2] & 0xFF) << 16) | ((b[pos + 3] & 0xFF) << 24);
    }

    private static int readU32BE(byte[] b, int pos) {
        if (pos + 4 > b.length) return -1;
        return ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
    }
}
