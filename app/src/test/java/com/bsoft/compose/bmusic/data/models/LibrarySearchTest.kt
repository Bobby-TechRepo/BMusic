package com.bsoft.compose.bmusic.data.models

import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val songs = listOf(
        Song(1, "a.mp3", "Hello", "Adele", "25", 1),
        Song(2, "b.mp3", "Halo", "Beyoncé", "I Am... Sasha Fierce", 1),
        Song(3, "c.mp3", "Say Hello", "Someone", "Other", 1),
        Song(4, "d.mp3", "Rolling in the Deep", "Adele", "21", 1),
    )
    private val albums = listOf(Album(1, "21", "Adele", 11), Album(2, "Renaissance", "Beyoncé", 16))
    private val artists = listOf(Artist(1, "Adele", 2, 20), Artist(2, "Beyoncé", 8, 90))
    private val playlists = listOf(PlaylistEntity(id = 1, name = "Hello Monday"), PlaylistEntity(id = 2, name = "Gym"))

    private fun search(q: String) = LibrarySearch.search(q, songs, albums, artists, playlists)

    @Test
    fun `blank query finds nothing`() {
        assertTrue(search("   ").isEmpty)
    }

    @Test
    fun `accents and case are ignored`() {
        val result = search("BEYONCE")
        assertEquals(listOf(2L), result.songs.map { it.id })
        assertEquals(listOf(2L), result.albums.map { it.id })
        assertEquals(listOf(2L), result.artists.map { it.id })
    }

    @Test
    fun `every word must match some field`() {
        assertEquals(listOf(1L, 4L), search("adele").songs.map { it.id })
        assertEquals(listOf(1L), search("adele hello").songs.map { it.id })
    }

    @Test
    fun `titles starting with the query rank first`() {
        assertEquals(listOf(1L, 3L), search("hello").songs.map { it.id })
    }

    @Test
    fun `playlists are searched too`() {
        assertEquals(listOf(1L), search("monday").playlists.map { it.id })
    }
}
