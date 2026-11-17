# Home retouch

A visual refresh of the Home screen's frame. The tab pages themselves (Playlists, Songs,
Albums, Artists and Folders) are unchanged.

> **Not built yet.** Gradle can't run here, so please run `./gradlew :app:assembleDebug`.

## Header (`ui/components/HomeKit.kt`)
- **Greeting and title:** a greeting for the time of day ("Good morning", "Good afternoon",
  "Good evening"), with **BMusic** in large bold type underneath. TalkBack treats the title as
  a heading.
- **Shortcuts:** **Stats** and **Settings** are round, tinted 48 dp buttons with Material
  Symbols icons, replacing the old mixed icon styles.
- **Search:** the search pill moved into the header. The header and pages now share one
  background, instead of a white bar over a tinted area.

## Tabs (phones)
- **Pill tabs:** the fixed tab row is replaced by pills that scroll sideways, each with an
  icon and its full label, so "Playlists" is no longer cut short to "Playli…". The selected
  pill is filled with the accent colour and animates as it changes.
- **Following the pages:** swiping between pages selects the matching pill, and scrolls it
  into view if it is off screen.
- **TalkBack** announces the pills as tabs, with the selected one marked.
- **Navigation rail:** the tablet rail uses the same Material Symbols icons, filled when
  selected.

## Mini player (`BottomControl.kt`)
- **Floating card:** a rounded card that floats above the navigation bar, with a soft shadow.
- **Content:**
  - 48 dp artwork;
  - the title, in normal text colour instead of the accent;
  - the artist, showing "Unknown artist" instead of "Artist: <unknown>";
  - a round **play/pause** button and a **next** button;
  - a thin rounded progress line along the bottom of the card.
- **Hidden** until something is in the queue, so an empty "Unknown" bar no longer sits at the
  bottom.

## New playlist button
- Rounded-square button in the accent's container colour, with the Material Symbols
  "playlist add" icon.

## Resources
- **Icons:** settings, skip next, and filled versions of queue music, music note, album and
  person.
- **Strings:** 4 new (the three greetings and "Unknown artist"), in all five languages.
