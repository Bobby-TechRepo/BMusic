# Phase 8: Folders, tag editor, crossfade

Phase 8 adds three listening features:

- a **Folders** tab, to browse music the way it's stored;
- a **tag editor** that writes into the music files themselves;
- **song transitions**, with both a fade and a true crossfade, picked in Settings.

Phase 9 (tablets and accessibility) comes next.

> **Not built yet.** Gradle can't run here, so please run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
>
> **Tested here.** The new pure-Kotlin logic was compiled with the real Kotlin compiler, and
> its 26 tests pass: folder tree (8), transitions (10) and tag edits (8).
>
> **Needs a real phone.** The tag editor and the crossfade work with Android's media system,
> so please try them on a device (checklist at the end).

## 1. Folders tab

A fifth Home tab, **Folders**, sits after Artists. On tablets it's in the navigation rail.

- **Layout:**
  - At the top, a **breadcrumb trail** (🏠 Folders › Music › Rock). Tap any part to jump
    back up, and the system Back button goes up one folder.
  - A **header** with the folder's name, its total song count, and **Shuffle** and **Play**
    buttons.
  - The **sub-folders**: each row has an icon tile, the name, "N songs · M folders" and a
    chevron.
  - Then the **songs** directly in the folder. They use the normal song row, with the
    now-playing bars and the ⋮ menu.
- **Storage:** if all the music is on one storage volume, its folders show directly.
  Otherwise the top level shows **Internal storage** and **SD card (ID)**.
- **What plays:**
  - Play and Shuffle take the folder **and everything below it**. The folder's own songs go
    first, then each sub-folder in name order.
  - Tapping a song plays from that song through the same list.
  - Sorting ignores case.
- **Resuming after a restart:** queues started from a folder resume correctly. They are
  saved as `folder_<id>`, where the ID is a stable hash of the folder path.
- **Moved or deleted folders:** if the open folder disappears, the tab falls back to its
  nearest parent.

## 2. Tag editor (writes to the files)

The song ⋮ menu has a new item, **Edit tags**. It appears on Android 11 and newer, for
MP3, FLAC, OGG, M4A/MP4/M4B, WMA, WAV, AIFF and DSF files. Opus and raw AAC have no tag
writer, so the item is hidden for them.

**What you can edit**
- Title, Artist, Album, Album artist, Genre, Year, Track, Disc and Lyrics.
- The **cover picture**: change it with the photo picker, or remove it. A new picture is
  scaled down to at most 1200 px and saved as JPEG.

**How a save works**
1. **Save** is enabled once something has changed and every field is valid:
   - the year is 4 digits, or a full date such as 2021-05-14;
   - track and disc are numbers, and "3/12" is accepted;
   - the title isn't empty.
2. Android shows its own dialog: **"Allow BMusic to modify this audio file?"**. This is how
   Android 11+ lets apps change other apps' media, and there is no way around it.
3. BMusic copies the song into its own storage, edits the copy with jaudiotagger, and
   writes it back through MediaStore.
   - If writing back fails part-way, it writes the untouched original back, so a song is
     never left half-written.
4. Android re-scans the file, and BMusic refreshes everything that showed the old tags:
   - the library;
   - artwork, ReplayGain and lyrics caches.

**If the song is playing**
- Playback steps aside while the file is rewritten.
- It then reloads from the new file at the same position.
- The notification, lock screen and app show the new title, artist and cover straight
  away.

**Other details**
- If you go back with unsaved changes, BMusic asks **"Discard changes?"**.
- The bottom of the editor shows the format, sample rate, bit rate and file path.

**New library:** `net.jthink:jaudiotagger:3.0.1`, from Maven Central.
- **R8:** the release shrinker keeps the whole library, because it loads tag classes by
  name. Warnings about desktop Java APIs it never uses on Android are silenced.
- **Licence:** jaudiotagger is **LGPL 2.1**. Using it as a separate, unmodified library is
  allowed, but credit it in the store listing or an "Open-source licences" page.

## 3. Song transitions: Off, Fade, Crossfade

**Settings › Playback › Song transitions** replaces the old "Sound fades as songs end"
switch. If that switch was on, the new setting starts on **Fade**.

| Choice | What you hear |
| --- | --- |
| **Off** | Each song starts right after the last (gapless if the files are) |
| **Fade** | The ending song fades out over the chosen length, then the next fades in, with no overlap |
| **Crossfade** | The next song starts while the ending one fades out, overlapping for the chosen length |

