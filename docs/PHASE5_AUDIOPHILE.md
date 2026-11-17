# Phase 5 — Volume levelling, M3U playlists, Android Auto

What was still open from the earlier phase notes, apart from billing (still left out).

> **Not built yet.** Gradle can't run here, so run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest` (and `connectedDebugAndroidTest` on a
> device).
>
> **Tested here.** The tag reader, ReplayGain maths and M3U code have no Android parts, so they
> were compiled with the real Kotlin compiler and their 54 tests run: all pass. The Media3
> constants used were checked against the Media3 1.11 sources.

## 1. Volume levelling (ReplayGain)

**What you see**
- **Settings → Volume levelling (ReplayGain)** has three choices:
  - **Off** (the default).
  - **Per song**: every song plays at the same loudness; best for shuffle.
  - **Per album**: whole albums play at the same loudness, keeping each album's own dynamics.
- If a song lacks the value for the chosen mode, the other value is used. Songs without tags
  play unchanged.

**Which tags are read**
- MP3: ID3 `TXXX:REPLAYGAIN_*`.
- FLAC, Ogg Vorbis and Opus: the `REPLAYGAIN_*` comments.
- Opus: `R128_TRACK_GAIN` / `R128_ALBUM_GAIN`, converted from the −23 LUFS reference to
  ReplayGain's −18 LUFS.
- M4A: iTunes `----:com.apple.iTunes:replaygain_*` atoms.

**How it is applied**
- The level becomes the playback service's *base volume*. "Sound fades" and the sleep-timer
  fade multiply it, so all three work together.
- The next song's tags are read ahead of time, so its level is ready the moment it starts. The
  values are cached for the session.
- **Limit:** the player can only turn the volume *down*. Songs that ReplayGain would make
  louder play at full volume. The peak value stops any song from clipping. Most modern music
  carries negative gain, so this covers most of a library.
- Code: `data/tags/` (`EmbeddedTagReader`, `ReplayGain`, `AudioTagRepository`) and
  `PlaybackService.updateReplayGain`.
- `EmbeddedLyricsReader` now reads through the same tag reader, so lyrics and gain come from one
  parser.

## 2. M3U playlists

**Export**
- In a playlist's menu, **Export as M3U** saves an `.m3u8` file wherever you choose.
- Each song is written as an `#EXTINF` line (length, artist and title) plus its full file path.
- To get the path, songs now carry their file path (`Song.path`, from MediaStore).

**Import**
- **Playlists tab → Import M3U** turns a file into a new playlist. The playlist is named after
  the file; if that name is taken, " (2)", " (3)" and so on are added.
- The importer understands:
  - extended and plain M3U;
  - Windows paths;
  - `file://` URIs;
  - UTF-8 or UTF-16 files.
  - Internet-radio lines are skipped.
- **How songs are found**, in order. Playlists from a PC or another phone almost never share
  absolute paths, so it tries:
  1. the exact path;
  2. the same file name, picking the closest folder structure when several songs share a name;
  3. the `#EXTINF` artist and title, preferring a length within 3 seconds.
- The message says how many songs were added and how many aren't on the device. If none are
  found, no empty playlist is created.
- Code: `data/playlists/` (`M3u`, `M3uMatcher`, `PlaylistTransfer`) and
  `PlaylistTransferViewModel`.

## 3. Android Auto: Playlists folder

- A **Playlists** folder now sits next to Songs, Albums and Artists.
- It lists Favourites, Recently added, Most played, Recently played, Rediscover, Never played,
  then your own playlists.
- Each list can be opened, or played directly. Tapping a song plays the list from that song.

## 4. System "resume" card

- Media3's `MediaButtonReceiver` is now declared. After a reboot, or once BMusic was fully
  closed, the system media controls can offer to resume.
- Headset buttons can also start playback.
- Both go through the `onPlaybackResumption` added in Phase 4b, which restores the saved queue
  without reshuffling.

## Gapless playback

- No change was needed. ExoPlayer already plays MP3 (LAME/Xing headers), AAC/M4A (iTunes
  gapless data), FLAC and Opus without gaps, because the queue is one continuous timeline.
- A true crossfade would need two players mixed together. It is left out because it would
  conflict with the single-player queue design. "Sound fades as songs end" covers the common
  case.

## Tests

| Test | Covers |
| --- | --- |
| `ReplayGainTest` | Tag text parsing, R128 conversion, junk values, dB to volume, the full-volume limit, peak clipping, mode fallbacks |
| `EmbeddedTagReaderTest` | ReplayGain from MP3 TXXX, FLAC, FLAC behind ID3 (merge), Opus R128, M4A freeform atoms, lyrics and gain together |
| `M3uTest` | Extended and plain M3U, BOM, CRLF, Windows paths, `file://`, streams skipped, EXTINF attributes, formatting round trip, matching (path, file name, closest folders, title/artist with length), unique names |
| `PlaylistTransferTest` | Export order and paths (with a deleted song), import naming, matching and counts, nothing found, non-playlist file |
| `EmbeddedLyricsReaderTest`, `LrcParserTest` | Unchanged, still passing on the new reader |

## Check on a device

1. Turn on **Per song** and play a loud modern track, then a quiet older one. They should sound
   closer in loudness. The tags must exist: foobar2000 or MusicBrainz Picard can add them.
2. Export a playlist. Open the `.m3u8` in another player or a text editor.
3. Import an M3U made on a PC. Check the count message, and that the songs are right when two
   files share a name.
4. In Android Auto (or the Desktop Head Unit), open Playlists. Play Rediscover, and open a custom
   playlist.
5. Reboot the phone, open the media controls in quick settings, and press resume.
