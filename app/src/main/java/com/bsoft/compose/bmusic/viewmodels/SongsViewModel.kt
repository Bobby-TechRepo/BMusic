package com.bsoft.compose.bmusic.viewmodels

import android.app.Application
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.repositories.FavouriteRepository
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.data.states.AlbumState
import com.bsoft.compose.bmusic.data.states.ArtistState
import com.bsoft.compose.bmusic.data.states.SongsState
import com.bsoft.compose.bmusic.modules.IoDispatcher
import com.bsoft.compose.bmusic.utils.Util
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
    application: Application,
    private val songRepository: SongRepository,
    val favouriteRepository: FavouriteRepository,
    val playlistRepository: PlaylistRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : AndroidViewModel(application) {
    private val app = application

    val favourites = favouriteRepository.getAllFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val playlists = playlistRepository.getPlayLists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    private val mutableState = MutableStateFlow(SongsState())
    val state = mutableState.asStateFlow()

    private val mutableAlbumState = MutableStateFlow(AlbumState())
    val albumState = mutableAlbumState.asStateFlow()

    private val mutableArtistState = MutableStateFlow(ArtistState())
    val artistState = mutableArtistState.asStateFlow()

    /** True once the user has granted audio permission; reloads are pointless before that. */
    private var hasPermission = false
    private var queryJob: Job? = null

    init {
        // SongRepository watches MediaStore and bumps `changes` when the library changes
        // (new download, deleted file, rescan). Reload so the UI never shows a stale library.
        viewModelScope.launch {
            songRepository.changes.drop(1).collect {
                if (hasPermission) query()
            }
        }
    }

    fun query() {
        queryJob?.cancel()
        mutableState.update { it.copy(message = app.getString(R.string.fetching_songs)) }
        queryJob = viewModelScope.launch(io) {
            try {
                val loaded = loadSongsState()
                mutableState.update { loaded }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loaded = true, error = app.getString(R.string.error_unexpected)) }
            }
        }
    }

    private suspend fun loadSongsState(): SongsState = coroutineScope {
        val songs = async { songRepository.songs }
        val albums = async { songRepository.albums }
        val artists = async { songRepository.artists }
        val last = async { songRepository.last }
        SongsState(
            songs = songs.await(),
            albums = albums.await(),
            artists = artists.await(),
            last = last.await(),
            loaded = true
        )
    }

    /** Blocking lookup in the in-memory library; cheap once the library has loaded. */
    fun loadSongsByID(song: Long): Song? = songRepository.findSongById(song)

    fun queryAlbum(albumId: Long) {
        mutableAlbumState.update { AlbumState() }
        viewModelScope.launch(io) {
            try {
                val album = songRepository.findAlbumById(albumId)
                if (album == null) {
                    mutableAlbumState.update { it.copy(loaded = true, error = app.getString(R.string.error_album_not_found, albumId)) }
                } else {
                    val songs = songRepository.findSongsByAlbumId(albumId)
                    mutableAlbumState.update { it.copy(loaded = true, songs = songs, album = album) }
                    val bitmap = Util.loadArtwork(app, album.artworkUri, Size(300, 300))
                    mutableAlbumState.update { state -> state.copy(bitmap = bitmap) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableAlbumState.update { it.copy(loaded = true, error = app.getString(R.string.error_unexpected)) }
            }
        }
    }

    fun queryArtist(artistId: Long) {
        mutableArtistState.update { ArtistState() }
        viewModelScope.launch(io) {
            try {
                val artist = songRepository.findArtistById(artistId)
                if (artist == null) {
                    mutableArtistState.update { it.copy(loaded = true, error = app.getString(R.string.error_artist_not_found, artistId)) }
                } else {
                    val details = songRepository.findArtistDetailsByArtistId(artistId)
                    mutableArtistState.update { it.copy(loaded = true, songs = details.songs, albums = details.albums, artist = artist) }
                    val bitmap = Util.loadArtwork(app, artist.artworkUri, Size(300, 300))
                    mutableArtistState.update { state -> state.copy(bitmap = bitmap) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableArtistState.update { it.copy(loaded = true, error = app.getString(R.string.error_unexpected)) }
            }
        }
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch(io) {
            playlistRepository.createPlayList(PlaylistEntity(name = cleanName))
        }
    }

    fun renamePlaylist(playlist: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch(io) {
            val existing = playlistRepository.getPlayList(playlist)
            playlistRepository.updatePlaylist(existing.copy(name = cleanName))
        }
    }

    fun deletePlaylist(playlist: Long) {
        viewModelScope.launch(io) {
            playlistRepository.deletePlayList(playlist)
        }
    }

    fun addToFavourites(songs: List<Song>) {
        viewModelScope.launch(io) {
            songs.forEach { song ->
                if (!favouriteRepository.isFavorite(song.id)) {
                    favouriteRepository.toggleFavourite(song)
                }
            }
        }
    }

    fun toggleFavourite(song: Song) {
        viewModelScope.launch(io) { favouriteRepository.toggleFavourite(song) }
    }

    /** Removes the given songs from Favourites (used by the Favourites edit mode). */
    fun removeFromFavourites(songIds: Collection<Long>) {
        viewModelScope.launch(io) { songIds.forEach { favouriteRepository.unFavourite(it) } }
    }

    fun createPlaylistWithSongs(name: String, songs: List<Song>) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch(io) {
            playlistRepository.createPlaylistWithSongs(cleanName, songs.map { PlaylistEntity.fromSong(0L, it) })
        }
    }

    fun removeFromPlaylist(playlist: Long, songIds: Collection<Long>) {
        viewModelScope.launch(io) { playlistRepository.removeSongs(playlist, songIds.toList()) }
    }

    /** Persists a custom playlist's new order; [orderedSongIds] lists every song, first to last. */
    fun reorderPlaylist(playlist: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch(io) { playlistRepository.reorder(playlist, orderedSongIds) }
    }

    fun addToPlaylist(playlist: Long, songs: List<Song>) {
        viewModelScope.launch(io) {
            playlistRepository.insertSongsIntoPlayList(
                playlistSongEntities = songs.map { PlaylistEntity.fromSong(playlist, it) }
            )
        }
    }

    fun onStoragePermissionResult(isGranted: Boolean) {
        hasPermission = isGranted
        if (isGranted) {
            mutableState.update { it.copy(permissionDenied = false, error = null, loaded = false) }
            query()
        } else {
            mutableState.update {
                it.copy(loaded = true, permissionDenied = true, error = app.getString(R.string.error_storage_permission))
            }
        }
    }
}
