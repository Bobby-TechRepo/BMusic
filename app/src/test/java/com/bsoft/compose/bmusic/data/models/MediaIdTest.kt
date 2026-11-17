package com.bsoft.compose.bmusic.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdTest {

    @Test
    fun `browse folders parse to folder ids`() {
        assertEquals(MediaId.Root, MediaId.parse("root"))
        assertEquals(MediaId.SongsFolder, MediaId.parse("songs"))
        assertEquals(MediaId.AlbumsFolder, MediaId.parse("albums"))
        assertEquals(MediaId.ArtistsFolder, MediaId.parse("artists"))
    }

    @Test
    fun `plain number is a track`() {
        assertEquals(MediaId.Track(12345), MediaId.parse("12345"))
    }

    @Test
    fun `album browse id no longer breaks parsing`() {
        // Regression: onGetChildren used to call "5#0".toLong() and crash the service.
        assertEquals(MediaId.Entry(MediaSource.Album(5), 0), MediaId.parse("album_5#0"))
        assertEquals(MediaId.Entry(MediaSource.Artist(7), 2), MediaId.parse("artist_7#2"))
        assertEquals(MediaId.Entry(MediaSource.Album(5), 0), MediaId.parse("album_5"))
    }

    @Test
    fun `queue entries keep source, index and song`() {
        assertEquals(
            MediaId.Entry(MediaSource.Playlist(9), 3, 12345),
            MediaId.parse("playlist_9#3|12345")
        )
        assertEquals(
            MediaId.Entry(MediaSource.RecentlyPlayed, 10, 12345),
            MediaId.parse("recently_played#10|12345")
        )
        assertEquals(MediaId.Entry(MediaSource.Songs, 4), MediaId.parse("songs#4"))
    }

    @Test
    fun `play next entries parse`() {
        assertEquals(
            MediaId.Entry(MediaSource.Queued("998877"), 0, 42),
            MediaId.parse("queued_998877|42")
        )
    }

    @Test
    fun `legacy bare playlist prefix maps to custom`() {
        assertEquals(MediaId.Entry(MediaSource.Custom, 1, 5), MediaId.parse("playlist#1|5"))
    }

    @Test
    fun `garbage is unknown, never an exception`() {
        listOf("", "album_x#0", "album_#", "nonsense#1", "0", "-3", "artist_").forEach {
            assertTrue("$it should be Unknown", MediaId.parse(it) is MediaId.Unknown)
        }
    }

    @Test
    fun `invalid song id after pipe is dropped`() {
        val id = MediaId.parse("songs#0|-1") as MediaId.Entry
        assertNull(id.songId)
    }

    @Test
    fun `encode round-trips every form`() {
        listOf(
            MediaId.Root,
            MediaId.Track(77),
            MediaId.Entry(MediaSource.Songs, 3),
            MediaId.Entry(MediaSource.Album(5), 0),
            MediaId.Entry(MediaSource.Playlist(9), 3, 12345),
            MediaId.Entry(MediaSource.Favourites, 1, 8),
            MediaId.Entry(MediaSource.Queued("123"), 0, 8),
        ).forEach { assertEquals(it, MediaId.parse(it.encode())) }
    }

    @Test
    fun `encode keeps the existing wire format`() {
        assertEquals("album_5#2", MediaId.Entry(MediaSource.Album(5), 2).encode())
        assertEquals("playlist_9#3|12345", MediaId.Entry(MediaSource.Playlist(9), 3, 12345).encode())
        assertEquals("queued_abc|42", MediaId.Entry(MediaSource.Queued("abc"), 0, 42).encode())
    }

    @Test
    fun `song id helper`() {
        assertEquals(12345L, MediaIdUtil.getSongId("recently_played#10|12345"))
        assertEquals(67890L, MediaIdUtil.getSongId("67890"))
        assertEquals(0L, MediaIdUtil.getSongId("songs#5"))
        assertEquals(0L, MediaIdUtil.getSongId("album_5#0"))
    }
}
