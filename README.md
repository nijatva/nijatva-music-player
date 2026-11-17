<p align="center"><img src="assets/music.svg" width="96" alt="Music icon"></p>

<!-- Icon: slate tile (#2E3641 → #3A4350) with the white beamed note. -->

# Nijatva Music — audio player

An offline music player for the audio files already on your device. One Compose
Multiplatform codebase for Android and desktop, plus a web player that needs no
install.

App name: **Music** · Android package: `com.nijatva.music.player`

**[Download the APK](https://github.com/vasudev-work/nijatva-music-player/releases/latest)** · **[Web player](https://vasudev-work.github.io/nijatva-music-player/player/)**

| Platform | How it plays | How it finds music | Artifact |
|---|---|---|---|
| **Android** | AndroidX Media3 (ExoPlayer) | MediaStore, with real tags and cover art | `app-debug.apk` |
| **Windows** | libVLC via vlcj | `~/Music` and `~/Downloads` | `.msi` installer |
| **macOS** | libVLC via vlcj | `~/Music` and `~/Downloads` | `.dmg` installer |
| **Linux** | libVLC via vlcj | `~/Music` and `~/Downloads` | `.deb` package |
| **Web** | HTML5 `<audio>` | files you pick or drop, ID3 read in the tab | static site (`web/`) |

> **iOS is not built yet.** A runnable iOS app needs an Xcode project *and* an Apple
> Developer signing certificate. The shared Compose UI is iOS-ready; the AVPlayer
> surface and Xcode project are the next step once signing is available.

## What it does

- **Library views** — Songs, Albums, Artists, Genres, Folders, plus a live Queue.
- **Playback** — play/pause, previous/next, seek, shuffle, repeat one or repeat queue.
- **Sleep timer** — 15, 30, 45 or 60 minutes, counted down in the UI.
- **Favourites** — star a track from the now-playing screen.
- **Appearance** — System / Dark / Light / Black themes with Green, Saffron or Indigo accents.
- **Equalizer** — five presets (Flat, Bass, Vocal, Treble, Warm). Android applies them to the
  audio session through `android.media.audiofx.Equalizer`; the web player uses Web Audio
  biquad filters. The desktop build ignores them.
- **System default player** — the app registers for `audio/*` VIEW intents, so Android lists it
  under Settings → Default apps → Music and "open with" on an audio file starts playback here.
- **Radio** — paste a direct stream URL and play it. Nijatva ships no station list and
  contacts no directory service.

Nothing leaves the device: no accounts, no ads, no analytics, no Google Play Services,
and the Android app asks only for the audio-read permission it needs.

## Layout

```
composeApp/
  src/commonMain/kotlin/com/nijatva/music/player/
    App.kt                    # shell: tabs, top bar menu, bottom navigation, radio
    model/Track.kt            # Track/Album/Artist/Folder + grouping helpers
    player/Playback.kt        # expect: Playback — the one interface the UI talks to
    library/MusicLibrary.kt   # expect: MusicLibrary.scan()
    ui/Theme.kt               # 4 themes × 3 accents
    ui/LibraryScreen.kt       # songs / albums / artists / genres / folders / queue
    ui/NowPlaying.kt          # full player sheet
    ui/Settings.kt            # appearance, library options, developer card
    ui/Components.kt          # rows, pills, mini player
    util/Format.kt            # 3:07, "42 songs · 3 hr 12 min"
  src/androidMain/…           # MediaSessionService, MediaController proxy, MediaStore library
  src/desktopMain/…           # vlcj player, filesystem library, main()
web/                          # standalone HTML5 player (index.html, app.js, styles.css)
docs/                         # GitHub Pages: landing page + copy of the web player
.github/workflows/build.yml   # CI: Android APK, three desktop installers, web bundle
```

The shared code only knows a tiny surface:

```kotlin
interface AudioPlayer {
    fun open(uri: String); fun play(); fun pause(); fun seekTo(positionMs: Long)
    fun release(); fun positionMs(): Long; fun durationMs(): Long; fun isPlaying(): Boolean
    fun setEqualizer(gainsMb: List<Int>?) {}
}
```

Each platform provides `rememberAudioPlayer()` and `rememberMusicLibrary()`; the queue,
shuffle, repeat, sleep timer and the whole UI are common.

## Build

```bash
./gradlew :composeApp:assembleDebug        # Android APK
./gradlew :composeApp:run                  # run the desktop app
./gradlew :composeApp:packageDeb           # Linux   (on Linux)
./gradlew :composeApp:packageDmg           # macOS   (on macOS)
./gradlew :composeApp:packageMsi           # Windows (on Windows)
```

Installers must be produced on their own OS — that is why CI runs a three-OS matrix.
Every push to `main` publishes all artifacts under the **Actions** tab.

### Desktop needs VLC

The desktop build plays through libVLC (vlcj). Install VLC, or bundle libVLC into the
package (`nativeDistributions.appResourcesRoot`). Without libVLC the window still opens
and the library lists your files; playback is a no-op rather than a crash.

### Web

`web/` is a static page — open `index.html` in a browser or host the folder anywhere.
It is also an installable web app: Chrome and Safari offer **Add to Home screen**, and a
service worker (`sw.js`) caches the shell so it opens offline as a standalone window.
Your audio files are never cached or uploaded — only the app itself is.

- Choose files, choose a folder, or drop files (and whole folders) on the page.
- Reads ID3v2 titles, artists, albums and embedded cover art locally.
- Songs / Albums / Artists / Folders / Queue, search, shuffle, repeat, volume.
- Sleep timer, five-band equalizer, theme and accent saved in `localStorage`.
- Keyboard: <kbd>Space</kbd> play/pause, <kbd>←</kbd> <kbd>→</kbd> seek, <kbd>n</kbd>/<kbd>p</kbd>
  next/previous, <kbd>s</kbd> shuffle, <kbd>r</kbd> repeat, <kbd>m</kbd> mute, <kbd>/</kbd> search.
- Lock-screen and OS media keys work through the Media Session API.

## Playback

Android playback lives in a foreground `MediaSessionService` (`service/MusicService.kt`).
The service owns the one ExoPlayer and the MediaSession; the UI drives a `MediaController`
and reads its state back. That is what keeps music playing when the app is swiped away,
and what puts the track on the lock screen, in Bluetooth controls and in Assistant. The
queue is handed to the session as MediaItems, so ExoPlayer itself handles next, previous,
shuffle and repeat, and the notification always matches the app.

Desktop uses `DesktopPlayback` — the same `Playback` interface, backed by libVLC in
process.

Equalizer, bass boost and virtualiser are attached to the ExoPlayer audio session inside
the service, so presets survive track changes.

## Known limits

- **Favourites live in memory.** There is no database on disk, so the list resets when the app closes.
- **Desktop reads no tags.** Titles come from file names and albums from folder names; Android
  gets real tags from MediaStore.
- **Cover art** is read on Android (MediaStore album art) and in the web player (ID3 `APIC`).
  The Compose UI draws a gradient placeholder instead of loading images.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