**Options**
- **Length:** a 1 to 12 second slider, shown for Fade and Crossfade. The default is 6 s.
- **Keep albums gapless:** shown for Crossfade, and on by default. When an album plays in
  order, its tracks still run straight into each other, which matters for live albums,
  DJ mixes and concept albums. This includes the jump from the last track of disc 1 to the
  first of disc 2.

**How true crossfade works.** Media3's player plays one song at a time, so the overlap
comes from a second, private "tail" player (`services/CrossfadeEngine.kt`):
1. **Preparing:** about 4 s before the overlap, the tail player loads the *current* song,
   paused at the point where the overlap starts.
2. **Starting the overlap:** at that point the tail starts playing. As soon as it makes
   sound, the main player mutes and moves to the next song. The notification, lock screen,
   Bluetooth and Android Auto simply see the next song start.
3. **The overlap:** the old song fades out on the tail while the new one fades in.
   **Equal-power curves** keep the overall loudness even through the overlap, with no dip
   in the middle. The tail then stops.

**What the tail player shares and doesn't**
- It uses the **same audio session**, so the equalizer, bass boost and the other effects
  apply to both songs.
- It keeps the song's **ReplayGain** level, the **sleep timer**'s fade, and the playback
  **speed**.
- It **never takes audio focus**. Pausing, a call, unplugging headphones, skipping or
  seeking cuts the overlap at once, and the new song carries on alone.

**When there is no crossfade**
- With repeat-one on.
- At the end of the queue.
- When the sleep timer is set to stop at the end of the song, so that song ends on its own.
- When the song's length is unknown.
- On very short songs: an overlap is never more than a third of a song, and an overlap
  under 0.5 s is skipped.
- When slow storage hasn't buffered the tail in time. The song then plays out normally
  rather than stuttering.

To support "Keep albums gapless", queue items now carry their track number.

## Files

**New**
- **Pure logic:**
  - `data/models/Folders.kt`: the folder tree, volumes and stable folder keys.
  - `data/playback/Transitions.kt`: the modes, lengths, the crossfade decision, gapless
    albums and the curves.
  - `data/tags/TagEdits.kt`: tag values, changes, validation and supported formats.
- **Android code:**
  - `data/tags/TagEditorRepository.kt`: read, the write request, the safe write and the
    refresh.
  - `data/playback/PlaybackFileGuard.kt`: steps playback aside while a file is rewritten.
  - `services/CrossfadeEngine.kt`
- **Screens:** `ui/pages/Folders.kt`, `ui/screens/TagEditor.kt` and
  `viewmodels/TagEditorViewModel.kt`.
- **Tests:** `FolderTreeTest`, `TransitionsTest`, `TagEditsTest`.
- **Resources:** nine icons, and 40 strings plus one plural in all five languages.

**Changed**
- `PlaybackService.kt`: the transition modes in the volume loop, the crossfade engine, the
  file guard and folder queues.
- `MediaId.kt`: `MediaSource.Folder`.
- `Song.kt`: queue items carry the track number.
- Settings: `AppSettingsData`, `AppSettingsPreferences` and `SettingsViewModel` (with
  their tests), and `Settings.kt` (the transitions UI).
- Navigation: `Navigation.kt` (Folders tab and the TagEditor route) and `Home.kt`.
- `Main.kt` and `SongActions.kt`: the Edit tags action.
- Caches: `Util.kt`, `AudioTagRepository.kt` and `LyricsRepository.kt` can now forget one
  song.
- Build: `libs.versions.toml`, `app/build.gradle.kts` and `proguard-rules.pro`.
- The Baseline Profile journey now visits Folders.

## Please try on a phone

1. **Folders:** open a few levels deep, use Back, use the breadcrumbs, Play a folder, then
   close the app and reopen it. The queue should resume.
2. **Tag editor:** edit an MP3 and a FLAC, change the cover, and approve the system dialog.
   - Check the change in BMusic and in another music app.
   - Do it once on the song that is playing.
3. **Crossfade:** set 8 s and play a mixed queue. You should hear an overlap, and the
   equalizer should still apply.
   - Pause during an overlap, and skip during one.
   - Play an album in order with "Keep albums gapless" on: there should be no crossfade
     between its tracks.
4. **Fade:** check the fade-out length follows the slider.
