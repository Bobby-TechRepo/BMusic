# BMusic playback migration (pass 2)

## Architecture decisions

- `PlaybackService` owns the ExoPlayer and MediaLibrarySession lifecycle.
- `PlaybackCoordinator` centralizes queue replacement, shuffle application, and synchronization with the actual player.
- `QueueManager` owns the app's source order and maps shuffled queue positions back to source positions. It does not perform playback itself.
- Room-backed play counters remain history/statistics. A `recently_played` list is a snapshot used when the user starts playback, not a live queue that is rebuilt during every transition.
- `MediaLibrarySession.Callback` remains responsible for browsing, resolving virtual library items, and accepting the app's custom shuffle command. UI playback requests continue through `MediaBrowser` so Media3 remains the authority for playback and external controller integration.

## Behavior fixed in this pass

1. Custom queue entries are resolved as explicit ordered items rather than mistaken for virtual browse IDs.
2. Tapping a song in a supplied list starts from that selected index. Shuffle keeps the selected song first and randomizes the remaining source positions.
3. Tapping a single album/artist/song item no longer toggles shuffle as a side effect.
4. Recently Played is no longer rebuilt with `setMediaItems()` from `onMediaItemTransition`; that could reset the current position or alter what plays next.
5. The current queue index is synchronized from ExoPlayer transitions, and shuffle tracks source-position mapping instead of locating items only by equality (which is ambiguous for duplicate entries).
6. Playlist queue entries use contextual IDs containing the playlist and source position; when restoring a context, the song ID is used to recover its current position if the playlist order changed.
7. Forward/rewind seek commands now seek directly to the requested target instead of issuing an unrelated seek operation first.

## Notification and lock-screen notes

The MediaLibrarySession notification and Android lock-screen controls are generated from Media3's session/player state. The title, artist, playback state, duration, and action availability should remain accurate because they come from the current MediaItem and Player. Android 13+ and many OEM lock screens own the layout; a custom RemoteViews notification cannot force the exact screenshot design onto the lock screen. Matching album art requires a valid album-art URI/bitmap in `MediaMetadata.artworkUri`/`artworkData`; BMusic's current `Song` model does not carry album IDs, so this pass does not falsely label a song-file URI as album artwork. The next artwork-focused step is to load the actual album ID/artwork URI from MediaStore and attach it to each MediaItem's metadata.

## Manual test matrix

- Tap the first, middle, and last song in a custom playlist; verify that exact song starts and Next follows the playlist order.
- Repeat with shuffle enabled; the tapped song should remain first and the remaining queue should be randomized.
- Toggle shuffle while a song is playing; playback should continue at the same position and current song.
- Start playback from Recently Played and Most Played; verify the queue stays stable as play counts/history update.
- Remove/reorder playlist songs, then tap a song; verify the clicked song starts at its current position.
- Test duplicate song entries if the product later allows them; queue-entry IDs must remain unique.
- Test play/pause/next/previous from the notification, lock screen, Bluetooth headset, and Android Auto/external controller if supported.
- Test API 26, API 33+, and a current Android device/OEM skin.

## Build verification

Compilation was attempted with the project's Gradle wrapper, but the environment could not download the configured Gradle distribution (`services.gradle.org` was unreachable). Please run `./gradlew :app:compileDebugKotlin` in Android Studio or a network-enabled development environment before merging.


## Pass 3: current queue editing

- `QueueManager` now exposes `move`, `removeAt`, and `insertAt` operations. These preserve duplicate queue entries and maintain the mapping between source order and shuffled playback order.
- `PlayingViewModel` exposes `moveQueueItem`, `removeQueueItem`, `playNext`, and `clearQueue` so UI actions can edit the active Media3 timeline without rebuilding it.
- The Now Playing queue's drag callback now forwards reorder operations to Media3 and the queue projection. Queue rows use each media item's `mediaId` as their stable key, rather than song ID, so duplicate tracks remain independently addressable.

## Notification and lock screen

The lock-screen and Android 13+ media notification are system-rendered from the active media session. BMusic should publish accurate title/artist/album metadata and a real artwork URI/data to Media3; it cannot force arbitrary Compose or RemoteViews styling into the system lock-screen media controls. The track URI is not necessarily an artwork URI, so do not set it as artwork merely to force a thumbnail. A subsequent art pass should resolve album IDs from MediaStore and provide a valid album-art URI or artwork bitmap.

## Verification

Run `:app:compileDebugKotlin`, then manually verify: selecting a middle song in every source context, shuffle on/off while preserving the current song, reordering across the current index, removing the current and non-current entry, duplicate songs, play-next, queue clearing, and next/previous via system media controls. This environment could not run Gradle because the wrapper distribution host was unreachable; compilation must be verified in Android Studio.


## Pass 4: media artwork for notification and lock screen

- `Song` now distinguishes the audio `mediaUri` from its artwork URI. The audio item always points at `MediaStore.Audio.Media`; album art is published only when a valid `ALBUM_ID` is available, using MediaStore's album-art endpoint.
- Song queries now read `MediaStore.Audio.Media.ALBUM_ID` and attach it to `MediaMetadata.artworkUri`, which is the metadata source Media3 can expose to the system media notification and lock-screen controls. The audio-file URI is not falsely advertised as artwork.
- Custom playlist entries persist `album_id`. Room schema version 2 includes a non-destructive migration from version 1, defaulting existing playlist rows to `-1` when their album cannot be reconstructed. Newly added playlist tracks retain their album ID.
- The OS still chooses the lock-screen / Android 13+ system media layout. This improves artwork and metadata correctness but does not force the screenshot's exact visual design into system UI.

### Pass 4 validation

- Compile `:app:compileDebugKotlin`.
- Install over an existing build with a version-1 Room database to verify migration preserves playlists.
- Start playback from Songs, Albums, Recently Played, Most Played, and a custom playlist; check that supported tracks publish artwork to the notification and lock screen.
- Test a track with missing album art; playback must still work and the system may display its fallback artwork.
