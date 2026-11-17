# Notification and lock-screen integration (Pass 5)

## What this pass changes

- The Media3 session now has an explicit `sessionActivity` PendingIntent pointing to `MainActivity`.
- Tapping the system media card can bring BMusic to the foreground using `SINGLE_TOP` / `CLEAR_TOP`, without creating a new activity stack each time.
- Playback continues to use the `MediaLibrarySession` notification integration, which is the correct path for Android media controls and lock-screen transport controls.

## Platform limitation

Android 13+ uses system-rendered media controls. The app cannot force an arbitrary RemoteViews layout, background crop, typography, or button positioning into the system media card or lock screen on all devices. The app can supply title, artist, artwork, playback state, supported transport actions, and a session activity. The exact screenshot design can be reproduced in an in-app player surface, but not guaranteed as the OS-owned notification / lock-screen layout.

## Test checklist

1. Run `:app:compileDebugKotlin`.
2. Start playback, pull down the notification shade, and confirm title, artist, artwork, and play/pause/previous/next controls are present as supported by the device.
3. Lock the screen while audio is playing; confirm the lock screen displays the active media session and artwork.
4. Tap the media card from both the notification shade and lock screen; confirm BMusic returns to the foreground without duplicate activity instances.
5. Test with an item whose `albumId` is missing. The system may show a fallback icon because no valid album-art URI can be published for that item.
