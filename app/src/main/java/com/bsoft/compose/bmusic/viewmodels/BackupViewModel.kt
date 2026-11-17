package com.bsoft.compose.bmusic.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.backup.BackupCodec
import com.bsoft.compose.bmusic.data.backup.BackupManager
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BackupUiState(val working: Boolean = false, val message: String? = null)

/** Writes and reads backup files chosen with the system file picker. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    application: Application,
    private val backupManager: BackupManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AndroidViewModel(application) {
    private val app = application
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Suggested file name, e.g. "BMusic-backup-2026-11-12.json". */
    fun suggestedFileName(): String =
        "BMusic-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    fun export(uri: Uri) = launchTask {
        val backup = backupManager.export()
        val text = BackupCodec.encode(backup)
        withContext(io) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                ?: error("Could not open the file")
        }
        app.getString(R.string.backup_done, backup.favourites.size, backup.playlists.size)
    }

    fun restore(uri: Uri) = launchTask {
        val text = withContext(io) {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the file")
        }
        val backup = try {
            BackupCodec.decode(text)
        } catch (e: IllegalArgumentException) {
            return@launchTask app.getString(R.string.backup_invalid_file)
        }
        val result = backupManager.restore(backup)
        buildString {
            append(app.getString(R.string.restore_done, result.favourites, result.playlists, result.playlistSongs))
            if (result.lyrics > 0) {
                append(' ').append(app.resources.getQuantityString(R.plurals.restore_lyrics, result.lyrics, result.lyrics))
            }
            if (result.missingSongs > 0) {
                append(' ').append(app.resources.getQuantityString(R.plurals.restore_missing, result.missingSongs, result.missingSongs))
            }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun launchTask(block: suspend () -> String) {
        if (_state.value.working) return
        _state.value = BackupUiState(working = true)
        viewModelScope.launch(io) {
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.getString(R.string.backup_failed)
            }
            _state.value = BackupUiState(working = false, message = message)
        }
    }
}
