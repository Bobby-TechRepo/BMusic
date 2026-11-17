package com.bsoft.compose.bmusic.data.serializables

import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsData(
    /** ARGB accent colour, or [ACCENT_DYNAMIC] for Material You (wallpaper) colours. */
    val accentColor: Int = 0xFF2196F3.toInt(), // Default Blue
    val language: String = "en",
    val rememberLastTab: Boolean = true,
    /** Index of the Home tab shown last; restored when [rememberLastTab] is on. */
    val lastTab: Int = 0,
    /** How the Songs tab is sorted; a [com.bsoft.compose.bmusic.data.models.SongSort] name. */
    val songSort: String = "TITLE",
    // Default true: earlier builds always hid songs under a minute (ringtones, voice notes).
    val hideShortSongs: Boolean = true,
    val shakeToChange: Boolean = false,
    /** Old "Sound fades as songs end" switch; read only when [transitionMode] is unset. */
    val soundFade: Boolean = false,
    /** How songs hand over: a [com.bsoft.compose.bmusic.data.playback.TransitionMode] name; "" = from [soundFade]. */
    val transitionMode: String = "",
    /** Length of the fade-out or crossfade, in seconds (1 to 12). */
    val transitionSeconds: Int = 6,
    /** With crossfade on, tracks of an album played in order still run straight into each other. */
    val gaplessAlbums: Boolean = true,
    /** A [com.bsoft.compose.bmusic.data.tags.ReplayGainMode] name. */
    val replayGainMode: String = "OFF",
    /** Latest searches, newest first, for the search screen's history. */
    val recentSearches: List<String> = emptyList(),
    val lastPlayedMediaId: String? = null,
    val lastRepeatMode: Int = 0,
    val lastShuffleMode: Boolean = false,
    // Full playback snapshot: media IDs preserve queue-entry identity (including duplicates).
    val lastQueueMediaIds: List<String> = emptyList(),
    val lastQueueIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val lastPlayWhenReady: Boolean = false,
    val lastQueueContext: String = "songs"
) {
    companion object {
        /** Not a real colour (fully transparent black), so it can never clash with a choice. */
        const val ACCENT_DYNAMIC = 0
    }
}
