# Redesign 2: song rows, library tabs, detail screens, queue, bottom sheets

This pass brings the library side of the app up to the same standard as Redesign 1. Every
song list, the Home tabs, the album, artist and playlist screens, the play queue and every
bottom sheet now share one set of components:

- `ui/components/LibraryKit.kt` has the song row, album tile, artist row and tile, and the now-playing bars.
- `ui/components/DetailKit.kt` has the album, artist and playlist screen layout.
- `ui/components/SheetKit.kt` has the bottom sheet parts.

All icons are Material Symbols Rounded (`res/drawable/ms_*.xml`). The light screens use theme
colours, so they follow the accent colour (including Match wallpaper) in light and dark mode.
The artwork-backed screens are always dark.

> **Not built yet.** Gradle can't run here, so run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
> Brackets, line endings and every string, plural and drawable reference were checked by script,
> in all five languages.

## Song rows (everywhere)

One `SongRow` draws every song in the app: Songs tab, album, artist, playlist, queue and Add songs.

- **Artwork:** 52 dp, rounded 12 dp.
- **Text:** title, then "Artist · 3:25". Most Played and Rediscover add the play count.
- **Now playing:** the playing song gets a dark scrim over its artwork with three animated
  bars, and its title is in the accent colour. The bars stop while playback is paused.
- **Album lists** show the track number instead of artwork. The playing track shows the bars
  in its place.
- **Selection mode:** a round check on the right, and the row is highlighted when selected.
- **Queue:** a remove button and a drag handle. Hold and drag the handle (right end) to move
  a song; swiping anywhere else scrolls the list. The row being dragged lifts onto a darker
  card. Songs already played are dimmed.
- **Menu:** a ⋮ button opens the song sheet. Long-press still starts multi-select where it
  did before.

`SongView` (light screens) and `QueueSongView` (dark screens) keep their old parameters and
draw a `SongRow`, so other callers did not change.

## Home tabs

**Songs**
- **Header:** the song count, a **sort chip** (sort icon plus the current order; the menu
  ticks the active order) and a **Shuffle** button that plays the whole library shuffled.
- The now-playing bars animate only while playing.

**Albums**
- Two-column grid of album tiles: square artwork, name, and "Artist · N songs".
- Artwork is loaded per album. The old view loaded it once per list slot, so scrolled items
  could show the wrong cover; that is fixed.

**Artists**
- **Phones:** a list with the artist's picture (or a coloured initial), name, "N albums · M songs"
  and a chevron.
- **Tablets:** a grid of large round pictures.

**Playlists**
- **Built-in playlists:** picture tiles with a dark gradient, a round icon badge, the name and
  the song count.
- **Custom playlists:** rows with an icon tile, name, "N songs · duration" and a ⋮ button.
  The section header has a count and an **Import** button. With no custom playlists, an
  empty-state card is shown.

The old `AlbumView` and `ArtistView` are removed.

## Album, artist and playlist screens

All three use the same layout:

- **Backdrop:** the artwork, blurred, behind the whole screen with a dark gradient.
- **Header:** large artwork with a soft shadow (round for artists, a 2×2 cover collage for
  playlists), the title, a subtitle, and "N songs · 1 h 12 min".
- **Play button:** a white **Play** button. It reads **Pause** when this album, artist or
  playlist is the one playing, and toggles playback.
- **Shuffle button:** a translucent **Shuffle** button.
- **Top bar:** a slim bar with a back arrow. Once the header scrolls away, the bar turns
  dark and shows the title.

**Album:** the songs show track numbers, and the playing track shows the bars.

**Artist:** an **Albums** row of album tiles (tap one to open it), then **Songs**.

**Playlist**
- **Top bar actions:** **Add songs** (custom playlists and Favourites) and **⋮ Playlist
  options** (custom playlists).
- **Empty playlists** show a short hint under the header.
- **Select or edit:** long-press, or Edit in the menu. The top bar shows "Selected: N" and
  **Done**.
- **Floating action bar:** a rounded bar at the bottom with **Select all**, **Play**, **Add
  to queue** and **Remove**, each with an icon.
- **Reordering** custom playlists: in edit mode, drag a song by its handle.

## Queue (player)

- **Now playing card:** the current artwork, a "NOW PLAYING" label, title, artist and the
  bars.
- **Up next header:** shows "N songs · 24 min" for what is left after the current song.
- **Header buttons:** two round buttons, **Save as playlist** and **Clear queue**, with the
  same dialogs as before.
- **Rows:** songs already played are dimmed, the current song shows the bars, and each row
  has remove and a drag handle.
- **Scrolling vs moving:** only the handle moves a song, so the list scrolls normally. (The
  old reorder helper started a drag on every touch because its long-press wait was commented
  out; that helper is fixed too, but the queue no longer uses it.)

## Bottom sheets

**Every sheet**
- Uses the standard drag handle; the hidden handles are removed.
- Keeps its content clear of the navigation bar.
- Starts with a **header**: artwork or an icon tile, a title and a subtitle.

**Song sheet**
- **Quick-action tiles:** **Play next**, **Add to queue** and **Favourite**. The Favourite
  tile is filled when the song is a favourite.
- **Actions below:** **Add to playlist ›**, Go to album, Go to artist, Share, and
  **Remove from playlist** (in red).
- **Add to playlist:** opens in the same sheet with a back row, **New playlist**, then your
  playlists.

**Playlist sheet**
- **Quick-action tiles:** **Play**, **Shuffle** and **Add songs**.
- **Actions below:** Open, Rename, Edit, Export as M3U, and **Delete** (in red). From the
  playlist screen, the header also shows the song count.

**New playlist sheet**
- Header tile, a rounded **Playlist name** field, and a full-width **Create** button.
- Errors show under the field.

**Sleep timer & speed sheet**
- Two sections, each with an icon tile and a live status line: "Stops in 12:31", or the
  current speed.
- Rounded chips. The selected speed has a tick.
- A running timer offers **+10 min** and **Turn off**.

## New strings (5 languages)

- `song_row_subtitle` ("%1$s · %2$s")
- `detail_pause`
- `done`
- `favourite`
- `cd_playlist_options`
- `playlist_empty_hint`
- `queue_up_next`
- plural `playlist_count`

## Files

- **New:** `ui/components/LibraryKit.kt`, `DetailKit.kt` and `SheetKit.kt`, plus 30
  Material Symbols drawables.
- **Rewritten:**
  - `SongView.kt`, `QueueSongView.kt`
  - `screens/Album.kt`, `screens/Artist.kt`, `screens/Playlist.kt`
  - `pages/Songs.kt`, `pages/Album.kt`, `pages/Artists.kt`, `pages/Playlists.kt`
  - `PlaylistEntityView.kt`, `SongActions.kt`, `PlaylistMenu.kt`, `PlaylistForm.kt`,
    `PlaybackOptionsSheet.kt`
- **Changed:**
  - `Playing.kt`: queue view.
  - `Home.kt`: shuffle all, play state, sheets.
  - `Main.kt`: now-playing ID passed to album and artist screens.
  - `DesignKit.kt`: `ArtworkThumb` decode size and placeholder colours.
- **Removed:** `AlbumView.kt`, `ArtistView.kt`.
