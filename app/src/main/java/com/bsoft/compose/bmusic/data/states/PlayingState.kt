package com.bsoft.compose.bmusic.data.states

data class PlayingState(
    val position: Long = 0,
    val playing: Boolean = false,
    val favourites: List<Long> = emptyList(),
    /** True while the player waits for data before it can continue. */
    val buffering: Boolean = false,
    /** True once the UI's MediaBrowser is connected to the playback service. */
    val connected: Boolean = false,
    /** True when connecting to the playback service failed after every retry. */
    val connectionFailed: Boolean = false,
    /** Playback speed; 1 is normal. */
    val speed: Float = 1f,
)
