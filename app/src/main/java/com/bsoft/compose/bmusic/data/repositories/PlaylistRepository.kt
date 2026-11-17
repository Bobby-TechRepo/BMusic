package com.bsoft.compose.bmusic.data.repositories

import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(private val playlistDao: PlaylistDao) {
    suspend fun createPlayList(playlistEntity: PlaylistEntity){
        playlistDao.createPlaylists(playlistEntity)
    }

    fun getPlayLists(): Flow<List<PlaylistEntity>> {
        return playlistDao.getPlaylists()
    }

    suspend fun updatePlaylist(playlistEntity: PlaylistEntity) {
        playlistDao.updatePlaylist(playlistEntity)
    }

    suspend fun deletePlayList(playlist: Long){
        playlistDao.deletePlaylist(playlist = playlist)
        playlistDao.deleteAllSongFromPlaylist(playlist = playlist)
    }

    /** Creates a playlist holding [songs], in order, and returns its ID. */
    suspend fun createPlaylistWithSongs(name: String, songs: List<PlaylistSongEntity>): Long {
        val id = playlistDao.createPlaylist(PlaylistEntity(name = name))
        insertSongsIntoPlayList(songs.map { it.copy(playlist = id) })
        return id
    }

    /** Appends songs not already in the playlist, after the current last position. */
    suspend fun insertSongsIntoPlayList(playlistSongEntities: List<PlaylistSongEntity>) {
        if (playlistSongEntities.isEmpty()) return
        var next = playlistDao.maxPosition(playlistSongEntities.first().playlist) + 1
        playlistSongEntities.distinctBy { it.id }.forEach {
            if(playlistDao.doesSongExistInPlaylist(id = it.id, playlist = it.playlist).not()){
                playlistDao.insertPlaylistSong(it.copy(position = next++))
            }
        }

        if(playlistSongEntities.isNotEmpty()){
            val playlist = playlistSongEntities.first().playlist
            val songsDuration = playlistDao.getTotalSongsDurationForPlaylist(playlist)
            val songsCount = playlistDao.getPlaylistSongCount(playlist)
            playlistDao.updateSongDetails(playlist, songsCount, songsDuration)
        }
    }

    suspend fun getPlayList(playlist: Long): PlaylistEntity{
        return playlistDao.getPlaylist(playlist)
    }

    suspend fun getSongsFromPlayList(playlist: Long): List<PlaylistSongEntity> {
        return playlistDao.getPlaylistSongs(playlist = playlist)
    }

    /** Removes several songs at once and updates the playlist's count and duration. */
    suspend fun removeSongs(playlist: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        playlistDao.deleteSongsFromPlaylist(playlist, songIds)
        refreshDetails(playlist)
    }

    /** Saves a new order: [orderedSongIds] is the full playlist, first to last. */
    suspend fun reorder(playlist: Long, orderedSongIds: List<Long>) {
        orderedSongIds.forEachIndexed { position, songId -> playlistDao.setPosition(playlist, songId, position) }
    }

    private suspend fun refreshDetails(playlist: Long) {
        val songsDuration = playlistDao.getTotalSongsDurationForPlaylist(playlist)
        val songsCount = playlistDao.getPlaylistSongCount(playlist)
        playlistDao.updateSongDetails(playlist, songsCount, songsDuration)
    }

    suspend fun deleteFromPlaylist(playlistSongEntity: PlaylistSongEntity){
        val playlist = playlistSongEntity.playlist

        playlistDao.deleteSongFromPlaylist(playlistSongEntity)
        val songsDuration = playlistDao.getTotalSongsDurationForPlaylist(playlist)
        val songsCount = playlistDao.getPlaylistSongCount(playlist)
        playlistDao.updateSongDetails(playlist, songsCount, songsDuration)
    }
}