package com.bsoft.compose.bmusic.data.backup

import com.bsoft.compose.bmusic.data.doas.FavouritesDao
import com.bsoft.compose.bmusic.data.doas.ListenEventDao
import com.bsoft.compose.bmusic.data.doas.PlayerCounterDao
import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.PlayCounterEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistSongEntity
import com.bsoft.compose.bmusic.data.lyrics.LyricsRepository
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
import org.junit.Before
import org.junit.Test

class BackupManagerTest {
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val favouritesDao: FavouritesDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val playerCounterDao: PlayerCounterDao = mockk(relaxed = true)
    private val listenEventDao: ListenEventDao = mockk(relaxed = true)
    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private lateinit var manager: BackupManager

    private val one = Song(id = 1, displayName = "one.mp3", title = "One", artist = "A", album = "X", duration = 100_000)
    private val two = Song(id = 2, displayName = "two.mp3", title = "Two", artist = "A", album = "X", duration = 120_000)

    @Before
    fun setUp() {
        every { songRepository.songs } returns listOf(one, two)
        every { playlistDao.getPlaylists() } returns flowOf(emptyList())
        manager = BackupManager(songRepository, favouritesDao, playlistDao, playlistRepository, playerCounterDao, listenEventDao, lyricsRepository)
    }

    @Test
    fun `restore adds new favourites and counts missing songs`() = runTest {
        every { favouritesDao.doesSongExist(1) } returns true
        every { favouritesDao.doesSongExist(2) } returns false
        val backup = BackupFile(favourites = listOf(one.toBackupSong(), two.toBackupSong(), BackupSong(99, "Gone")))

        val result = manager.restore(backup)

        assertEquals(1, result.favourites)
        assertEquals(1, result.missingSongs)
        coVerify(exactly = 1) { favouritesDao.insertAll(*anyVararg()) }
    }

    @Test
    fun `restore creates a new playlist with the found songs`() = runTest {
        val songs = slot<List<PlaylistSongEntity>>()
        coEvery { playlistRepository.createPlaylistWithSongs("Mix", capture(songs)) } returns 5L
        val backup = BackupFile(playlists = listOf(BackupPlaylist("Mix", listOf(two.toBackupSong(), one.toBackupSong(), BackupSong(99, "Gone")))))

        val result = manager.restore(backup)

        assertEquals(1, result.playlists)
        assertEquals(2, result.playlistSongs)
        assertEquals(listOf(2L, 1L), songs.captured.map { it.id })
    }

    @Test
    fun `restore merges into a playlist with the same name`() = runTest {
        every { playlistDao.getPlaylists() } returns flowOf(listOf(PlaylistEntity(id = 7, name = "mix")))
        coEvery { playlistDao.getPlaylistSongCount(7) } returnsMany listOf(1, 2)
        val backup = BackupFile(playlists = listOf(BackupPlaylist("Mix", listOf(one.toBackupSong(), two.toBackupSong()))))

        val result = manager.restore(backup)

        assertEquals(0, result.playlists)
        assertEquals(1, result.playlistSongs)
        coVerify { playlistRepository.insertSongsIntoPlayList(match { list -> list.all { it.playlist == 7L } }) }
    }

    @Test
    fun `restore keeps the higher play count`() = runTest {
        coEvery { playerCounterDao.get(1) } returns PlayCounterEntity(song = 1, title = "One", played = 10, lastPlayed = 500)
        coEvery { playerCounterDao.get(2) } returns null
        val backup = BackupFile(
            playCounts = listOf(
                BackupPlayCount(one.toBackupSong(), played = 3, lastPlayed = 900),
                BackupPlayCount(two.toBackupSong(), played = 4, lastPlayed = 100),
            )
        )

        val result = manager.restore(backup)

        assertEquals(2, result.playCounts)
        coVerify { playerCounterDao.update(PlayCounterEntity(song = 1, title = "One", played = 10, lastPlayed = 900)) }
        coVerify { playerCounterDao.insertAll(PlayCounterEntity(song = 2, title = "Two", played = 4, lastPlayed = 100)) }
    }

    @Test
    fun `restore does not duplicate listening history`() = runTest {
        coEvery { listenEventDao.countAt(1, 100) } returns 1
        coEvery { listenEventDao.countAt(2, 200) } returns 0
        val backup = BackupFile(
            listens = listOf(BackupListen(one.toBackupSong(), at = 100), BackupListen(two.toBackupSong(), at = 200))
        )

        val result = manager.restore(backup)

        assertEquals(1, result.listens)
        coVerify(exactly = 1) { listenEventDao.insert(*anyVararg()) }
        coVerify {
            listenEventDao.insert(ListenEventEntity(song = 2, title = "Two", artist = "A", album = "X", listenedAt = 200, durationMs = 120_000))
        }
    }

    @Test
    fun `export includes imported lyrics of songs still in the library`() = runTest {
        every { favouritesDao.getAll() } returns flowOf(emptyList())
        every { playerCounterDao.getAll() } returns flowOf(emptyList())
        coEvery { listenEventDao.all() } returns emptyList()
        coEvery { lyricsRepository.importedLyrics() } returns listOf(1L to "[00:01.00]One", 99L to "[00:01.00]Gone")

        val backup = manager.export(nowMs = 5L)

        assertEquals(listOf(BackupLyrics(one.toBackupSong(), "[00:01.00]One")), backup.lyrics)
    }

    @Test
    fun `restore saves lyrics without replacing ones already imported`() = runTest {
        coEvery { lyricsRepository.saveImported(one, any(), overwrite = false) } returns true
        coEvery { lyricsRepository.saveImported(two, any(), overwrite = false) } returns false // already has some
        val backup = BackupFile(
            lyrics = listOf(
                BackupLyrics(one.toBackupSong(), "[00:01.00]One"),
                BackupLyrics(two.toBackupSong(), "[00:01.00]Two"),
                BackupLyrics(BackupSong(99, "Gone"), "x"),
            )
        )

        val result = manager.restore(backup)

        assertEquals(1, result.lyrics)
        assertEquals(1, result.missingSongs)
        coVerify { lyricsRepository.saveImported(one, "[00:01.00]One", overwrite = false) }
    }

    @Test
    fun `old backups without lyrics still decode`() {
        val old = """{"version":1,"favourites":[{"id":1,"title":"One"}]}"""
        assertEquals(emptyList<BackupLyrics>(), BackupCodec.decode(old).lyrics)
    }
}
