# iPod Classic Launcher

An Android home-screen launcher styled after the iPod Classic, with a click
wheel that scrolls with haptic ticks and plays the music already on the device.

Package `dev.dsh.ipodlauncher` · version 3.9 · minSdk 26 · targetSdk 35

## Layout

The app hides the system bars and owns the whole display. The wheel is as wide
as the content area and the panel takes every remaining pixel, so nothing is
centred inside dead black bands and nothing can overflow off-screen. Only the
camera cutout is respected (top padding); the clock and battery move into the
iPod panel's own status bar.

## Themes

`Settings → Theme` cycles the four looks; the choice applies live and persists.

| Theme | Look |
|---|---|
| **Classic** | silver face, white screen, blue selection — the original iPod |
| **Liquid Glass** | translucent rounded panels over a violet→magenta backdrop with a refracted interior, specular rim and a glass click wheel |
| **Graphite** | dark charcoal chrome, white type, deep blue selection |
| **Product Red** | red wheel and red selection on a black body |
| **Adaptive** | the glass material, repainted from the current album art |

Liquid Glass is a material, not a palette. It is built to read like Apple's:

- **Blurred artwork backdrop.** Once a track loads, the cover is scaled to 200 px
  and given three separable box-blur passes (radius 9), then scaled up to 720 px.
  Blurring at a sane resolution is what matters: an earlier version shrank to
  26 px and relied on upscaling, which read as pixelated blocks rather than blur. The theme's own colours are then laid over it at
  only ~25 % so the artwork's colour survives; the material is deliberately
  near-colourless, the way Apple's is.
- **Refraction.** Panels paint the *window* backdrop, magnified 1.05× about
  their own centre, rather than a gradient local to the panel.
- **Edge lensing.** A 10 dp band at the rim is filled with a stronger 1.24×
  magnification, so the border bends the background like thick glass.
- **Specular rim.** A 1.4 dp stroke fades from near-white to a tint at the far
  corner, with a crisp 1.1 dp highlight along the top edge, plus an inner shadow
  along the bottom for sheet thickness and a soft drop shadow underneath.

The panel clips all of that to a 26 dp rounded path, so edges read as curved
glass rather than a tinted rectangle. The classic themes keep the crisp
rectangular iPod face at 0 dp and no material layers.

### Tinted transparency

Every shader and bitmap draw goes through `shade()`, which resets the paint
alpha. `Paint.setColor()` sets alpha as well as colour, so the translucent tint
and scrim alphas were leaking into later gradient and artwork draws — the album
cover was rendering at the scrim's ~15 % alpha under the tint instead of on top
of it. That is why art and song text looked washed out.

Both glass themes draw in this order: refracted backdrop → `glassTint` → a
darkening `glassScrim` → top specular sheen → a rim gradient that ends in the
tint colour. Liquid Glass is near-colourless (`0x1FFFFFFF` white frost) with a 7 % black
scrim; Adaptive takes its tint from the artwork and needs a 32 % scrim because
bright covers produce bright backdrops. The wheel disc gets the same tint and
scrim, and the selection rows use the tint at 70 %/45 % alpha.

### Adaptive palette

`Theme.ADAPTIVE` is a mutable glass theme repainted whenever artwork arrives:

- the bitmap is downscaled to 32×32 and quantised to 4 bits per channel;
- the most common bucket supplies the base hue, the most vivid one the accent;
- a dark backdrop is built from that hue at 12 % / 24 % / 36 % value, with a
  saturated glow behind the top-right corner;
- tint, rim end, selection, scrollbar and centre button inherit the accent at
  low alpha.

The palette is recomputed per track and when the theme is selected, so the UI
recolours as you change songs. With no artwork it stays on its glass defaults.

### Tinted transparency

Every shader and bitmap draw goes through `shade()`, which resets the paint
alpha. `Paint.setColor()` sets alpha as well as colour, so the translucent tint
and scrim alphas were leaking into later gradient and artwork draws — the album
cover was rendering at the scrim's ~15 % alpha under the tint instead of on top
of it. That is why art and song text looked washed out.

Both glass themes draw in this order: refracted backdrop → `glassTint` → a
darkening `glassScrim` → top specular sheen → a rim gradient that ends in the
tint colour. Liquid Glass is near-colourless (`0x1FFFFFFF` white frost) with a 7 % black
scrim; Adaptive takes its tint from the artwork and needs a 32 % scrim because
bright covers produce bright backdrops. The wheel disc gets the same tint and
scrim, and the selection rows use the tint at 70 %/45 % alpha.

### Adaptive palette

`Theme.ADAPTIVE` is a mutable glass theme repainted whenever artwork arrives:

