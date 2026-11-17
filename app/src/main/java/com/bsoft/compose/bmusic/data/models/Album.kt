package com.bsoft.compose.bmusic.data.models

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Album(val id: Long, val name: String, val artist: String, val songCount: Int){
    val artworkUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id)
    fun toMediaItem(index: Int = 0): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artist)
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
        Song.albumArtUri(id)?.let { metadata.setArtworkUri(it) }
        return MediaItem.Builder()
            .setMediaId(MediaId.Entry(MediaSource.Album(id), index).encode())
            .setMediaMetadata(metadata.build())
            .build()
    }
}