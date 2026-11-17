package com.bsoft.compose.bmusic.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SongSortTest {
    private fun song(id: Long, title: String, artist: String = "A", album: String = "B", duration: Long = 1, track: Int = 0, added: Long = 0) =
        Song(id = id, displayName = title, title = title, artist = artist, album = album, duration = duration, track = track, dateAdded = added)

    @Test
    fun `title sort ignores case and accents`() {
        val songs = listOf(song(1, "zebra"), song(2, "Échec"), song(3, "apple"), song(4, "Banana"))
        assertEquals(listOf("apple", "Banana", "Échec", "zebra"), songs.sortedForDisplay(SongSort.TITLE, Locale.ENGLISH).map { it.title })
    }

    @Test
    fun `artist sort breaks ties by title`() {
        val songs = listOf(song(1, "B", artist = "Zed"), song(2, "Z", artist = "Adele"), song(3, "A", artist = "Adele"))
        assertEquals(listOf(3L, 2L, 1L), songs.sortedForDisplay(SongSort.ARTIST, Locale.ENGLISH).map { it.id })
    }

    @Test
    fun `album sort follows track numbers`() {
        val songs = listOf(song(1, "Outro", album = "X", track = 3), song(2, "Intro", album = "X", track = 1), song(3, "Middle", album = "X", track = 2))
        assertEquals(listOf(2L, 3L, 1L), songs.sortedForDisplay(SongSort.ALBUM, Locale.ENGLISH).map { it.id })
    }

    @Test
    fun `date added is newest first and duration longest first`() {
        val songs = listOf(song(1, "a", added = 10, duration = 300), song(2, "b", added = 30, duration = 100), song(3, "c", added = 20, duration = 200))
        assertEquals(listOf(2L, 3L, 1L), songs.sortedForDisplay(SongSort.DATE_ADDED).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), songs.sortedForDisplay(SongSort.DURATION).map { it.id })
    }

    @Test
    fun `unknown saved name falls back to title`() {
        assertEquals(SongSort.TITLE, SongSort.fromName("NOPE"))
        assertEquals(SongSort.TITLE, SongSort.fromName(null))
        assertEquals(SongSort.ARTIST, SongSort.fromName("ARTIST"))
    }

    @Test
    fun `only text orders get an A-Z index`() {
        assertTrue(SongSort.TITLE.alphabetical)
        assertTrue(SongSort.ALBUM.alphabetical)
        assertFalse(SongSort.DATE_ADDED.alphabetical)
        assertFalse(SongSort.DURATION.alphabetical)
    }
}
