# Playback integration contracts — Pass 11

This pass adds JVM contract tests around `PlaybackCoordinator`, the boundary between app queue state and Media3's `Player` API. It does not change runtime playback behavior or claim to simulate Android process death.

## Coverage

- Queue replacement passes the exact queue, selected index, context, and start position to Player.
- Queue removal and reordering update both the Player and QueueManager.
- Play-next inserts immediately after the current Player index.
- Invalid queue operations do not issue Player mutations.
- Clearing the queue clears both Player media items and app queue state.

## Run

```bash
./gradlew :app:testDebugUnitTest
```

## Device-level restoration regression

The coordinator tests complement, but do not replace, a real-device check of persisted restoration:

1. Play from the middle of a custom playlist and seek to a known position.
2. Force-stop, relaunch, and verify song, playlist context, queue order, and approximate position.
3. Repeat with shuffle enabled and with duplicate entries.
4. While playing, add-next, reorder, and remove an entry; restart and confirm the saved timeline matches the visible queue.
5. Start playback from Recently Played and Most Played; verify history selection doesn't mutate the active queue unexpectedly.

A force-stop can only restore the most recently persisted position; periodic snapshots may lag by several seconds. This is expected unless the app is able to persist a final snapshot before shutdown.
