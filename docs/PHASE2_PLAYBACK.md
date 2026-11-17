# Phase 2 — Re-architect playback

Goal of this phase: one owner for the play queue, standard shuffle that every controller
understands, and playback snapshots that are small, correct and written by one place.

Phase 1 follow-up included here: the 4 `SongRepositoryTest` failures. One cause: `songCollection`
was typed `Uri` (non-null). The JVM test stub returns `null` for `MediaStore` constants, so the
Kotlin null-check failed while the file's static fields initialised. It never happened on a
device. The explicit type is removed.

> Like Phase 1, this was written without a local Gradle build. Run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest` (or let CI run) before merging.

## How playback is wired now

```
UI (PlayingViewModel)          Notification · Bluetooth · Android Auto · Assistant
        │  standard Player commands only (setMediaItems, move, remove, add, shuffle)
        ▼
MediaLibrarySession ── LibraryCallback: resolves media IDs to playable items, nothing else
        │
        ▼
BMusicPlayer (ForwardingSimpleBasePlayer over ExoPlayer)
        │  every queue change and shuffle toggle
        ▼
PlaybackCoordinator ──► ExoPlayer      (what plays)
                    └─► QueueManager   (queue model the UI reads)
        │
PlaybackService listener ──► PlaybackSnapshotStore (playback_queue.json, playback_position.json)
```

- **Single owner.** `PlayingViewModel` no longer touches `QueueManager` or the snapshot
  preferences. It only reads `QueueManager.state`. Commands from any controller reach
  `BMusicPlayer`, which calls `PlaybackCoordinator`, which updates ExoPlayer and `QueueManager`
  together. The old risk of the UI's copy drifting from what plays is gone, and edits made from
  Android Auto now update the in-app queue too.
- **Callback resolves, player installs.** `onSetMediaItems` and `onAddMediaItems` only turn IDs
  into playable items. Installing the queue and applying shuffle happens in `BMusicPlayer`.

## Shuffle

- The custom `toggle_command` session command is gone. Shuffle is the standard Media3
  `shuffleModeEnabled`. That means the system media controls, Bluetooth devices and Android Auto
  can toggle it, and they see the right state.
- BMusic still shows "Up next" in play order, so shuffling reorders the timeline. `BMusicPlayer`
  reports shuffle from `QueueManager` while ExoPlayer's own shuffle stays off.
- **No more stutter when toggling.** Before, shuffle replaced the whole timeline, which reloaded
  the current song. Now `PlaybackCoordinator.setShuffle` removes everything except the playing
  entry and adds the new order back around it. Playback is never interrupted.
- A queue edit that keeps the same song playing no longer restarts its play-count listening time.

## Snapshots

- Playback state moved out of `app_settings.json` into two small files written only by the
  service through `PlaybackSnapshotStore`:
  - `playback_queue.json` (media IDs, original order, context, shuffle) is written only when the
    queue changes. Identical saves are skipped, and bursts (dragging an entry) coalesce into one write.
  - `playback_position.json` (index, position, play-when-ready, repeat) is written on track change,
    play/pause, repeat change, every 15 s while playing, and when the service stops.
- **Shuffle survives restarts properly.** The original order behind a shuffled queue is saved
  (`QueueManager.sourceOrder`). Restore puts back the exact play order without reshuffling, and
  turning shuffle off afterwards returns to the original order. Before, restore reshuffled and
  the original order was lost.
- Songs deleted since the last session are dropped, and the rest keep their order and position.
- The old snapshot in `app_settings.json` is read once, migrated and cleared (`migratedLegacy`).
- Corrupt snapshot files reset to empty instead of crashing (`ReplaceFileCorruptionHandler`).

## Smaller changes

- "Play all" and "Shuffle" no longer force repeat-all on.
- Tapping a song on the Songs tab sends its ID as well as its position, so the right song plays
  even when sorting and filtering arrive in Phase 3.
- New `@ApplicationScope` coroutine scope for work that must outlive screens and the service.

## Tests added or updated

- `PlaybackCoordinatorTest`: seamless shuffle on and off (verifies the timeline is never replaced),
  no-op when unchanged, batch add, range remove, clear via range, install without prepare.
- `QueueManagerTest`: exact restore and un-shuffle, gaps from deleted songs, invalid source order.
- `PlaybackSnapshotStoreTest`: round trip, duplicate saves written once, position saves leave the
  queue file alone, mismatched order dropped, one-time legacy migration.
- `PlayingViewModelTest`: constructor no longer takes `AppSettingsPreferences`.

## Device checklist

1. Play an album, then toggle shuffle on and off from the app. The song should never restart or
   stutter, and "Up next" should change.
2. Toggle shuffle from the notification (if your device shows the button), from Bluetooth, or from
   Android Auto. The in-app shuffle icon should follow.
3. With shuffle on, force-stop the app and reopen it. "Up next" should be the same shuffled order.
   Turn shuffle off and the album order should return.
4. Reorder, remove and "Play next" items in the queue, then restart. The queue should be exactly
   as you left it.
5. Upgrade over a Phase 1 install. The last queue should still restore once.
6. Toggle shuffle mid-song after 20 seconds of listening. The play still counts after another
   10 seconds, not 30.
7. Tap "Play all" on an album with repeat off. Repeat should stay off.

## Left for later phases

- Media3 playback resumption (`onPlaybackResumption`) for the system's "resume" card after a reboot.
- Custom notification buttons (shuffle, repeat, favourite) via `MediaButtonPreferences`.
- A custom bitmap loader if the legacy album-art URI turns out to be missing art on some devices.
