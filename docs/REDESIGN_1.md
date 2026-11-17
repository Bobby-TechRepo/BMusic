# Redesign 1 — Search, Settings, Sound effects, Listening stats

These four areas now share one visual language:

- rounded 24 dp cards on a soft `surfaceContainerLow` background;
- icons in tinted rounded squares;
- titles with a short explanation under them;
- the same artwork thumbnails, avatars, section headers and empty states, all from
  `ui/components/DesignKit.kt`.

Everything uses theme colours, so it follows the accent colour (including Match wallpaper)
and light and dark mode.

> **Not built yet.** Gradle can't run here, so run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
>
> **Tested here.** The new chart data code (`ListeningActivity`) was compiled with the real
> Kotlin compiler and its tests pass.

## Search

**On Home**
- The search icon in the top bar is replaced by a full-width rounded search bar
  ("Search songs, albums, artists and playlists") above the tabs. Tapping it opens search.

**The search screen**
- **Field:** the same rounded field with a search icon, a clear button and a back arrow. It
  gets the keyboard straight away, and the keyboard hides when you scroll the results.
- **Before typing:** recent searches, each with a remove button, plus **Clear**. A query is
  saved when you open a result or press search on the keyboard (last 8, stored in the app
  settings). With no history yet, an empty-state message appears instead.
- **Filter chips** (All / Songs / Albums / Artists / Playlists), each with its count. Only
  kinds that have results appear.
- **All shows, in order:**
  - **Top result:** a large card. An exact artist or album name match wins, otherwise the
    best song. Songs get a play button.
  - The first four songs, with artwork, artist · album, a now-playing marker and the song menu.
  - Albums as a horizontal row of artwork cards.
  - Artists as a row of round avatars.
  - Playlists.
  - Each section has **See all**, which switches to its filter.
- **Matches are highlighted:** the matched text is bold and in the accent colour.
- **No results:** a clear message with a suggestion.

## Settings

- **Large collapsing title bar.** On tablets the content is centred and capped at 640 dp.
- **Groups:** Appearance, General, Library, Playback, Backup & restore, About. Each setting has
  an icon and a one-line explanation.
- **Switch rows:** the whole row toggles, and screen readers announce it as a switch.
- **Language** opens a dialog with radio buttons instead of a dropdown.
- **Accent colour:** swatches with a ring and check mark on the chosen one.
- **Volume levelling** is a segmented Off / Per song / Per album control.
- **Backup & restore** are two tappable rows, with a spinner while working. The result dialog
  is unchanged.
- **About** shows the app version.

## Sound effects: rotary knobs

- Bass boost, Surround and Loudness are now **round knobs** (`RotaryKnob`) with the level
  printed in the middle.
- **To change a level, put a finger on the knob and turn it in a circle.** Clockwise raises it,
  anti-clockwise lowers it, through 270° like a real amplifier knob.
  - Turning is relative, so the knob never jumps to where you touched.
  - Small turns add up.
  - Crossing 3 o'clock is handled.
- **Double-tap** resets a knob to 0.
- **Look:**
  - a level arc with a gradient from the tertiary to the accent colour;
  - 20 tick marks that light up as the level rises;
  - a metal-look knob with a pointer dot.
- **Feel:** a light haptic click every 5 %.
- **Accessibility:** screen readers read the label and value. Their adjust gestures change the
  level, through the standard progress semantics.
- Levels apply live while turning and are saved when you let go, as with the old sliders.
- Knobs shrink to fit three in a row on narrow phones.

## Listening stats

- **Large collapsing title bar.** A segmented control picks 7 days / 30 days / All time.
- **Hero card:** total listening time in large type on an accent gradient, with the number of
  plays and the **daily average** under it.
  - For all time, the average counts from the first listen.
- **Tiles:** Plays and Songs, each with an icon.
- **Activity chart**, a new single-colour bar chart:
  - **Bars:** one per day for 7 or 30 days, one per month (last 12) for all time.
  - **Labels:** weekday letters for a week, every 7th day for a month, month initials for all
    time. Two dashed guide lines show the scale.
  - The busiest bar is named under the title.
  - **Tap a bar** to read its date and plays.
  - Bars grow in when the period changes.
  - Data comes from a new `ListenEventDao.playsPerDay` query, grouped by local day.
- **Top songs and Top artists:**
  - ranked rows, with the top three numbers in the accent colour;
  - artwork for songs, initial avatars for artists;
  - the play count;
  - a thin bar comparing each entry with number one.
- **Empty state:** icon, title and explanation.

## Other changes

- `BitmapImage` no longer tells screen readers every picture is a "Logged user profile picture".
  Artwork is decorative by default.
- New Material Symbols Rounded icons (`ms_*`, Apache 2.0).

## Tests

| Test | Covers |
| --- | --- |
| `ListeningActivityTest` | 7-day bars ending today with zero-filled gaps, 12 monthly bars (older plays excluded), busiest bar |
| `ListeningStatsRepositoryTest` (updated) | Activity bars and daily average for a week; all-time days counted from the first listen |
| `DesignKitTest` | Knob rotation across 0°/360°, match highlighting and no-match cases |

## Check on a device

1. **Home:** tap the search bar. Type part of an artist's name, then try the chips and See all.
   Open a result and go back: the query now appears under Recent searches.
2. **Player → Equalizer page:**
   - Turn the bass knob slowly, then quickly. You should feel the clicks and hear the bass
     change.
   - Double-tap to reset it.
   - Try TalkBack's volume-key adjust on a knob.
3. **Settings:** check light and dark mode, Match wallpaper, the language dialog, and a backup.
4. **Listening stats:** switch periods, tap bars, and check the chart in Japanese (narrow labels).
