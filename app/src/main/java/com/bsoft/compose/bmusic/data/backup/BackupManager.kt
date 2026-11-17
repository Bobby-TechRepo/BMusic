package com.bsoft.compose.bmusic.data.backup

import com.bsoft.compose.bmusic.data.doas.FavouritesDao
import com.bsoft.compose.bmusic.data.doas.ListenEventDao
import com.bsoft.compose.bmusic.data.doas.PlayerCounterDao
import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.FavouriteEntity
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.PlayCounterEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.lyrics.LyricsRepository
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What a restore did, for the confirmation message. */
data class RestoreResult(
    val favourites: Int = 0,
    val playlists: Int = 0,
    val playlistSongs: Int = 0,
    val playCounts: Int = 0,
    val listens: Int = 0,
    val lyrics: Int = 0,
    /** Backed-up songs that are not on this device. */
    val missingSongs: Int = 0,
)

/**
 * Exports and restores the user's own data: favourites, playlists (in order), play counts,
 * listening history and imported lyrics. Restoring merges: nothing already here is deleted, playlists with the same
 * name gain the missing songs, counts keep the higher value, history is not duplicated, and
 * lyrics already imported here are kept.
 * Blocking work; call from an I/O dispatcher.
 */
@Singleton
class BackupManager @Inject constructor(
    private val songRepository: SongRepository,
    private val favouritesDao: FavouritesDao,
    private val playlistDao: PlaylistDao,
    private val playlistRepository: PlaylistRepository,
    private val playerCounterDao: PlayerCounterDao,
    private val listenEventDao: ListenEventDao,
    private val lyricsRepository: LyricsRepository,
) {
    suspend fun export(nowMs: Long = System.currentTimeMillis()): BackupFile {
        val library = songRepository.songs.associateBy { it.id }
        fun describe(id: Long, title: String) = library[id]?.toBackupSong() ?: BackupSong(id = id, title = title)

        val favourites = favouritesDao.getAll().first().map { describe(it.song, it.title) }
        val playlists = playlistDao.getPlaylists().first().map { playlist ->
            BackupPlaylist(
                name = playlist.name,
                songs = playlistDao.getPlaylistSongs(playlist.id).map {
                    BackupSong(it.id, it.title, it.artist, it.album, it.duration)
                }
            )
        }
        val counts = playerCounterDao.getAll().first().map {
            BackupPlayCount(describe(it.song, it.title), it.played, it.lastPlayed)
        }
        val listens = listenEventDao.all().map {
            BackupListen(BackupSong(it.song, it.title, it.artist, it.album, it.durationMs), it.listenedAt)
        }
        // Only songs still in the library: without a title an orphan file could never be matched.
        val lyrics = lyricsRepository.importedLyrics().mapNotNull { (songId, lrc) ->
            library[songId]?.let { BackupLyrics(it.toBackupSong(), lrc) }
        }
        return BackupFile(
            createdAt = nowMs, favourites = favourites, playlists = playlists, playCounts = counts,
            listens = listens, lyrics = lyrics
        )
    }

    suspend fun restore(backup: BackupFile): RestoreResult {
        val matcher = SongMatcher(songRepository.songs)
        val missing = mutableSetOf<Long>()
        fun find(song: BackupSong) = matcher.match(song).also { if (it == null) missing += song.id }

        var favourites = 0
        backup.favourites.forEach { saved ->
            val song = find(saved) ?: return@forEach
            if (!favouritesDao.doesSongExist(song.id)) {
                favouritesDao.insertAll(FavouriteEntity.fromSong(song))
                favourites++
            }
        }

        var playlistsCreated = 0
        var playlistSongs = 0
        val existing = playlistDao.getPlaylists().first()
        backup.playlists.forEach { saved ->
            val songs = saved.songs.mapNotNull { find(it) }
            val target = existing.firstOrNull { it.name.equals(saved.name, ignoreCase = true) }
            if (target == null) {
                playlistRepository.createPlaylistWithSongs(saved.name, songs.map { PlaylistEntity.fromSong(0L, it) })
                playlistsCreated++
                playlistSongs += songs.distinctBy { it.id }.size
            } else {
                val before = playlistDao.getPlaylistSongCount(target.id)
                playlistRepository.insertSongsIntoPlayList(songs.map { PlaylistEntity.fromSong(target.id, it) })
                playlistSongs += playlistDao.getPlaylistSongCount(target.id) - before
            }
        }

        var counts = 0
        backup.playCounts.forEach { saved ->
            val song = find(saved.song) ?: return@forEach
            val current = playerCounterDao.get(song.id)
            if (current == null) {
                playerCounterDao.insertAll(PlayCounterEntity(song = song.id, title = song.title, played = saved.played, lastPlayed = saved.lastPlayed))
                counts++
            } else if (saved.played > current.played || saved.lastPlayed > current.lastPlayed) {
                playerCounterDao.update(
                    current.copy(played = maxOf(current.played, saved.played), lastPlayed = maxOf(current.lastPlayed, saved.lastPlayed))
                )
                counts++
            }
        }

        var listens = 0
        backup.listens.forEach { saved ->
            val song = find(saved.song) ?: return@forEach
            // Restoring the same file twice must not double the history.
            if (listenEventDao.countAt(song.id, saved.at) == 0) {
                listenEventDao.insert(
                    ListenEventEntity(song = song.id, title = song.title, artist = song.artist, album = song.album,
                        listenedAt = saved.at, durationMs = song.duration)
                )
                listens++
            }
        }

        var lyrics = 0
        backup.lyrics.forEach { saved ->
            val song = find(saved.song) ?: return@forEach
            if (lyricsRepository.saveImported(song, saved.lrc, overwrite = false)) lyrics++
        }

        return RestoreResult(favourites, playlistsCreated, playlistSongs, counts, listens, lyrics, missing.size)
    }
}
