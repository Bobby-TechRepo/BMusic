package com.bsoft.compose.bmusic.data.models

import com.bsoft.compose.bmusic.data.entities.PlayCounterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun count(song: Long, played: Int, daysAgo: Long) =
        PlayCounterEntity(song = song, title = "s$song", played = played, lastPlayed = now - daysAgo * day)

    private fun song(id: Long, added: Long) =
        Song(id = id, displayName = "$id.mp3", title = "s$id", artist = "a", album = "b", duration = 200_000, dateAdded = added)

    @Test
    fun `rediscover keeps liked songs not played lately`() {
        val counts = listOf(
            count(1, played = 10, daysAgo = 40),
            count(2, played = 10, daysAgo = 5),   // played recently
            count(3, played = 2, daysAgo = 90),   // not played enough
            count(4, played = 25, daysAgo = 31),
            count(5, played = 10, daysAgo = 100), // same plays as 1, forgotten longer
        )
        assertEquals(listOf(4L, 5L, 1L), SmartPlaylists.rediscover(counts, now))
    }

    @Test
    fun `rediscover ignores counts without a date`() {
        val undated = PlayCounterEntity(song = 9, title = "old", played = 50, lastPlayed = 0)
        assertEquals(emptyList<Long>(), SmartPlaylists.rediscover(listOf(undated), now))
    }

    @Test
    fun `never played lists unplayed songs newest first`() {
        val library = listOf(song(1, added = 10), song(2, added = 30), song(3, added = 20))
        val counts = listOf(count(1, played = 1, daysAgo = 1))
        assertEquals(listOf(2L, 3L), SmartPlaylists.neverPlayed(library, counts).map { it.id })
    }

    @Test
    fun `lists are capped`() {
        val library = (1L..500L).map { song(it, added = it) }
        assertEquals(SmartPlaylists.MAX_SONGS, SmartPlaylists.neverPlayed(library, emptyList()).size)
    }

    @Test
    fun `new sources round trip through media ids`() {
        listOf(MediaSource.Rediscover, MediaSource.NeverPlayed).forEach { source ->
            val id = MediaId.Entry(source, 3, 42)
            assertEquals(id, MediaId.parse(id.encode()))
        }
    }
}
