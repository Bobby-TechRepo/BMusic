# Phase 4a — Premium features, part 1

Goal of this phase: the features people expect from a paid music player and that work fully
offline. Nothing is locked behind Pro yet. That waits on the monetisation decision (see the end).

> Written without a local Gradle build, like the earlier phases. Run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest` (or let CI run) before merging. The
> database moves to version 4. Commit the new `app/schemas/.../4.json` after the first build.

## What changed

### Sleep timer

- Open it from the moon button on the player. Choose 15, 30, 45, 60 or 90 minutes, or **End of
  song**. While a countdown runs, the sheet shows the time left and offers +10 min and Off. The
  moon button stays highlighted.
- The **playback service** owns the stop, not the screen. The timer works with the app closed and
  the phone locked.
- The music fades out over the last 10 seconds, then pauses, and the volume goes back to normal.
  This uses the same volume loop as "Sound fades as songs end", so the two features never fight
  over the volume.
- **End of song** pauses when the current song finishes, at the start of the next one. It also
  handles repeat-one and the end of the queue.
- If a countdown runs out while music is paused, the timer simply turns off.
- Code: `SleepTimer` (testable, injected clock), `PlaybackService` (`onMediaItemTransition`,
  `updateSoundFade`, `stopForSleepTimer`), `PlaybackOptionsSheet`.

### Playback speed

- Speeds 0.5×, 0.75×, 1×, 1.25×, 1.5× and 2×, in the same sheet as the sleep timer. A speed
  other than 1× replaces the speed icon with the speed itself, e.g. "1.5×".
- The speed is set through the `MediaBrowser`, so the service applies it, and it survives
  leaving the screen. It does **not** survive killing the app yet. 4b will persist it with the
  playback position.

### Sound effects

- Under the equalizer: **Bass boost**, **Surround** (virtualizer) and **Loudness boost**, each
  0–100 %. They work whether or not the equalizer switch is on.
- They are applied by the service on the player's audio session and saved in the equalizer
  preferences, so they are active without the screen. Moving a slider applies the change
  immediately, and the value is saved when you let go.
- Phones without an effect simply don't show its slider. A message appears if none is supported.
- Code: `AudioEffectsManager`, `AudioEffectsSettings`, `EqualizerPreferences.effects`.

### Listening stats

- A new chart button on Home opens **Listening stats**, for 7 days, 30 days or all time:
  - plays, listening time and the number of different songs;
  - top 10 songs and top 5 artists.
- Every listen counted by `PlayCountTracker` (30 s or half the song) is stored in a new
  `listen_events` table. It stores title, artist and album too, so stats survive a deleted file.
- **Room v4**: `MIGRATION_3_4` creates `listen_events` with an index on `listened_at`. Existing
  data is untouched. History starts from the first play after updating, because older play
  counts have no timestamps per listen.

### Backup and restore

- **Settings → Backup**: **Back up** writes a JSON file to any place the system file picker
  offers, including Drive. **Restore** reads one back.
- A backup holds favourites, playlists (in their order), play counts and listening history.
- Restoring **merges**:
  - nothing already here is deleted;
  - a playlist with the same name gets the missing songs;
  - play counts keep the higher value;
  - history is never duplicated, so restoring the same file twice is safe.
- Songs are matched by MediaStore ID only when the title matches too, because IDs differ on
  another phone. Otherwise they are matched on title and artist, ignoring case and accents, and
  a song of the same length (±3 s) is preferred. The result message says how many backed-up
  songs are not on this device.
- The format is versioned (`version: 1`) and ignores unknown fields, so later builds can extend
  it.
- Code: `data/backup/` (`BackupModels`, `BackupManager`), `BackupViewModel`, `Settings.kt`.

### Strings

There are 34 new strings and 2 plurals, in English, Spanish, French, Japanese and Chinese.
Spanish and French plurals include the `many` form.

## Tests

| Test | Covers |
| --- | --- |
| `SleepTimerTest` | Countdown, expiry, clamping, extend, 10 s fade curve, end-of-song, cancel |
| `BackupModelsTest` | JSON round trip, unknown fields, bad files, song matching rules |
| `BackupManagerTest` | Restore merging: favourites, new and existing playlists, play counts, no duplicate history, missing songs |
| `ListeningStatsRepositoryTest` | Period windows, what a listen stores, stats queries |
| `SpeedLabelTest` | "1×", "1.25×", rounding |
| `EqualizerViewModelTest` (updated) | Effects apply live and save only on release |
| `PlayingViewModelTest` (updated) | New `SleepTimer` constructor parameter |

## Check on a device

1. Update over an existing install and confirm playlists and favourites are still there (v3 → v4).
2. Set a 15-minute timer, lock the phone, and confirm the music fades out and pauses.
3. Turn on **End of song** with repeat-one, and confirm it stops after one play.
4. Set the speed to 1.5×, leave the player, and confirm the speed is kept and shown on the button.
5. Try bass boost on wired headphones and Bluetooth. Some Bluetooth stacks ignore effects.
6. Play a few songs past 30 s and check that Listening stats counts them.
7. Back up to Drive, clear the app data, restore, and check playlists keep their order.

## Left for Phase 4b

- Lyrics: embedded and `.lrc` files, with synced scrolling.
- Home-screen widget, and favourite / shuffle buttons in the media notification.
- Smart playlists: recently added, most played, not played in a while.
- Last.fm scrobbling, using the new listen history.
- Persist the playback speed.
- **Pro billing**: decide which of the above are Pro, then add Play Billing and a paywall.

## Decision needed

Is Pro a **one-time unlock** or a **subscription**? A one-time unlock suits an offline player and
is simpler, since there is no server and no renewal handling. A subscription only pays off if 4b
adds ongoing services, such as cloud backup or lyrics fetched online. The billing code and the
paywall wording depend on this choice.
