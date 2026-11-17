# Phase 1 — Stabilise

Goal of this phase: no known crash, library always current, playback that survives bad files and
background starts, and a CI build that proves it. Bug numbers refer to the "Critical bugs" table in
the BMusic senior code review.

> **Not compiled yet.** This change set was written without access to Gradle or Google's Maven
> repository, so it has not been built or run. Push it and let `.github/workflows/android-ci.yml`
> build it, or run `./gradlew :app:assembleDebug :app:testDebugUnitTest` locally, before merging.

## What changed

| Bug | Fix | Main files |
| --- | --- | --- |
| 1 Service crash browsing albums/artists | New typed `MediaId` / `MediaSource` parser used everywhere; the wire format is unchanged so saved queues still restore. Browse children carry album/artist context. | `data/models/MediaId.kt`, `services/PlaybackService.kt`, `Album.kt`, `Artist.kt` |
| 2 Shuffle on empty list crashes | `indices.randomOrNull()` guards on playlist, album and artist screens; Play All ignored when empty. | `ui/screens/Playlist.kt`, `Album.kt`, `Artist.kt` |
| 3 Empty title crashes A–Z lists | `sectionLetter()` never throws; non-A–Z first characters group under `#`. | `ui/components/AlphabeticList.kt` |
| 4 Library never refreshes | `SongRepository` now has thread-safe caches, one cursor mapper, `invalidate()`, and a debounced MediaStore observer started in `BMusicApp`. `SongsViewModel` reloads on `changes`. | `data/repositories/SongRepository.kt`, `BMusicApp.kt`, `viewmodels/SongsViewModel.kt` |
| 5 Artist pages show no albums | Albums are derived from the artist's tracks instead of comparing the album's artist *name* with the artist *ID*. | `SongRepository.kt` |
| 6 Wrong notification artwork, corrupt `albumId` | `MediaMetadata.artworkUri` is the MediaStore album-art URI (never the audio file); `albumId` round-trips through metadata extras. | `data/models/Song.kt` |
| 7 Inflated play counts | `PlayCountTracker`: a play counts after 30 s or half the track of real listening. Restores, seeks and quick skips no longer count. | `data/PlayCountTracker.kt`, `PlaybackService.kt` |
| 8 Main-thread blocking in the service | All library callbacks return futures from coroutines (`kotlinx-coroutines-guava`) and do MediaStore/Room work on an injected I/O dispatcher. No more `runBlocking`. | `PlaybackService.kt`, `modules/DispatchersModule.kt` |
| 9 Silent playback errors | The service skips unplayable tracks (I/O, parsing, decoding errors; at most 5 in a row). The UI shows a snackbar, retries the controller connection 3 times, and shows a retry screen if it still fails. | `PlaybackService.kt`, `viewmodels/PlayingViewModel.kt`, `ui/Main.kt` |
| 10 Language switch broken on Android 8–12 | `MainActivity` is an `AppCompatActivity`, theme parent is AppCompat, `AppLocalesMetadataHolderService` is declared, and `LocaleHelper` uses one `AppCompatDelegate` path. | `activities/MainActivity.kt`, `res/values/themes.xml`, manifest, `utils/LocaleHelper.kt` |
| 11 Equalizer crashes / not restored in background | `EqualizerManager` never throws; the service attaches it to the initial audio session and applies saved settings itself. The ViewModel only observes and clears its listeners. | `data/EqualizerManager.kt`, `viewmodels/EqualizerViewModel.kt`, `PlaybackService.kt` |
| 12 Add Songs search case bug | Case-insensitive matching. | `ui/screens/AddSongs.kt` |
| 13 Stale, slow A–Z list | Headers recomputed per list, rows keyed by song ID, no O(n²) `indexOf`, guarded sidebar lookups. | `AlphabeticList.kt` and callers |

### Also fixed along the way

- **Artist screen played the wrong song**: tapping a track played the one after it, because row 0 is the album strip. Now `index - 1`.
- **Permission flow**: only `READ_MEDIA_AUDIO` (or `READ_EXTERNAL_STORAGE` on 12 and below) is requested. Denial shows a screen with *Allow access* and *Open settings*, and the app picks up a grant made in Settings when you return.
- **Error and empty states**: a load failure shows a retry screen; an empty library shows "No music found" on the Songs tab.
- **Reopening the app mid-song**: the play button and progress bar now sync immediately instead of waiting for the next play/pause.
- **Seek while paused** moves the progress bar; **Previous** restarts the song after its first seconds.
- **Mini-player progress** no longer computes NaN with an unknown duration.
- **Background playback**: `setWakeMode(C.WAKE_MODE_LOCAL)` plus the `WAKE_LOCK` permission.
- **Media3 browse items** now set `isPlayable`/`isBrowsable` and media types, which Media3 requires for library results. Search notifies its result count, so Assistant and Auto search work.
- **Snapshot**: the position is saved every 15 s while playing instead of every 5 s at all times.
- **Repo**: pass notes and screenshots moved to `docs/`, `gradlew` is executable, CI added.
- **Accessibility**: the player and mini-player buttons now have TalkBack labels.
- **Strings**: 17 new strings, translated into English, Spanish, French, Japanese and Chinese.

### New dependency and build settings

- `org.jetbrains.kotlinx:kotlinx-coroutines-guava` (same version as `kotlinx-coroutines-test`).
- `testOptions.unitTests.isReturnDefaultValues = true`, so JVM tests that touch `Bundle`, `Uri` or `Handler` stubs run instead of throwing.

### Tests added or updated

- `MediaIdTest`: every ID form, garbage input, encode round trip, wire-format stability.
- `PlayCountTrackerTest`: threshold, short tracks, skips, restored position, seeks, repeat.
- `SectionLetterTest`: empty and blank titles, accents, digits, non-Latin scripts.
- `SongRepositoryTest`: invalidation, blank search, `SecurityException` before permission.
- `SongsViewModelTest`: rewritten for the injected repository and dispatcher; permission and reload cases.

## Device checklist before moving to Phase 2

1. Fresh install: deny the permission, check the permission screen appears, then grant it from *Open settings* and come back. The library should load without restarting.
2. While the app is open, copy a new MP3 to the phone. It should appear in Songs within a few seconds.
3. Delete a song that is in the queue, then play to it. A snackbar should appear and the next song should play.
4. Android Auto (or the Desktop Head Unit): open Albums, then an album, then Artists. Nothing should crash. Tapping a track should play the rest of that album.
5. Notification and lock screen: the album cover should show for tracks that have one.
6. Play a song for 10 seconds and skip it. Most Played should not change. Play one for 40 seconds and it should go up by one. Restarting the app should not add a play.
7. Set an EQ preset, force-stop the app, then resume playback from Bluetooth or the notification without opening the app. The EQ should be active.
8. On an Android 12 or lower device, change the language in Settings. The UI should switch and stay switched after a restart.
9. Shuffle an empty custom playlist, and open a song whose title is blank. Neither should crash.
10. Open an artist and tap the first song. That exact song should play.

## Known limits (left for later phases)

- The ViewModel still mirrors queue edits into `QueueManager` (Phase 2 makes the service the single owner).
- Shuffle still uses the custom session command, not Media3's shuffle mode (Phase 2).
- Short songs are still always hidden by the 60 s filter; the "Hide short songs" setting is wired up in Phase 3.
- Album tracks are still listed by title, not track number (Phase 3 sorting).
- Verify on a device that the legacy `content://media/external/audio/albumart/<id>` URI serves art on your target Android versions. If it does not, Phase 2 adds a custom bitmap loader.
