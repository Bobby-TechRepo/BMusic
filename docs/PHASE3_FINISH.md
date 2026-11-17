# Phase 3 — Finish the app

Goal of this phase: every visible control works, every list can be acted on, and the app reads
well in all five languages and with TalkBack.

Phase 2 follow-up included here: the 3 `PlaybackSnapshotStoreTest` failures. The store's writers
run in the test's `backgroundScope`, and `advanceUntilIdle()` does not execute background-only
work, so nothing was written. The tests now use `runCurrent()`. The store itself was correct.

> Written without a local Gradle build, like the earlier phases. Run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest` (or let CI run) before merging. Room now
> exports its schema to `app/schemas/`; commit that folder after the first build.

## What changed

### Settings that now work

| Setting | What it does now | Where |
| --- | --- | --- |
| Hide short songs | Filters out tracks under one minute. On by default, matching the old always-on filter. Applies app-wide, including Android Auto. | `BMusicApp`, `SongRepository.setHideShortSongs` |
| Remember last open tab | Home reopens on the tab you last used. | `HomeScreen`, `AppSettingsData.lastTab` |
| Sound fades as songs end | 1.5 s fade-in and 4 s fade-out per track, capped at a third of very short tracks. Runs only while playing. | `PlaybackService.updateSoundFade`, `fadeVolume()` |
| Shake to change song | Two jolts within 0.6 s skip to the next song, with a 1.5 s cooldown. The accelerometer is used only while music plays. | `ShakeDetector`, `PlaybackService.updateShakeListener` |
| Accent colour | New "Match wallpaper" option (Material You) on Android 12+. | `Settings.kt`, `MainActivity` |

### Acting on songs

- **One song menu everywhere**: the Songs tab, albums, artists, playlists and search. It offers
  Play next, Add to queue, Add to playlist (or create a new one), Add or remove favourite, Go to
  album, Go to artist, Share, and Remove for custom playlists. A snackbar confirms each action.
- **Share sends the audio file** itself, with a read grant, instead of just the title text.
- **Playlist edit mode works**: long-press any song (or choose Edit in the playlist menu) to
  select. The bar offers Select all, Play, Add to queue, and Remove (for custom playlists and
  Favourites). Before, all three buttons were empty.
- **Reorder custom playlists**: in edit mode, long-press and drag. The order is saved.
- **Queue editing**: each queue row has a remove button. The header shows the count, plus
  *Save as playlist* and *Clear* (with confirmation).

### Finding songs

- **Global search** (magnifier on Home) covers songs, albums, artists and playlists. It ignores
  case and accents ("beyonce" finds "Beyoncé"), requires every word to match ("adele hello"),
  and ranks titles that start with the query first.
- **Songs tab sorting**: Title, Artist, Album, Recently added, Longest first. The choice is saved.
  Sorting follows the device language (`Collator`), and the A–Z index shows only for text orders.
  Tapping a song queues exactly the order on screen.
- **Albums play in track order** (disc, then track), in both the list and the queue.

### Database (Room version 3)

- `playlist_songs.position` stores the user's order. Migration 2→3 numbers existing rows in the
  order they were added, so current playlists look the same after the upgrade.
- New songs are appended after the last position. Bulk remove runs in one query.
- `exportSchema = true`, so migrations can be tested with `MigrationTestHelper`.
- `FavouritesDao.get` compared numbers with `LIKE`; now `=`.

### Polish

- **Artwork cache**: an in-memory LRU cache (1/8 of the heap) that also remembers songs with no
  art, so scrolling no longer decodes the same thumbnails again and again. Rows now reload art
  when their song changes (the queue row never did before).
- **Back stack survives recreation** (`rememberNavBackStack`): switching language or dark mode no
  longer drops you back on Home. Back on Home sends the app to the background, as Android does.
- **Orientation**: phones stay portrait; tablets and foldables rotate freely (Android 16 ignores
  large-screen locks anyway). The lock is applied in a `SideEffect`, not on every recomposition.
- **Playlists tab** is one scrolling grid. Before, custom playlists were in a non-scrolling
  column and a long list was cut off. Its duplicate search bar is gone.
- **Album and artist screens** show their error state instead of a blank page.
- **Translations**: 42 new strings and 7 plurals in all five languages. The remaining hard-coded
  English is gone ("Rename playlist", "Play next", "Share", the player tab titles, "Played N times",
  "System default", and the album/artist loading text).
- **TalkBack**: repeat, shuffle, favourite, song options, remove, reorder and now-playing icons
  are labelled, and the player's tabs are announced as tabs.
- **Removed scratch code**: `ui/Test.kt`, `ui/recordables.kt`,
  `ExpandedFullScreenContainedSearchBarExample.kt`, `MediaViewModel`, `QueueEntry`, `RequestState`.

## Tests added or updated

- `SongSortTest`: accent- and case-insensitive titles, tie-breaks, track order, newest and
  longest first, unknown saved values.
- `LibrarySearchTest`: blank query, accents, multi-word, ranking, playlists.
- `SoundFadeTest`: middle, fade-in, fade-out, unknown duration, short tracks.
- `ShakeDetectorTest`: resting phone, one jolt counted once, window, cooldown.
- `PlaylistRepositoryTest`: append after the last position, reorder, bulk remove, create with songs.
- `SongsViewModelTest`: bulk remove, reorder, create playlist with songs, favourite toggle.

## Device checklist

1. Upgrade over a Phase 2 install with custom playlists. They should keep their songs and order.
2. Open a playlist, long-press a song, select three, then Remove. Reopen it and they should stay gone.
3. Edit a custom playlist, drag a song to the top, go back and reopen it. The new order should stay.
4. Open the song menu from the Songs tab: Play next, Add to queue, add to a new playlist, Go to
   artist, Share to another app (it should receive the audio file).
5. Search "beyonce" (no accent) and "adele hello".
6. Sort Songs by Recently added, restart the app, and the sort should be kept. Tap a song; Next
   should follow the on-screen order.
7. Turn on "Sound fades as songs end": the end of each song should fade out. Turn on "Shake to
   change song" and shake twice: the next song should play.
8. Turn off "Hide short songs": short clips should appear in Songs within a moment.
9. Choose "Match wallpaper" on Android 12+ and the colours should follow the wallpaper.
10. Switch the app language while on an album page. You should stay on that page.
11. With TalkBack on, the player's repeat, shuffle and favourite buttons should announce their state.

## Left for Phase 4 (premium)

Gapless and crossfade between tracks, synced lyrics, sleep timer, an advanced EQ, ReplayGain, smart
playlists, listening stats, Android Auto and Wear, a home-screen widget, backup and M3U, scrobbling,
and the Play Billing Pro unlock. Also from earlier phases: custom notification buttons and the
system "resume" card.
