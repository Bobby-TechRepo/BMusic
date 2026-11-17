package com.bsoft.compose.bmusic.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bsoft.compose.bmusic.data.models.Song

@Entity(tableName = "playlist_songs", primaryKeys = ["id", "playlist"])
data class PlaylistSongEntity(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "playlist") val playlist: Long,
    @ColumnInfo(name = "album_id", defaultValue = "-1") val albumId: Long = -1L,
    /** Order within the playlist; the user can rearrange it. Added in database version 3. */
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0
){
    val song: Song
        get() = Song(id = id, displayName = displayName, title = title, album = album, artist = artist, duration = duration, albumId = albumId)
}