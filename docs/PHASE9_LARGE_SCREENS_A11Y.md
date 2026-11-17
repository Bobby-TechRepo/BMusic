# Phase 9: Large screens, accessibility, keyboard

The last phase of the extended roadmap. BMusic now uses the space on tablets, unfolded foldables
and desktop windows, works better with TalkBack and large text, and responds to a hardware
keyboard.

> **Not built yet.** Gradle can't run here, so please run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
>
> **Tested here.** The new pure logic was compiled with the real Kotlin compiler, and its
> 13 tests pass: the pane moves (7) and the keyboard mapping (6).

## 1. Two-pane library on large screens

When the window is at least **840 dp wide** (Material's "expanded" size: tablets in landscape,
unfolded foldables, desktop windows), Home splits in two:

- **Left:** Home as usual, with the navigation rail, the tabs' content, search and the mini
  player.
- **Right:** the album, artist or playlist you open, in the same dark, artwork-backed design.
  Before you open anything, it shows "Nothing open yet".

How it behaves:
- **Opening from Home** replaces what the right pane shows; it doesn't pile up. Tapping the
  one already open keeps its history.
- **Opening from inside the pane** stacks within the pane, e.g. an artist's album. The pane's
  back arrow and the system Back button go back within the pane, then close it.
- **Opening from the song menu:** "Go to album" and "Go to artist" open in the pane too, when
  Home is showing.
- **Full-screen screens** stay full screen: the player, Settings, Search, Stats, Add songs and
  the tag editor.
- **Resizing keeps your place.**
  - Window gets narrow (fold the phone, shrink the window): the pane's screens move onto the
    normal back stack, right above Home, so Back still walks through them.
  - Window gets wide again: the album, artist and playlist screens directly above Home move
    into the pane.
- **Restarts:** the pane's history is saved, so it survives rotation and the app being
  closed in the background.

The logic is in `utils/Panes.kt`, unit-tested in `PanesTest`, and wired up in `ui/Main.kt`.

## 2. Accessibility

**TalkBack**
- **Song rows** (everywhere):
  - The playing song reads as **"Now playing"**.
  - Rows offer TalkBack **actions** ("Song options", and "Remove from queue" in the queue),
    so there's no need to find the small buttons.
- **Headings:** section headers are marked as headings, so TalkBack users can jump between
  them. This covers Home sections, album, artist and playlist titles, "Albums" and "Songs" on
  artist pages, Settings groups, bottom-sheet titles, "Up next" in the queue, and the open
  folder's name.
- **Seek bar:** reads **"1:23 of 3:45"** instead of a bare percentage.
- **Mini player:** announces "Double-tap to open the player".
- **Unlabelled buttons:** the old player-controls file had unlabelled buttons and wasn't used
  any more, so it was removed (`ui/components/Playing.kt`). The live controls were already
  labelled, and every other icon-only button already has a label.

**Large text**
- **Buttons and tiles** no longer have fixed heights, so they grow instead of cutting text off
  at 200% font size. This covers the Shuffle, Play and Import buttons, the search pill and
  search field, the Play and Shuffle buttons on album, artist and playlist screens, New
  playlist's Create button and the section headers.
- **Built-in playlist tiles** keep one even height (132 dp). It grows only in step with the
  system font size, so the name and count always fit.

**Touch targets**
- **Folder breadcrumbs:** each one is now at least 48 dp tall.

**Reduced motion**
- With **Remove animations** on (Settings › Accessibility), the now-playing bars stand still
  and the seek line stays flat instead of waving.
- The setting is read again when you return to the app (`utils/Motion.kt`).

## 3. Keyboard shortcuts

For tablets with keyboards, Chromebooks and desktop windows. Shortcuts only fire when no text
field is using the key, so typing in search is never affected. Plain arrow keys still move the
focus between buttons.

| Keys | Action |
| --- | --- |
| Space, K | Play / pause |
| Ctrl + →, Shift + N | Next song |
| Ctrl + ←, Shift + P | Previous song |
| L / J | Forward / back 10 seconds |
| Ctrl + F, / | Search |

- **The list in the app:** **Settings › About › Keyboard shortcuts**.
- **Holding a key down** acts once.
- **Media keys** (play, pause, next) on keyboards and headsets go through the media session as
  before.

The mapping is in `utils/KeyboardShortcuts.kt` and tested in `KeyboardShortcutsTest`. The
activity sends each shortcut to the UI.

## Files

**New**
- `utils/Panes.kt`, `utils/KeyboardShortcuts.kt` and `utils/Motion.kt`.
- Tests: `PanesTest` and `KeyboardShortcutsTest`.
- `ms_keyboard` icon, and 11 strings in all five languages.

**Changed**
- `ui/Main.kt`: the two-pane layout, detail screens shared by the pane and the full screen,
  and shortcut handling.
- `MainActivity.kt`: key handling.
- `LibraryKit.kt`: song row TalkBack state and actions, and reduced-motion bars.
- `Seeker.kt`: spoken position, reduced motion.
- `BottomControl.kt`: the "open the player" label.
- Headings and flexible heights: `DesignKit.kt`, `DetailKit.kt`, `SheetKit.kt`,
  `Settings.kt` (also the shortcuts list), `Playing.kt`, `Folders.kt`, `Songs.kt`,
  `Playlists.kt`, `PlaylistForm.kt` and `Search.kt`.

**Removed:** `ui/components/Playing.kt` (unused).

## Please try

1. **Tablet or emulator** (Pixel Tablet, or a resizable emulator):
   - Open an album, then its artist, then Back twice.
   - Rotate to portrait and back: the pane's screens should survive.
2. **TalkBack:**
   - Swipe through a song list. The playing song should say "Now playing".
   - Open the actions menu on a row (swipe up then right, or three-finger tap). You should
     find "Song options".
3. **Large text:** set Display size and Font size to the largest and look at Home, a playlist
   and the album screen.
4. **Keyboard:** with a keyboard connected, try Space, Ctrl+→ and Ctrl+F. Then type a space
   in search: it should type, not pause.
