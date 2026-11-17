# Phase 6 — Release readiness

The app's features are complete, so this phase prepares BMusic for Google Play: a smaller signed
release build, sensible device backups, a way to learn about crashes without any tracking, an
automated release pipeline and the store paperwork. The publishing steps are in `docs/RELEASE.md`.

> **Not built yet.** Gradle can't run here. Most important: run `./gradlew :app:assembleRelease`
> and check the release build on a phone (the list is in `RELEASE.md`), because R8 is now on.
>
> **Tested here.** The crash-report format was compiled and tested here.

## 1. Release build

**R8 and resource shrinking**
- Code and resource shrinking (`isMinifyEnabled`, `isShrinkResources`) are on for release builds,
  so the app is smaller and starts faster.
- `proguard-rules.pro` keeps:
  - line numbers, so crashes stay readable with the mapping file;
  - the serializers of the app's `@Serializable` types (settings, snapshots, backups);
  - enum lookups for values saved by name;
  - the Navigation 3 route names, so the back stack survives process death.
- Room, Hilt, Media3, Compose and DataStore bring their own rules.

**Signing**
- Signing reads `keystore.properties` (see `keystore.properties.example`), or `BMUSIC_*`
  environment variables on CI.
- Without either, release builds stay unsigned instead of failing.
- Keystores and the properties file are git-ignored.

**Dependencies**
- `media3-ui`, `datastore-preferences` and `datastore-rxjava3` were removed. They were never
  used, since the UI is Compose and settings use the typed DataStore.

## 2. Device backups

- The Auto Backup rules for Android 11 and below, and the data-extraction rules for Android 12+
  (cloud backup and phone-to-phone transfer), were empty templates. They now list what is
  backed up:
  - settings and equalizer;
  - the library database (playlists, favourites, counts, history);
  - imported lyrics.
- Left out:
  - the playing queue and position, which point at this phone's MediaStore IDs and would be
    wrong on a new phone;
  - crash reports;
  - the widget's cached song.

## 3. Crash reports, without tracking

- `CrashReporter` saves the last crash on the phone: time, app version, phone model, Android
  version and the stack trace. It holds no personal data and no file names.
- After a crash, **Settings → About** shows **Send crash report**. It opens the share sheet, so
  the user picks how and to whom to send it. The ✕ deletes the report.
- Nothing is sent automatically. BMusic still has no internet permission, which keeps the Play
  "Data safety" answer simple: no data collected.
- `CrashReportFormat` is pure Kotlin and unit-tested. It caps reports at 32,000 characters so
  deep recursion can't produce huge files.

## 4. Release pipeline and lint gate

**`.github/workflows/release.yml`**
- Pushing a `v*` tag runs the unit tests, then builds a signed `.aab` (for Play) and `.apk`
  (for sideloading).
- It names the files with the version and keeps the R8 `mapping.txt`.
- It attaches all three to a GitHub release, with the English "What's new" text as the notes.
- Versions:
  - the versionName comes from the tag;
  - the versionCode is `100 + run number`, so it always increases.
- It fails clearly if the signing secret is missing.

**Lint**
- Lint now uses a baseline:
  - create it once with `./gradlew :app:lintDebug -PcreateLintBaseline`, then commit
    `app/lint-baseline.xml`;
  - after that, any new lint problem fails CI.
- Until the baseline exists, lint only reports, so nothing is blocked.

## 5. Store materials

- **Play Store listing**, in `fastlane/metadata/android/` for en-US, es-ES, fr-FR, ja-JP and
  zh-CN: title, short description, full description and "What's new". This is the standard
  fastlane layout, also read by Gradle Play Publisher and F-Droid.
- Lengths were checked against Play limits: 30 characters for the title, 80 for the short
  description, 4,000 for the full description and 500 for "What's new".
- **`docs/PRIVACY.md`:** a privacy policy that matches what the app really does. Fill in your
  contact email before publishing it.
- **`docs/RELEASE.md`:**
  - one-time setup (upload key, Play App Signing, CI secrets, lint baseline, Room schemas);
  - the per-release checklist, including a device test of the R8 build;
  - Play Console answers (Data safety, ads, audience, content rating, foreground-service
    declaration);
  - the screenshots to make.

## Tests

| Test | Covers |
| --- | --- |
| `CrashReportFormatTest` | Header lines (UTC time, version, device, Android, thread), causes included, very long reports cut short |

## Still open, by choice

- **Material 3 alpha:** the app uses `1.5.0-alpha27`. Move to the stable 1.5 release when it
  ships, and retest the redesigned screens.
- **Baseline Profiles:** faster first launch, but they need a new module and a device to
  generate them. Worth adding once releases are flowing.
- **Pro / billing:** left out, as you asked.
