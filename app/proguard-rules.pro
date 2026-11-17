# R8 rules for BMusic release builds.
#
# Most libraries ship their own rules (Room, Hilt, Media3, Compose, DataStore, Navigation 3,
# kotlinx.serialization), so this file only holds what is specific to this app. After changing
# it, check a release build on a device: ./gradlew :app:assembleRelease

# Readable crash reports: keep line numbers, hide the source file names. Upload
# app/build/outputs/mapping/release/mapping.txt to Play Console with each release so stack
# traces can be de-obfuscated.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the app's @Serializable classes are stored on disk (settings,
# playback snapshot, backups) and in the navigation back stack. The library's bundled rules
# cover generated serializers; also keep the companion serializer() lookups for our own types.
-keepclassmembers @kotlinx.serialization.Serializable class com.bsoft.compose.bmusic.** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.bsoft.compose.bmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum values are saved by name (ReplayGainMode, StatsPeriod, sort orders); R8 keeps the
# names Enum.name returns, but keep the constants so valueOf()/entries lookups stay intact.
-keepclassmembers enum com.bsoft.compose.bmusic.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** getEntries();
}

# Navigation 3 saves the back stack (our Route classes) for process death and restores it by
# class; keep their names so a restored stack still finds them.
-keepnames class com.bsoft.compose.bmusic.utils.Route
-keepnames class com.bsoft.compose.bmusic.utils.Route$*

# jaudiotagger (tag editor). It loads ID3 frame classes by name, so keep the library whole, and
# it references desktop Java APIs (AWT, ImageIO, Swing) only on code paths Android never takes.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn javax.sound.**
