# BMusic queue phase 8

## Fix in this pass

Shuffle toggling no longer searches the queue by `mediaId` to recover the current item. That lookup is ambiguous when the same song appears more than once. `QueueManager` already returns the correct active index when shuffle is enabled (current entry moved to the front) or disabled (current entry mapped back to source order); the coordinator now uses that index directly.

## Regression checklist

- Play a playlist containing the same song twice, then toggle shuffle on and off. The exact playing queue entry should remain selected.
- Toggle shuffle while playback is paused and verify the play position is preserved.
- Remove the current entry and verify Media3's next/current selection is reflected in the queue UI.
- Insert the same song using Play Next twice; both entries should remain independently removable.
- Reorder entries while shuffle is enabled and verify the active playback order remains visible.

## Notification shade limitation

The supplied screenshot is a custom notification design, not the in-app mini-player. Media3's standard media notification is rendered using Android's media notification framework. On Android 13+, the system owns the prominent media-player layout in the notification shade and lock screen; arbitrary `RemoteViews` styling is not guaranteed to be shown there. This pass intentionally does not claim to have reproduced the screenshot in the system shade. A custom Media3 notification provider can customize notification details where supported, but cannot force Android 13+ to render a fully arbitrary layout.
