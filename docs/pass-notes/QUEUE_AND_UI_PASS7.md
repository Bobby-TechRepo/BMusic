# BMusic Migration Pass 7

## UI correction
- Restored `ui/components/BottomControl.kt` to the original compact mini-player design from the uploaded project. The artwork-background redesign belongs to the system notification work, not the in-app bottom mini-player.

## Queue verification focus
This pass retains the PlaybackCoordinator/QueueManager work from prior passes. Verify these cases on-device before treating queue management as complete:
1. Select a song from the middle of a custom playlist; Next should continue with the following playlist entry.
2. Play the same song twice in one queue; both entries must remain independently selectable/removable.
3. Toggle shuffle on and off while a song is playing; the current song should not change.
4. Remove the current queue entry, then use Next/Previous.
5. Add a song to play next and confirm it plays after the current track.
6. Reorder entries with shuffle both off and on.
7. Play from Recently Played and Most Played; this must not cause the active queue to be rebuilt on each track transition.

## Notification design status
The reference image is intended for the Android notification shade, not the in-app mini-player. The custom notification appearance is not implemented by this pass. Android 13+ system media controls and lock-screen surfaces impose layout restrictions; the next notification implementation should use supported Media3/session metadata and platform-compatible notification customization, then be tested on the target Android versions.
