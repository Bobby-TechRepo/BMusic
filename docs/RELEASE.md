# Releasing BMusic

This is a step-by-step checklist for publishing to Google Play.

## One-time setup

1. **Create the upload key** and keep it (and its passwords) somewhere safe outside the repo:
   ```
   keytool -genkeypair -v -keystore release.jks -alias bmusic -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Local signing:** copy `keystore.properties.example` to `keystore.properties` and fill it
   in. Both `keystore.properties` and `*.jks` are git-ignored.
3. **CI signing:** add these repository secrets (Settings → Secrets and variables → Actions):
   - `BMUSIC_KEYSTORE_BASE64`: the output of `base64 -w0 release.jks`
   - `BMUSIC_KEYSTORE_PASSWORD`
   - `BMUSIC_KEY_ALIAS`
   - `BMUSIC_KEY_PASSWORD`
4. **Play Console:** create the app and turn on **Play App Signing**. Google then holds the app
   signing key, and your key is only the upload key.
5. **Lint gate:** run `./gradlew :app:lintDebug -PcreateLintBaseline`, then commit
   `app/lint-baseline.xml`. From then on, new lint problems fail CI.
6. **Room schemas:** commit `app/schemas/` after a build so future database migrations can be
   tested against them.
7. **Baseline Profile:** with a phone on Android 13+ connected (with some music on it), run
   `./gradlew :app:generateReleaseBaselineProfile` and commit
   `app/src/release/generated/baselineProfiles/`. See `docs/PHASE7_PERFORMANCE.md`.

## Every release

0. **Refresh the Baseline Profile** if screens or start-up changed since the last release:
   `./gradlew :app:generateReleaseBaselineProfile`, then commit the updated files.
1. **Test the release build on a device first:** `./gradlew :app:assembleRelease`. R8 is on, so
   check the following on a real phone:
   - a library loads;
   - search works;
   - playback continues in the background;
   - the widget works;
   - Android Auto works (Desktop Head Unit);
   - backup, then restore;
   - lyrics show;
   - stats show;
   - Settings, including the language switch.
2. **Update the "What's new" text** in `fastlane/metadata/android/<locale>/changelogs/default.txt`
   (500 characters at most per language).
3. **Tag and push:**
   ```
   git tag v1.2.0
   git push origin v1.2.0
   ```
   The **Release** workflow tests, builds and signs, then publishes a GitHub release with:
   - `BMusic-1.2.0.aab`, for Play;
   - `BMusic-1.2.0.apk`, for sideloading;
   - `mapping-1.2.0.txt`.
   The versionCode is `100 + run number`, so it always increases.
4. **Upload to Play:**
   - upload the `.aab` to the internal testing track first, then promote it;
   - upload `mapping-*.txt` under **App bundle explorer → Downloads → ReTrace mapping file**,
     so crash stack traces are readable.

## Play Console answers

**Store listing.** Copy from `fastlane/metadata/android/`:
- title (30 characters at most);
- short description (80 at most);
- full description;

for en-US, es-ES, fr-FR, ja-JP and zh-CN.

**Graphics you still need to make:**
- 512×512 icon;
- 1024×500 feature graphic;
- at least 2 phone screenshots;
- tablet screenshots, for large-screen listing quality.

Suggested screenshots:
1. Player;
2. Lyrics;
3. Equalizer with the knobs;
4. Search;
5. Listening stats;
6. Widget on the home screen.

**Privacy policy.** Host `docs/PRIVACY.md` (for example with GitHub Pages) and paste its URL.
Fill in the contact line first.

**Data safety:**
- Does the app collect or share user data? **No.** There is no network access and no SDKs that
  collect data.
- Is data encrypted in transit? **Not applicable.** Nothing is sent.
- Can users request deletion? Data stays on the device; clearing app storage or uninstalling
  deletes it.

**App content:**
- Ads: **No.**
- Target audience: 13+ (or "all ages"; nothing in the app is age-restricted).
- Content rating questionnaire: music player, no user-generated content, no communication
  features.

**Permissions and foreground service.** Declare the **media playback** foreground service type:
it plays audio the user started, in the background. Play may ask for a short video: start a
song, leave the app and show it keeps playing with the notification.

## Known items to watch

- `androidx.compose.material3` is on an **alpha** (`1.5.0-alpha27`). Move to the first stable
  1.5 release when it ships, and retest the redesigned screens.
- **Baseline Profiles** would speed up first launch and scrolling further. They need a separate
  `:baselineprofile` module and a device or emulator to generate them, so they can be added once
  the release pipeline is in use.
