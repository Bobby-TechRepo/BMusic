package com.bsoft.compose.bmusic.viewmodels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsoft.compose.bmusic.data.playback.OpenedFileInfo
import com.bsoft.compose.bmusic.data.playback.OpenedFilePlan
import com.bsoft.compose.bmusic.data.playback.planFor
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** How to play a file opened from another app. */
sealed interface OpenedFile {
    data class Library(val plan: OpenedFilePlan.Library) : OpenedFile
    /** A file outside the library, ready to play on its own. */
    data class External(val item: MediaItem) : OpenedFile
}

/**
 * "Open with BMusic": works out whether an opened music file is a library song (then it plays
 * with its folder) or a file from elsewhere (then it plays by itself, with the title and artist
 * read from its tags, or its file name).
 */
@HiltViewModel
class OpenedFileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val songRepository: SongRepository,
) : ViewModel() {

    suspend fun resolve(uri: Uri): OpenedFile = withContext(io) {
        val info = OpenedFileInfo(
            scheme = uri.scheme,
            authority = uri.authority,
            lastSegment = uri.lastPathSegment,
            path = if (uri.scheme == "file") uri.path else null,
        )
        when (val plan = planFor(info, songRepository.songs)) {
            is OpenedFilePlan.Library -> OpenedFile.Library(plan)
            OpenedFilePlan.External -> OpenedFile.External(externalItem(uri))
        }
    }

    private fun externalItem(uri: Uri): MediaItem {
        val fileName = displayName(uri)
        val metadata = MediaMetadata.Builder()
        var title: String? = null
        runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    title = extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { metadata.setArtist(it) }
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { metadata.setAlbumTitle(it) }
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { metadata.setDurationMs(it) }
                    embeddedPicture?.let { metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                } finally {
                    release()
                }
            }
        }.onFailure { Log.w(TAG, "No tags for $uri", it) }

        val shownTitle = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
        metadata.setTitle(shownTitle).setDisplayTitle(fileName).setIsPlayable(true).setIsBrowsable(false)
        return MediaItem.Builder()
            // Not a library ID: the playback service plays such items as they are.
            .setMediaId("$EXTERNAL_PREFIX${uri.hashCode().toUInt().toString(16)}")
            .setUri(uri)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"

    private companion object {
        const val TAG = "OpenedFile"
        const val EXTERNAL_PREFIX = "opened_"
    }
}