- the bitmap is downscaled to 32×32 and quantised to 4 bits per channel;
- the most common bucket supplies the base hue, the most vivid one the accent;
- a dark backdrop is built from that hue at 12 % / 24 % / 36 % value, with a
  saturated glow behind the top-right corner;
- tint, rim end, selection, scrollbar and centre button inherit the accent at
  low alpha.

The palette is recomputed per track and when the theme is selected, so the UI
recolours as you change songs. With no artwork it stays on its glass defaults.

## Controls

| Wheel action      | In a menu                    | In Now Playing        |
|-------------------|------------------------------|-----------------------|
| Drag around ring  | Scroll one item per detent   | Change volume, or scroll lyrics |
| Center button     | Select / open                | Cycle artwork ↔ lyrics (play/pause when the track has no lyrics) |
| MENU (top)        | Back one level               | Back                  |
| ►►| (right)       | Page down (a screenful)      | Next track            |
| |◄◄ (left)       | Page up                      | Previous / restart    |
| ►|| (bottom)      | Open Now Playing             | Play / pause          |

The panel is touch-enabled as well, so the wheel is optional:

| Touch                  | Result                                        |
|------------------------|-----------------------------------------------|
| Tap a list row         | Select and open it                            |
| Drag a list up/down    | Scroll one row per half row-height            |
| Tap the album cover    | Toggle lyrics                                 |
| Tap the title bar      | Back one level                                |
| Tap the status strip   | Nothing                                       |

Every tap fires the same haptic click as the wheel, and row taps reuse the
row's own action, so touch and wheel can never diverge.

The panel is touch-enabled as well, so the wheel is optional:

| Touch                  | Result                                        |
|------------------------|-----------------------------------------------|
| Tap a list row         | Select and open it                            |
| Drag a list up/down    | Scroll one row per half row-height            |
| Tap the album cover    | Toggle lyrics                                 |
| Tap the title bar      | Back one level                                |
| Tap the status strip   | Nothing                                       |

Every tap fires the same haptic click as the wheel, and row taps reuse the
row's own action, so touch and wheel can never diverge.

## Music

Tracks come from MediaStore (`IS_MUSIC != 0`), so anything the system has
indexed is playable. Reading them needs `READ_MEDIA_AUDIO` (API 33+) or
`READ_EXTERNAL_STORAGE` (≤32); the app asks the first time **Music** is opened
and shows an *App Permissions* shortcut if access was refused.

On first open, if the library is empty, the app also walks `Music/`,
`Downloads/`, `Podcasts/` and `Ringtones/` (two levels deep, 400 files) and asks
`MediaScannerConnection` to index what it finds. This matters in practice: on
this device the music sat in a nested `Music/Music/` folder that MediaStore had
never catalogued, so nothing would have appeared without it. The scan picked up
95 tracks. **Music → Scan for Music** repeats it on demand.

Browsing: **Songs**, **Artists**, **Albums** (each with counts), plus a *Tracks*
readout on the Music page.

**Shuffle.** The root menu has **Shuffle Songs** (the whole library in random
order from the top), and **Settings → Shuffle** is a persistent mode: when on,
picking any track from a list keeps that track first and randomises everything
after it, so `next` walks a shuffled queue rather than alphabetical order.

Playback is a `MediaPlayer` with a queue and a requesting audio focus
(`USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`), so other apps duck or hand over
properly. Now Playing shows real title/artist/album, elapsed and remaining
time, and a live progress bar that refreshes twice a second.

Known gap: playback runs in the activity rather than a foreground service, so a
memory-pressured system may stop music while another app is in front.

## Album art

The embedded picture is read with `MediaMetadataRetriever.getEmbeddedPicture()`
(APIC in MP3, PICTURE in FLAC, covr in MP4), downsampled to 900 px and drawn
centre-cropped into a rounded square through a `BitmapShader`. The square is full-bleed inside the panel: 4 dp of padding, and both the
title block and the progress row are drawn **on** the cover under a bottom scrim
instead of reserving height below it. On a 1080x2400 panel that is ~1000 px
square — about 90 % of the panel width, up from 580 px originally.

Its edges are not a hard rectangle: the bitmap is composited into a layer whose
border is erased with a 7.5 % gradient on all four sides, and behind it sits the
same cover blurred, faded radially. So the sharp cover dissolves into its own
colours instead of into the lighter glass, which previously showed as a pale
halo around the art. Tapping the cover opens lyrics. The square is full-bleed inside the panel: 4 dp of padding, and both the
title block and the progress row are drawn **on** the cover under a bottom scrim
instead of reserving height below it. On a 1080x2400 panel that is ~1000 px
square — about 90 % of the panel width, up from 580 px originally.

