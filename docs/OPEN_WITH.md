# Open with BMusic

BMusic now appears in Android's "Open with" list for music files. It also plays them when
it's the default app, from file managers, Downloads, chat apps (WhatsApp, Telegram) and email
attachments.

> **Not built yet.** Gradle can't run here, so please run
> `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
>
> **Tested here.** The rules that decide what plays were compiled with the real Kotlin
> compiler, and their 4 tests pass (`OpenedFileTest`).

## What it opens
Any `audio/*` file: MP3, AAC/M4A, FLAC, OGG/Opus, WAV, AMR, MIDI and so on. It also covers
the older `application/ogg` and `application/x-flac` types some apps still send. Both
`content://` links (the modern kind) and `file://` links are accepted.

## What plays
- **A song from your library**, for example opened from the system's Files app or Audio
  picker:
  - it plays **with the rest of its folder**, starting at that song, just like tapping it in
    the Folders tab;
  - next and previous move through the folder;
  - the queue survives a restart.
- **A file from elsewhere** (a download, a chat attachment, a file manager's link):
  - it plays **on its own**;
  - its title, artist, album and cover come from its tags, or the file name if it has none;
  - the notification and lock screen show them;
  - it doesn't count towards play counts or stats, and isn't saved for "resume after
    restart". Other apps only lend BMusic access while it's open, so it couldn't be reopened
    later.

In both cases the player screen opens straight away. If BMusic was closed, it waits until the
player service has started, then plays.

## Behaviour notes
- **One window:** BMusic opens as a single window (`launchMode="singleTask"`). Opening a
  file while BMusic is already open switches to it and plays the file, instead of starting a
  second copy inside the file manager.
- **Rotation:** rotating the screen doesn't replay the file.

## Files
- **`AndroidManifest.xml`:** a VIEW intent filter for audio, and single-window launch.
- **`data/playback/OpenedFile.kt`:** matches a library song by MediaStore ID or file path,
  and chooses folder playback. Tested in `OpenedFileTest`.
- **`viewmodels/OpenedFileViewModel.kt`:** reads the tags of files from outside the library.
- **`MainActivity.kt`:** takes the opened file on start and when BMusic is already open.
- **`Main.kt`:** plays it and shows the player.
- **`PlayingViewModel.kt`:** `playItem()` plays a single file.

## Please try
1. In the Files app, open an MP3 or M4A in your Music folder and choose **BMusic** (or make
   it the default). It should play, and Next should move to the next song in that folder.
2. Open a downloaded or WhatsApp audio file. It should play on its own, with its title.
3. With BMusic already playing, open another file. BMusic should switch to it.
