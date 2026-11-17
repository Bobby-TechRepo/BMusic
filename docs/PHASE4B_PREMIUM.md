# Phase 4b — Premium features, part 2

Goal of this phase: finish the premium feature set without billing. As asked, no Pro gating,
paywall or Play Billing code was added; every feature is free.

> As before, Gradle can't run in this environment, so this is not built. One part *was* compiled
> and tested here: the lyrics parsers were built with the real Kotlin compiler and their 29 unit
> tests pass. The Media3 calls were checked against the Media3 1.11 sources. Run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest` before merging.

## What changed

### Lyrics

- A new **Lyrics** page on the player, between Playing and Equalizer. On tablets it is a tab in
  the right-hand pane.
- **Synced lyrics** highlight the current line and keep it a third of the way down the screen.
  - Tap a line to jump to that moment.
  - Scrolling by hand pauses the auto-follow until 3 seconds after you let go.
  - Empty timed lines show as ♪.
- **Plain lyrics** show as scrollable text.
- **Where lyrics come from**, in this order:
  1. A `.lrc` file imported for that song with **Import .lrc file**. Android doesn't let apps
     read `.lrc` files that sit next to the music, so the file is picked once and copied into the
     app's own storage. **Replace lyrics** and **Remove imported lyrics** manage the copy.
  2. Lyrics embedded in the audio file. The reader only reads the tag area, never the audio:
     - MP3: ID3v2.2–2.4 `USLT`, and `SYLT` with millisecond timing. LRC text stored in `USLT`
       still comes out synced. Unsynchronised tags are handled.
     - FLAC (including FLAC behind an ID3 tag), Ogg Vorbis and Opus: the `SYNCEDLYRICS`,
       `LYRICS` and `UNSYNCEDLYRICS` comments.
     - M4A / MP4: the `©lyr` atom, even when it sits after the audio.
- LRC support: several timestamps on one line, `[offset:±ms]`, header tags, enhanced word
  timings (removed), and UTF-8 or UTF-16 files.
- Code: `data/lyrics/` (`Lyrics.kt` with `LrcParser`, `EmbeddedLyricsReader`,
  `LyricsRepository`), `LyricsViewModel`, `LyricsView`.

### Notification and lock-screen buttons

- **Favourite** (heart) and **Shuffle** buttons next to previous / play / next, in the media
  notification, on the lock screen, and in Android Auto.
- The icons follow the state: the heart fills for a favourite song and the shuffle icon shows
  on or off. Shuffle goes through `BMusicPlayer`, so it reorders the queue exactly like the
  in-app button.
- Uses Media3 media button preferences and two custom session commands. The connection
  callback moved to `onConnectAsync`, which Media3 1.11 recommends. It keeps Media3's default
  trust rules: full control for the system, Android Auto and BMusic itself, and read-only access
  for other apps.

### Home-screen widget

- **Now playing** widget, 4×1 and horizontally resizable. It shows the artwork, title and
  artist, with previous / play-pause / next buttons. Tapping it opens BMusic.
- The playback service pushes every change: song, play state, and artwork (192 px).
- The buttons send media keys straight to the service, the same way headset buttons do.
  Play / pause are sent as separate keys, so Media3's double-tap detection never delays them.
- **Playback resumption:** if BMusic was fully closed, Play starts the service. The new
  `onPlaybackResumption` waits for the saved queue to be restored, then resumes it at the saved
  position. This also covers headsets and the system media controls.
  - Known limitation: with shuffle on, the songs after the current one are shuffled again,
    because the restored queue goes in as a new queue.

### Smart playlists

- Two new built-in lists on the Playlists tab:
  - **Rediscover**: songs played at least 3 times but not in the last 30 days. Most played come
    first, then the ones forgotten longest. Play counts are shown.
  - **Never played**: songs never counted as played, newest additions first.
- Both are capped at 100 songs and can be played and shuffled like any list. The service can
  rebuild them, so a restored queue still knows where it came from.
- Favourites, Recently added, Most played and Recently played already existed.
- Code: `SmartPlaylists`, `MediaSource.Rediscover` / `NeverPlayed` (new IDs `rediscover` and
  `never_played`).

### Playback speed is remembered

- The speed is saved with the playback position and comes back after the app is killed.
  Values from the file are clamped to 0.25–3×.

### Strings

- 13 new strings in all five languages.

## Not done, and why

- **Billing / Pro**: left out, as asked.
- **Last.fm scrobbling**: the Last.fm API needs an API key and secret registered to you, plus
  network code that can't be tested here.
  - Scrobbler apps such as Pano Scrobbler read the system's media sessions, so they should
    already scrobble BMusic.
  - A built-in scrobbler can come later using the `listen_events` history from Phase 4a.
- **Imported lyrics are not in backups yet.** The backup format is versioned, so adding them
  later is a small change.

## Tests

| Test | Covers |
| --- | --- |
| `LrcParserTest` | Timestamps (1–3 fraction digits, `mm:ss:xx`), repeats, offset ±, header tags, word timings, gaps, plain text, null cases, `indexAt`, BOM decoding |
| `EmbeddedLyricsReaderTest` | ID3v2.2/2.3/2.4 `USLT` (Latin-1, UTF-16 with BOM, UTF-8 LRC), `SYLT` (ms, frames rejected), unsynchronisation, FLAC (blocks, preference, behind ID3), Ogg Vorbis, Opus, M4A (full and QuickTime `meta`, moov after mdat), truncated and unknown files |
| `SmartPlaylistsTest` | Rediscover rules and order, undated counts, Never played order, caps, media-ID round trip |
| `PlaybackSnapshotStoreTest` (added) | Speed saved, clamped, and carried in the snapshot |

## Check on a device

1. Play an MP3 with embedded lyrics and an M4A tagged in iTunes, and open the Lyrics page.
2. Import an `.lrc` file. Check the highlight follows the song, that tapping a line seeks, and
   that scrolling pauses the follow.
3. Pull down the notification. Tap the heart and shuffle, and confirm the app updates too.
4. Add the widget. Play, pause and skip from it. Swipe BMusic away from Recents, then tap Play on
   the widget and confirm the last song resumes where it stopped.
5. Open Rediscover and Never played. They fill as play history builds up.
6. Set 1.5×, force-stop the app, reopen, and play. It should still be 1.5×.
