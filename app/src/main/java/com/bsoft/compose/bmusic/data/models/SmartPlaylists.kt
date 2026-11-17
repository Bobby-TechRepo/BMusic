package com.bsoft.compose.bmusic.data.models

import com.bsoft.compose.bmusic.data.entities.PlayCounterEntity

/** Rules behind the "Rediscover" and "Never played" built-in playlists. */
object SmartPlaylists {
    /** A song needs this many plays to count as one you liked. */
    const val REDISCOVER_MIN_PLAYS = 3

    /** ...and no play for this many days to count as forgotten. */
    const val REDISCOVER_IDLE_DAYS = 30

    const val MAX_SONGS = 100

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Song IDs you played often but not lately: most played first, then the longest forgotten.
     */
    fun rediscover(counts: List<PlayCounterEntity>, nowMs: Long): List<Long> {
        val cutoff = nowMs - REDISCOVER_IDLE_DAYS * DAY_MS
        return counts
            .filter { it.played >= REDISCOVER_MIN_PLAYS && it.lastPlayed in 1..cutoff }
            .sortedWith(compareByDescending<PlayCounterEntity> { it.played }.thenBy { it.lastPlayed })
            .take(MAX_SONGS)
            .map { it.song }
    }

    /** Library songs that were never counted as played, newest additions first. */
    fun neverPlayed(library: List<Song>, counts: List<PlayCounterEntity>): List<Song> {
        val played = counts.filter { it.played > 0 }.mapTo(HashSet()) { it.song }
        return library
            .filter { it.id !in played }
            .sortedByDescending { it.dateAdded }
            .take(MAX_SONGS)
    }
}
