package com.bsoft.compose.bmusic.data.models

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long = -1L,
    /** MediaStore artist ID, or -1 when unknown (e.g. a song rebuilt from a playlist row). */
    val artistId: Long = -1L,
    /** MediaStore TRACK: disc number * 1000 + track number; 0 when unknown. */
    val track: Int = 0,
    /** When the file was added to the device, in seconds since the epoch; 0 when unknown. */
    val dateAdded: Long = 0L,
    /** Absolute file path (MediaStore DATA), for M3U playlists; empty when unknown. */
    val path: String = ""
) {
    /** URI used to decode/play the actual audio file. */
    val mediaUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /**
     * Album art for the system media notification, lock screen and Android Auto. Media3's
     * bitmap loader reads this URI; MediaStore serves the album thumbnail from it. The audio
     * file URI is never published as artwork: it is not an image.
     */
    val artworkUri: Uri?
        get() = albumArtUri(albumId)

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setDisplayTitle(displayName)
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(duration)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // Carry the album ID explicitly so fromMediaItem() can round-trip it.
            .setExtras(Bundle().apply {
                putLong(EXTRA_ALBUM_ID, albumId)
                putLong(EXTRA_ARTIST_ID, artistId)
                // Lets crossfade keep albums gapless (it needs to know the next track number).
                putInt(EXTRA_TRACK, track)
            })
        artworkUri?.let { metadata.setArtworkUri(it) }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(metadata.build())
            .build()
    }

    companion object{
        fun fromMediaItem(mediaItem: MediaItem): Song{
            val rawId = mediaItem.mediaId
            val id = MediaIdUtil.getSongId(rawId)
            val displayName = mediaItem.mediaMetadata.displayTitle?.toString() ?: "Unknown Name"
            val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
            val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
            val album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown Album"
            val duration = mediaItem.mediaMetadata.durationMs ?: 0
            val albumId = mediaItem.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID, -1L)?.takeIf { it > 0L }
                ?: albumIdFromArtUri(mediaItem.mediaMetadata.artworkUri)

            val artistId = mediaItem.mediaMetadata.extras?.getLong(EXTRA_ARTIST_ID, -1L)?.takeIf { it > 0L } ?: -1L

            val track = mediaItem.mediaMetadata.extras?.getInt(EXTRA_TRACK, 0) ?: 0

            return Song(id, displayName, title, artist, album, duration, albumId, artistId, track)
        }

        const val EXTRA_ALBUM_ID = "com.bsoft.compose.bmusic.ALBUM_ID"
        const val EXTRA_ARTIST_ID = "com.bsoft.compose.bmusic.ARTIST_ID"
        const val EXTRA_TRACK = "com.bsoft.compose.bmusic.TRACK"
        private const val ALBUM_ART_BASE = "content://media/external/audio/albumart"

        fun albumArtUri(albumId: Long): Uri? =
            if (albumId > 0L) Uri.parse("$ALBUM_ART_BASE/$albumId") else null

        /** Only trusts album-art URIs; any other URI (e.g. an audio file) yields -1. */
        private fun albumIdFromArtUri(uri: Uri?): Long {
            val text = uri?.toString() ?: return -1L
            if (!text.startsWith("$ALBUM_ART_BASE/")) return -1L
            return text.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0L } ?: -1L
        }
    }
}