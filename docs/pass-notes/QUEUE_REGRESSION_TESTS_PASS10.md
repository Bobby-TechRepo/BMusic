# Queue regression tests — Pass 10

This pass strengthens JVM unit coverage for the queue model without changing playback behavior.

## Covered scenarios

- Replacing a queue and selecting a requested entry.
- Clamping invalid start indices and clearing the selection for an empty queue.
- Enabling/disabling shuffle while retaining the selected song.
- Keeping duplicate song occurrences as separate queue entries.
- Removing one duplicate occurrence and removing the current entry.
- Moving entries and updating the selected index.
- Rejecting invalid move indices.
- Inserting an entry while preserving the current selection.
- Retaining repeat mode.

## Run

From the project root:

```bash
./gradlew :app:testDebugUnitTest
```

These are JVM tests and do not replace device tests for ExoPlayer timeline callbacks, process death, or DataStore persistence. Please run the test task locally; Gradle could not be independently executed in the current authoring environment.
