# Polish pass 1

Three loose ends left over from Phase 4.

> Not built here, like the earlier phases. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
> The new migration test needs a device or emulator: `./gradlew :app:connectedDebugAndroidTest`.

## 1. Imported lyrics are in backups

- **Back up** now includes every `.lrc` file you imported, stored as the original text with the
  song's details, so it can be matched on another phone.
- **Restore** brings them back and never replaces lyrics already imported on this phone. The
  result message adds "Lyrics for N songs restored".
- The format stays `version: 1`, and the new `lyrics` list is optional:
  - older backups still restore;
  - older app versions simply ignore the new list.
- Lyrics of songs no longer in the library are left out, because they could never be matched.
- Code: `BackupLyrics`, `BackupManager` (now needs `LyricsRepository`), and
  `LyricsRepository.saveImported` / `importedLyrics`.

## 2. Resuming no longer reshuffles

- **Before:** when Play on the widget, a headset or the system controls resumed a closed app,
  Media3 handed the restored queue back as a "new" queue. With shuffle on, that shuffled it again.
- **Now:** `PlaybackCoordinator.install` notices it is being given the queue that is already
  loaded (same entries, same order). It only moves to the requested song and position, so the
  shuffled order and the original order behind it are kept.
- A genuinely different list still replaces the queue as before. Tapping **Shuffle** again still
  reshuffles, because the app sends the list in its original order.

## 3. Database migration test

- `androidTest/.../UserDataDatabaseMigrationTest` writes real database files as versions 1, 2
  and 3 left them, then opens them with the app's migrations.
- **What it checks:**
  - Room compares every table, column, default, key and index with the current entities when
    it opens the file, so any difference fails the test.
  - Favourites, play counts and playlists survive the upgrade.
  - Playlist songs are numbered in the order they were added (v3).
  - Album IDs default to -1 when the old file had none (v2).
  - Listening history works after the upgrade (v4).
- No schema files or extra libraries are needed: the old schemas are written out in the test.
  From now on, also commit the `app/schemas/` folder Room generates, so future migrations can
  use Room's `MigrationTestHelper`.

## Tests

| Test | Covers |
| --- | --- |
| `PlaybackCoordinatorTest` (2 new) | Re-installing the loaded shuffled queue only seeks; a different list still replaces |
| `BackupManagerTest` (3 new) | Export keeps lyrics of library songs only; restore saves without replacing; old files decode |
| `UserDataDatabaseMigrationTest` (device) | Upgrades from versions 1, 2 and 3 to 4 |
