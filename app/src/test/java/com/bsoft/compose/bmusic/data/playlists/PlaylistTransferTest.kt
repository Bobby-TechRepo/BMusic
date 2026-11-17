package com.bsoft.compose.bmusic.data.playlists

import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistSongEntity
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaylistTransferTest {
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val transfer = PlaylistTransfer(songRepository, playlistDao, playlistRepository)

    private val one = Song(id = 1, displayName = "one.mp3", title = "One", artist = "A", album = "X", duration = 100_000, path = "/Music/one.mp3")
    private val two = Song(id = 2, displayName = "two.mp3", title = "Two", artist = "B", album = "X", duration = 61_000, path = "/Music/two.mp3")

    @Before
    fun setUp() {
        every { songRepository.songs } returns listOf(one, two)
        every { playlistDao.getPlaylists() } returns flowOf(listOf(PlaylistEntity(id = 3, name = "Mix")))
    }

    @Test
    fun `export writes paths in playlist order`() = runTest {
        coEvery { playlistDao.getPlaylistSongs(3) } returns listOf(
            PlaylistEntity.fromSong(3, two),
            PlaylistEntity.fromSong(3, one),
            // Deleted from the device: only the file name is known.
            PlaylistSongEntity(id = 9, title = "Gone", displayName = "gone.mp3", artist = "C", album = "Y", duration = 5_000, playlist = 3),
        )
        assertEquals(
            "#EXTM3U\n#EXTINF:61,B - Two\n/Music/two.mp3\n#EXTINF:100,A - One\n/Music/one.mp3\n#EXTINF:5,C - Gone\ngone.mp3\n",
            transfer.exportM3u(3)
        )
    }

    @Test
    fun `import creates a uniquely named playlist with the matched songs`() = runTest {
        val songs = slot<List<PlaylistSongEntity>>()
        coEvery { playlistRepository.createPlaylistWithSongs("Mix (2)", capture(songs)) } returns 4L
        val text = "#EXTM3U\n/other/phone/two.mp3\n#EXTINF:100,A - One\nmissing-name.mp3\nnot-here.mp3\ntwo.mp3\n"

        val result = transfer.importM3u(text, "Mix")

        assertEquals(M3uImportResult("Mix (2)", added = 2, missing = 1), result)
        assertEquals(listOf(2L, 1L), songs.captured.map { it.id })
    }

    @Test
    fun `import with nothing found creates no playlist`() = runTest {
        val result = transfer.importM3u("nobody.mp3\n", "Road trip")
        assertEquals(M3uImportResult("Road trip", added = 0, missing = 1), result)
        coVerify(exactly = 0) { playlistRepository.createPlaylistWithSongs(any(), any()) }
    }

    @Test
    fun `a file with no songs is not a playlist`() = runTest {
        assertNull(transfer.importM3u("#EXTM3U\n# just comments\n", "x"))
    }
}
