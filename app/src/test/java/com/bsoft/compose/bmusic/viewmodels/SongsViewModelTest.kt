package com.bsoft.compose.bmusic.viewmodels

import android.app.Application
import app.cash.turbine.test
import com.bsoft.compose.bmusic.MainDispatcherRule
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.data.models.ArtistDetails
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.repositories.FavouriteRepository
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SongsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = mockk(relaxed = true)
    private val favouriteRepository: FavouriteRepository = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val changes = MutableStateFlow(0L)

    private val song = Song(id = 1, displayName = "Song", title = "Song", artist = "Artist", album = "Album", duration = 120_000)

    private lateinit var viewModel: SongsViewModel

    @Before
    fun setUp() {
        every { application.getString(any()) } returns ""
        every { application.getString(any(), *anyVararg()) } returns ""
        every { favouriteRepository.getAllFavorites() } returns flowOf(emptyList())
        every { playlistRepository.getPlayLists() } returns flowOf(emptyList())
        every { songRepository.changes } returns changes
        every { songRepository.songs } returns listOf(song)
        every { songRepository.albums } returns emptyList()
        every { songRepository.artists } returns emptyList()
        every { songRepository.last } returns emptyList()

        // The unconfined test dispatcher runs "background" work immediately.
        viewModel = SongsViewModel(
            application, songRepository, favouriteRepository, playlistRepository,
            io = mainDispatcherRule.testDispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `permission granted loads the library`() = runTest {
        viewModel.onStoragePermissionResult(true)

        viewModel.state.test {
            val item = awaitItem()
            assertTrue(item.loaded)
            assertEquals(listOf(song), item.songs)
            assertFalse(item.permissionDenied)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permission denied shows the permission state`() = runTest {
        viewModel.onStoragePermissionResult(false)

        val state = viewModel.state.value
        assertTrue(state.loaded)
        assertTrue(state.permissionDenied)
        assertNotNull(state.error)
    }

    @Test
    fun `library change reloads songs once permission is granted`() = runTest {
        viewModel.onStoragePermissionResult(true)
        val newSong = song.copy(id = 2, title = "New")
        every { songRepository.songs } returns listOf(song, newSong)

        changes.value = 1L

        assertEquals(listOf(song, newSong), viewModel.state.value.songs)
    }

    @Test
    fun `library change is ignored before permission is granted`() = runTest {
        changes.value = 1L
        verify(exactly = 0) { songRepository.songs }
    }

    @Test
    fun `queryAlbum should update albumState`() = runTest {
        val album = Album(id = 1, name = "Album", artist = "Artist", songCount = 1)
        every { songRepository.findAlbumById(1) } returns album
        every { songRepository.findSongsByAlbumId(1) } returns listOf(song)

        viewModel.queryAlbum(1)

        val state = viewModel.albumState.value
        assertTrue(state.loaded)
        assertEquals(album, state.album)
        assertEquals(listOf(song), state.songs)
    }

    @Test
    fun `queryArtist should update artistState`() = runTest {
        val artist = Artist(id = 1, name = "Artist", albumCount = 1, songCount = 1)
        val details = ArtistDetails(id = 1, songs = listOf(song), albums = emptyList())
        every { songRepository.findArtistById(1) } returns artist
        every { songRepository.findArtistDetailsByArtistId(1) } returns details

        viewModel.queryArtist(1)

        val state = viewModel.artistState.value
        assertTrue(state.loaded)
        assertEquals(artist, state.artist)
        assertEquals(details.songs, state.songs)
    }

    @Test
    fun `createPlaylist should call repository`() = runTest {
        viewModel.createPlaylist("New")
        coVerify { playlistRepository.createPlayList(any()) }
    }

    @Test
    fun `createPlaylist ignores blank names`() = runTest {
        viewModel.createPlaylist("   ")
        coVerify(exactly = 0) { playlistRepository.createPlayList(any()) }
    }

    @Test
    fun `deletePlaylist should call repository`() = runTest {
        viewModel.deletePlaylist(1L)
        coVerify { playlistRepository.deletePlayList(1L) }
    }

    @Test
    fun `addToPlaylist should call repository with mapped entities`() = runTest {
        viewModel.addToPlaylist(1L, listOf(song))
        coVerify { playlistRepository.insertSongsIntoPlayList(any()) }
    }
    @Test
    fun `removeFromPlaylist removes all selected songs at once`() = runTest {
        viewModel.removeFromPlaylist(3L, setOf(1L, 2L))
        coVerify { playlistRepository.removeSongs(3L, listOf(1L, 2L)) }
    }

    @Test
    fun `reorderPlaylist saves the full order`() = runTest {
        viewModel.reorderPlaylist(3L, listOf(2L, 1L))
        coVerify { playlistRepository.reorder(3L, listOf(2L, 1L)) }
    }

    @Test
    fun `createPlaylistWithSongs passes the songs in order`() = runTest {
        val other = song.copy(id = 2)
        viewModel.createPlaylistWithSongs("  Mix  ", listOf(song, other))
        coVerify { playlistRepository.createPlaylistWithSongs("Mix", match { list -> list.map { it.id } == listOf(1L, 2L) }) }
    }

    @Test
    fun `toggleFavourite delegates to the repository`() = runTest {
        viewModel.toggleFavourite(song)
        coVerify { favouriteRepository.toggleFavourite(song) }
    }
}
