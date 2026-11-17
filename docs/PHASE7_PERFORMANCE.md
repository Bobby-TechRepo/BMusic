# Phase 7: Performance and quality

This phase makes BMusic start faster, scroll more smoothly and use less memory on older
phones, and adds device tests for the redesigned screens. Phase 8 (new listening features)
and Phase 9 (tablets and accessibility) follow.

> **Not built yet.** Gradle can't run here. Please run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
>
> **Tested here.** The new artwork sampling code was compiled with the real Kotlin compiler
> and its 5 tests pass.
>
> **Needs a device.** The device UI tests and the profile tools below need a phone or
> emulator.

## 1. Baseline Profile (faster start-up and first scroll)

A Baseline Profile lists the code a user hits first: start-up, the Home tabs, opening an
album, starting a song. Android compiles that code ahead of time when the app is installed,
instead of interpreting it on the first runs. Google measures about 30% faster code on first
launch with one, and the first scrolls drop fewer frames.

**New `:baselineprofile` module**
- `BaselineProfileGenerator` launches BMusic and goes through the main screens:
  - it scrolls Songs;
  - it opens an album and an artist;
  - it looks at Playlists;
  - it plays a song and opens the player.
- Every step is optional, so an empty library or a tablet layout still produces a profile.
- It also marks start-up code for the Startup Profile, which lays out the app's code so
  start-up reads less of it.
- `StartupBenchmark` measures the gain: cold start-up and Songs scrolling, each without and
  with the profile.

**In the app**
- `androidx.profileinstaller` is added. It installs the profile on APKs you sideload too, not
  only on installs from Play. This also covers the profiles the Compose libraries ship, so
  every install gets faster even before you generate ours.
- The Baseline Profile plugin adds two build types made from `release`: `benchmarkRelease`
  and `nonMinifiedRelease`. Without your release key they are signed with the debug key, so
  they install on your test phone.
- Screens carry stable test tags, listed in `ui/components/TestTags.kt`. They are exposed to
  UI Automator, which has no effect for users.

**Generating the profile** (once now, then again when screens change)
1. Connect a phone with **Android 13 or newer** and some music on it. A rooted Android 9 to
   12 phone also works.
2. Run `./gradlew :app:generateReleaseBaselineProfile`. This takes a few minutes, and the
   app opens and moves by itself.
3. Commit `app/src/release/generated/baselineProfiles/`. Release builds pick it up
   automatically.

**Measuring:** `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`. Compare
`startupWithoutProfile` and `startupWithProfile` (timeToInitialDisplayMs) in the test
results.

`docs/RELEASE.md` now includes these steps in the one-time setup and the per-release
checklist.

## 2. Artwork on Android 8 and 9

Android 10+ asks the system for a thumbnail at the right size. On Android 8 and 9
(minSdk 26) the app had two problems:

- **Album covers** were decoded at full size. A 3000 × 3000 cover is about 36 MB in memory,
  just to show it at 160 px. They are now decoded scaled down: that cover now decodes at
  187 × 187, about 140 KB.
- **Song artwork never showed.** The app tried to read the song file as if it were an
  image. Now it reads the picture embedded in the song's tags and scales it the same way.

How it works:
- `artworkSampleSize()` picks the largest scale-down that still leaves the image at least as
  big as where it's shown, on both sides. It is pure Kotlin and unit-tested.
- A file that can't be read (removed, or no permission) is now treated like "no artwork"
  instead of failing.

## 3. Smoother lists

- **Compose stability config (`app/compose_stability.conf`).** `Uri`, `MediaItem` and
  `MediaMetadata` never change once created. Telling the Compose compiler this lets song
  rows, tiles and the queue skip redrawing when nothing changed.
- **Queue:** each queue item is converted to a song once, instead of on every redraw of
  the list.

## 4. Material 3 release candidate

Material 3 moved from `1.5.0-alpha27` to **`1.5.0-rc01`**, released 4 November 2026. A
release candidate is the planned final version and its API won't change. The app used no
alpha-only APIs. Please look over the redesigned screens once, as a few component defaults
may differ slightly. Move to `1.5.0` when it ships.

## 5. Device tests for the redesigned components

Run them with `./gradlew :app:connectedDebugAndroidTest` with a phone or emulator connected.

| Test | Covers |
| --- | --- |
| `SongRowTest` | Title and "artist · duration"; row tap and ⋮ menu; selection mode hides the menu and reports selected or not to TalkBack; the queue row's remove button and drag handle; no handle unless asked; track numbers on album lists |
| `SheetsTest` | Sheet header; quick-action tiles and action rows are clickable; the playlist sheet passes the right playlist to Play, Shuffle, Add songs, Rename and Delete; optional actions are hidden; New playlist's Create stays off until a name is typed, and the name is trimmed |
| `ArtworkSamplingTest` (JVM) | Large covers scale down but never below the target; small images aren't scaled; the shorter side decides; bad sizes fall back to 1 |

## Also changed

- **Reorder hint:** the TalkBack label for the drag handle is now "Drag to reorder" (it said
  "Long-press and drag") in all five languages.

## Files

**New**
- `baselineprofile/`: the module, with `BaselineProfileGenerator`, `StartupBenchmark` and
  `Journeys`.
- `app/compose_stability.conf`
- `ui/components/TestTags.kt`
- `utils/ArtworkSampling.kt`
- Tests: `SongRowTest`, `SheetsTest`, `ArtworkSamplingTest`.

**Changed**
- Build: `libs.versions.toml` (Material 3, benchmark 1.5.0, profileinstaller 1.4.1,
  UI Automator 2.4.0), `settings.gradle.kts`, the root `build.gradle.kts` and
  `app/build.gradle.kts`.
- Code:
  - `Util.kt`: artwork scaling on Android 8 and 9.
  - `MainActivity.kt`: test tags exposed.
  - Test tags on the Home tabs, lists, rows, tiles, detail screens and mini player.
  - `Playing.kt`: queue conversion.
- `strings.xml` (5 languages): the reorder hint.
- `docs/RELEASE.md`: the Baseline Profile steps.
