package com.bsoft.compose.bmusic.data.playlists

import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Result of importing an M3U file. */
data class M3uImportResult(val playlistName: String, val added: Int, val missing: Int)

/** Turns BMusic playlists into M3U files and back. */
@Singleton
class PlaylistTransfer @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistDao: PlaylistDao,
    private val playlistRepository: PlaylistRepository,
) {
    /** The playlist as M3U8 text, with absolute paths where the library knows them. */
    suspend fun exportM3u(playlistId: Long): String {
        val library = songRepository.songs.associateBy { it.id }
        val entries = playlistDao.getPlaylistSongs(playlistId).map { song ->
            M3uEntry(
                location = library[song.id]?.path?.takeIf { it.isNotEmpty() } ?: song.displayName,
                title = song.title,
                artist = song.artist,
                durationMs = song.duration
            )
        }
        return M3u.format(entries)
    }

    /**
     * Creates a playlist from M3U text, named [suggestedName] (made unique). Returns null when
     * the text lists no songs; no playlist is created when none of them are on this device.
     */
    suspend fun importM3u(text: String, suggestedName: String): M3uImportResult? {
        val entries = M3u.parse(text)
        if (entries.isEmpty()) return null
        val library = songRepository.songs
        val byId = library.associateBy { it.id }
        val matcher = M3uMatcher(library.map { it.toLibraryFile() })
        val songs = entries.mapNotNull { entry -> matcher.match(entry)?.let { byId[it.id] } }.distinctBy { it.id }
        val missing = entries.count { matcher.match(it) == null }

        val name = uniquePlaylistName(
            suggestedName.trim().ifEmpty { DEFAULT_NAME },
            playlistDao.getPlaylists().first().map { it.name }
        )
        if (songs.isNotEmpty()) {
            playlistRepository.createPlaylistWithSongs(name, songs.map { PlaylistEntity.fromSong(0L, it) })
        }
        return M3uImportResult(name, songs.size, missing)
    }

    private fun Song.toLibraryFile() = LibraryFile(id, path, displayName, title, artist, duration)

    private companion object {
        const val DEFAULT_NAME = "Imported playlist"
    }
}
