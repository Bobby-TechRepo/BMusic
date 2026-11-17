package com.bsoft.compose.bmusic.viewmodels

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.lyrics.decodeText
import com.bsoft.compose.bmusic.data.playlists.PlaylistTransfer
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** M3U files bigger than this are not playlists. */
private const val MAX_M3U_BYTES = 4 * 1024 * 1024

/** Exports a playlist to an M3U file and imports M3U files as new playlists. */
@HiltViewModel
class PlaylistTransferViewModel @Inject constructor(
    private val app: Application,
    private val transfer: PlaylistTransfer,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AndroidViewModel(app) {
    /** A message to show once, then clear with [dismissMessage]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** File name offered when exporting [playlistName]. */
    fun suggestedFileName(playlistName: String) =
        playlistName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "playlist" } + ".m3u8"

    fun export(playlistId: Long, uri: Uri) = launchTask {
        val text = transfer.exportM3u(playlistId)
        withContext(io) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                ?: error("Could not open the file")
        }
        app.getString(R.string.m3u_exported)
    }

    fun import(uri: Uri) = launchTask {
        val (text, name) = withContext(io) {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open the file")
            require(bytes.size <= MAX_M3U_BYTES) { "File too large" }
            decodeText(bytes) to displayName(uri)
        }
        val result = transfer.importM3u(text, name.substringBeforeLast('.'))
        when {
            result == null -> app.getString(R.string.m3u_invalid_file)
            result.added == 0 -> app.getString(R.string.m3u_no_songs_found)
            else -> buildString {
                append(app.resources.getQuantityString(R.plurals.m3u_imported, result.added, result.playlistName, result.added))
                if (result.missing > 0) {
                    append(' ').append(app.resources.getQuantityString(R.plurals.m3u_missing, result.missing, result.missing))
                }
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun displayName(uri: Uri): String =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    private fun launchTask(task: suspend () -> String) {
        viewModelScope.launch {
            _message.value = try {
                task()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.getString(R.string.m3u_failed)
            }
        }
    }
}
