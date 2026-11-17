package com.bsoft.compose.bmusic.data.repositories

import com.bsoft.compose.bmusic.data.doas.ListenEventDao
import com.bsoft.compose.bmusic.data.entities.DayPlays
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.SongPlays
import com.bsoft.compose.bmusic.data.models.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsRepositoryTest {
    private val dao: ListenEventDao = mockk(relaxed = true)
    private val repository = ListeningStatsRepository(dao)
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `periods start at the right time`() {
        val now = 100 * day
        assertEquals(93 * day, StatsPeriod.WEEK.since(now))
        assertEquals(70 * day, StatsPeriod.MONTH.since(now))
        assertEquals(0L, StatsPeriod.ALL_TIME.since(now))
    }

    @Test
    fun `record stores the song details`() = runTest {
        val song = Song(id = 3, displayName = "s.mp3", title = "Song", artist = "Artist", album = "Album", duration = 180_000)
        repository.record(song, atMs = 777L)
        coVerify {
            dao.insert(ListenEventEntity(song = 3, title = "Song", artist = "Artist", album = "Album", listenedAt = 777L, durationMs = 180_000))
        }
    }

    @Test
    fun `record never stores a negative length`() = runTest {
        val song = Song(id = 3, displayName = "s.mp3", title = "Song", artist = "Artist", album = "Album", duration = -1)
        repository.record(song, atMs = 1L)
        coVerify { dao.insert(match<ListenEventEntity> { it.durationMs == 0L }) }
    }

    @Test
    fun `stats query the period window`() = runTest {
        val now = 100 * day
        val since = 93 * day
        val top = listOf(SongPlays(song = 1, title = "One", artist = "A", plays = 5))
        coEvery { dao.playsSince(since) } returns 8
        coEvery { dao.distinctSongsSince(since) } returns 3
        coEvery { dao.listenedMsSince(since) } returns 1_000_000L
        coEvery { dao.topSongsSince(since, 10) } returns top
        coEvery { dao.playsPerDay(since, 0L) } returns listOf(DayPlays(day = 99, plays = 3))

        val stats = repository.stats(StatsPeriod.WEEK, nowMs = now, zoneOffsetMs = 0L)

        assertEquals(8, stats.plays)
        assertEquals(3, stats.distinctSongs)
        assertEquals(1_000_000L, stats.listenedMs)
        assertEquals(top, stats.topSongs)
        assertFalse(stats.isEmpty)
        coVerify { dao.topArtistsSince(since, 5) }
        // Seven daily bars ending today (epoch day 100); yesterday had 3 plays.
        assertEquals(7, stats.activity.size)
        assertEquals(listOf(0, 0, 0, 0, 0, 3, 0), stats.activity.map { it.plays })
        assertEquals(7, stats.days)
        assertEquals(1_000_000L / 7, stats.dailyAverageMs)
    }

    @Test
    fun `no plays means empty stats`() {
        assertTrue(ListeningStats().isEmpty)
    }

    @Test
    fun `all time averages over the days since the first listen`() = runTest {
        val now = 100 * day + 1_000
        coEvery { dao.playsSince(0L) } returns 4
        coEvery { dao.listenedMsSince(0L) } returns 10 * day
        coEvery { dao.firstListenAt() } returns 91 * day
        coEvery { dao.playsPerDay(any(), 0L) } returns emptyList()

        val stats = repository.stats(StatsPeriod.ALL_TIME, nowMs = now, zoneOffsetMs = 0L)

        assertEquals(10, stats.days) // day 91 through day 100
        assertEquals(day, stats.dailyAverageMs)
        assertEquals(12, stats.activity.size)
        assertTrue(stats.activity.all { it.monthly })
    }
}