Its edges are not a hard rectangle: the bitmap is composited into a layer whose
border is erased with a 7.5 % gradient on all four sides, and behind it sits the
same cover blurred, faded radially. So the sharp cover dissolves into its own
colours instead of into the lighter glass, which previously showed as a pale
halo around the art. Tapping the cover opens lyrics. Decoding happens
on a background pool and results are cached in an `LruCache` (12 MB), so
skipping tracks does not re-decode. The grey ♫ placeholder only appears for
files with no embedded picture — all 95 tracks on this device carry 1500×1500
artwork, so it is not seen in practice.

## Lyrics

No framework API exposes lyrics, so `TrackMeta` parses the container itself:

- **FLAC** — Vorbis comments, keys `LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`
- **MP3** — ID3v2 `USLT` frames (ISO-8859-1 / UTF-16 / UTF-16BE / UTF-8,
  including unsynchronisation)
- **Sidecar** — `<name>.lrc` or `.txt` next to the track, with `[mm:ss]` stamps
  stripped. Usually blocked by scoped storage on Android 11+, so it is best
  effort.

Press the center button in Now Playing: with lyrics present it cycles
artwork ↔ lyrics; with none it falls back to play/pause and the lyrics pane
says "No lyrics for this track". In the lyrics pane the wheel scrolls the text
instead of changing volume. Lines are word-wrapped to the panel width.

M4A/M4P (`©lyr`) is not parsed yet.

## Haptics

`Settings → Test Haptics` fires three detents then a press.

This device reports `hasAmplitudeControl() == false`, so the amplitude argument
is ignored by the platform and duration is the only lever. Pulses therefore
lengthen on fixed-amplitude hardware:

| Strength | tick (amplitude-capable) | tick (fixed amplitude) |
|----------|--------------------------|------------------------|
| Soft     | 10 ms @ 110              | 18 ms @ full           |
| Normal   | 14 ms @ 180              | 24 ms @ full           |
| Strong   | 18 ms @ 255              | 32 ms @ full           |

Raw one-shot waveforms are used rather than `VibrationEffect.EFFECT_TICK`
because predefined effects are scaled by the system haptic-intensity slider and
are silent when it sits at zero.

## Click sound

The wheel makes the iPod's mechanical click: one tick per detent, a slightly
lower click for button presses. Both are synthesised by
`make-click-sounds.mjs` into `res/raw/wheel_tick.wav` (836 bytes) and
`wheel_press.wav` (1,984 bytes) — a noise transient mixed with a damped sine,
so no sample library is needed.

They play through a `SoundPool` (6 streams) so overlapping detents during a fast
spin do not cut each other off, at 55 % / 70 % volume, with ±4 % random pitch so
a spin does not sound like a machine gun. The stream is `USAGE_MEDIA` —
sonification attached to the media session, the way an iPod's wheel belongs to
the music rather than to system UI. Toggle under **Settings → Click Sound**;
wheel drags, touch scrolling, row taps and every hardware button share it.

## Wheel input hardening

Synthetic or erratic touches (a swipe crossing the hub, a coordinate jump) used
to accumulate huge angles and fire dozens of phantom detents — enough to skip
tracks or fling the list. The wheel now stops tracking when the finger enters
the hub, drops per-event deltas above 60°, and only treats a release as a
button press when the finger both stayed put (< 42 dp) and barely rotated.
Button presses are also debounced by 220 ms, because a duplicated press used to
open two menu levels at once.

## Settings

Theme, haptics on/off, click sound on/off, shuffle, haptic strength, wheel speed (11°/17°/24° per detent),
test haptics, a read-only vibrator capability row, default-home picker, system
settings.

## Set as home screen

Press Home and pick **iPod → Always**, or use **Settings → Default Home**.
The APK declares `CATEGORY_HOME`.

## Build

No Android Studio required — `build.ps1` drives javac, d8, aapt2, zipalign and
apksigner directly against a bare toolchain:

```
pwsh -File build.ps1
```

Toolchain (fetched by `download-deps.mjs` into `toolchain/`): Temurin JDK
17.0.20.1, Android build-tools 35.0.0, Android platform 35 (`android.jar`).

Signing key: `signing/ipod.keystore` (alias `ipod`, store/key password
`ipodpass`). Keep it — the same key must sign future updates or Android refuses
the install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## Debug hook

`am start -n dev.dsh.ipodlauncher/.MainActivity --ez wake true` turns the panel
on and shows over the keyguard, so the UI can be screenshotted while the phone
is idle. Normal launches ignore it.
