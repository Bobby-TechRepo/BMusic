package com.bsoft.compose.bmusic.data

import kotlin.math.min

/**
 * Decides when a track counts as "played", using the common scrobbling rule: the listener has
 * actually heard at least half the track or [minListenMs], whichever is shorter.
 *
 * Only real listening time counts. Position jumps larger than the polling step (seeks, a
 * restored position after a restart) are ignored, so restoring the last session on app launch
 * or skipping a track after two seconds no longer inflates play counts.
 *
 * Not thread-safe; drive it from the player's thread.
 */
class PlayCountTracker(private val minListenMs: Long = DEFAULT_MIN_LISTEN_MS) {
    private var songId: Long? = null

    /** The song being tracked, or null. */
    val currentSongId: Long?
        get() = songId
    private var durationMs: Long = 0L
    private var listenedMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var counted: Boolean = false

    /** Listening time needed before the current track counts. */
    val thresholdMs: Long
        get() = if (durationMs > 0L) min(minListenMs, durationMs / 2) else minListenMs

    /** Call when a new track (or a repeat of the same one) starts. */
    fun start(songId: Long, durationMs: Long, positionMs: Long) {
        this.songId = songId.takeIf { it > 0L }
        this.durationMs = durationMs.coerceAtLeast(0L)
        listenedMs = 0L
        lastPositionMs = positionMs.coerceAtLeast(0L)
        counted = false
    }

    /** Call when playback stops or the queue is cleared. */
    fun reset() {
        songId = null
        listenedMs = 0L
        counted = false
    }

    /**
     * Call periodically while playing. Returns the song ID exactly once, at the moment it
     * crosses the threshold; otherwise null.
     *
     * @param maxStepMs the largest position change still treated as continuous playback.
     */
    fun onProgress(positionMs: Long, maxStepMs: Long): Long? {
        val id = songId ?: return null
        val delta = positionMs - lastPositionMs
        lastPositionMs = positionMs
        if (delta in 1..maxStepMs) listenedMs += delta
        if (!counted && listenedMs >= thresholdMs) {
            counted = true
            return id
        }
        return null
    }

    companion object {
        const val DEFAULT_MIN_LISTEN_MS = 30_000L
    }
}
