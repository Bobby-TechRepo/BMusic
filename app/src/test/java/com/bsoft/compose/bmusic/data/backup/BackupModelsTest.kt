package com.bsoft.compose.bmusic.data.backup

import com.bsoft.compose.bmusic.data.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupModelsTest {
    private fun song(id: Long, title: String, artist: String = "Artist", duration: Long = 200_000L) =
        Song(id = id, displayName = "$title.mp3", title = title, artist = artist, album = "Album", duration = duration)

    @Test
    fun `codec round trips a backup`() {
        val file = BackupFile(
            createdAt = 42L,
            favourites = listOf(BackupSong(1, "One", "A", "B", 1000)),
            playlists = listOf(BackupPlaylist("Road trip", listOf(BackupSong(2, "Two")))),
            playCounts = listOf(BackupPlayCount(BackupSong(3, "Three"), played = 7, lastPlayed = 99L)),
            listens = listOf(BackupListen(BackupSong(4, "Four"), at = 123L)),
        )
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun `codec ignores unknown fields and fills defaults`() {
        val decoded = BackupCodec.decode("""{"version":1,"somethingNew":true,"favourites":[{"id":5,"title":"Five"}]}""")
        assertEquals(listOf(BackupSong(id = 5, title = "Five")), decoded.favourites)
        assertEquals(emptyList<BackupPlaylist>(), decoded.playlists)
    }

    @Test
    fun `codec rejects a file that is not a backup`() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json at all") }
    }

    @Test
    fun `matcher uses the id when the title also matches`() {
        val library = listOf(song(1, "Hello"), song(2, "Hello"))
        assertEquals(2L, SongMatcher(library).match(BackupSong(2, "Hello", "Artist"))?.id)
    }

    @Test
    fun `matcher ignores a reused id with a different title`() {
        val library = listOf(song(1, "Other song"), song(9, "Hello"))
        assertEquals(9L, SongMatcher(library).match(BackupSong(1, "Hello", "Artist"))?.id)
    }

    @Test
    fun `matcher ignores case and accents`() {
        val library = listOf(song(9, "Café del Mar", artist = "Énergie"))
        assertEquals(9L, SongMatcher(library).match(BackupSong(1, "cafe del mar", "ENERGIE"))?.id)
    }

    @Test
    fun `matcher prefers a song of the same length`() {
        val library = listOf(song(10, "Live", duration = 300_000L), song(11, "Live", duration = 201_000L))
        assertEquals(11L, SongMatcher(library).match(BackupSong(1, "Live", "Artist", durationMs = 200_000L))?.id)
    }

    @Test
    fun `matcher returns null when the song is missing`() {
        val library = listOf(song(10, "Live"))
        assertNull(SongMatcher(library).match(BackupSong(1, "Live", "Somebody else")))
    }

    @Test
    fun `song converts to a backup song`() {
        assertEquals(BackupSong(3, "Three", "Artist", "Album", 200_000L), song(3, "Three").toBackupSong())
    }
}
