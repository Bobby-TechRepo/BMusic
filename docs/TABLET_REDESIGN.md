# Tablet redesign

Tablets get their own layout, locked to landscape. Phones are unchanged.

```
┌──────┬──────────────────────┬│┬──────────────────────────────────────────┐
│ rail │ Library section      │││ Player (or Settings / Listening stats)   │
│      │ phone-sized          │││                                          │
│ 🔍   │ Playlists · Songs ·  │││  Now playing        [≡] [stats] [⚙]      │
│ ▤    │ Albums · Artists ·   │││                                          │
│ ♪    │ Folders, plus Album, │││          artwork + controls    ┌───────┐ │
│ ◉    │ Artist, Playlist,    │││                                │ panel │ │
│ 👤   │ Search, Add songs,   │││                                │ Queue │ │
│ 📁   │ Tag editor           │││                                │ Lyrics│ │
│      │                      │││                                │ EQ    │ │
└──────┴──────────────────────┴│┴────────────────────────────────┴───────┴─┘
```

## When it applies

- **Orientation:** devices with a smallest width of at least 600 dp are locked to
  `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, and phones stay portrait (`MainActivity`).
- **Layout:** the tablet layout is used whenever the window is *Expanded* (at least 840 dp
  wide). On Android 16+, which can ignore orientation locks on large screens, the layout still
  follows the window: a portrait tablet window (Medium) gets the phone layout.

## Sections (`ui/Main.kt`)

1. **Navigation rail** (`TabletRail`): a Search button, then the five library tabs. Tapping a tab
   returns the library section to Home and shows that tab. Search has no back arrow on tablets, since the rail is always on
   screen (system back still works).
2. **Library section**: 34% of the width, clamped to 360–420 dp, so it is phone-sized. It has its
   own back stack (`rootBackStack`) with Home and every library screen. Its pages get a Compact
   `WindowSizeClass`, so they use their phone grids. On tablets Home has no tab chips, no
   Stats/Settings buttons and no mini player (`HomeScreen(tabletLibrary = true)`).
3. **Divider.**
4. **Right section**: its own back stack (`rightStack`), which always has the player at the
   bottom. Settings and Listening stats are the only screens swapped in for it. Their back arrow
   (and system back) returns to the player, and opening one replaces the other.

`navigate(route)` decides the side: on tablets, Playing, Settings and Stats go right and
everything else goes left. When the window changes between phone and tablet sizes,
`splitForTablet` / `mergeForPhone` (`utils/Panes.kt`, covered by `PanesTest`) move screens
between the single phone stack and the two tablet stacks. This replaces the Phase 9 two-pane
library.

## Player on tablets (`PlayingScreen(persistent = true)`)

- The header has "Now playing" and three buttons: **panel toggle**, **Listening stats** and
  **Settings**. There is no back arrow, because the player is always on screen.
- The controls sit centred, at most 620 dp wide.
- **Overlay panel:** one button slides a panel over the player's right side. It has the tabs
  Queue | Lyrics | Equalizer, and the same button (or back) closes it. It is 58% of the player's
  width, clamped to 380–560 dp. Lyrics was kept as a third
  tab so it is still reachable on tablets.
- **Welcome card** when nothing is queued: an artwork placeholder, "Pick something to play",
  **Shuffle all** (the whole library) and **Browse songs** (opens the Songs tab on the left). The
  Stats/Settings buttons stay in the header.
- The status bar is shared with the library, so the player doesn't force light icons there.

## Strings

`cd_queue_panel`, `player_welcome_title`, `player_welcome_body`, `player_welcome_browse` and
`shuffle_all` were added in all five locales. The Phase 9 `pane_empty_*` strings were removed.
