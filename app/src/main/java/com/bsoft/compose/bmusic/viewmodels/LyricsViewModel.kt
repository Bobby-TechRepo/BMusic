package com.bsoft.compose.bmusic.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.lyrics.Lyrics
import com.bsoft.compose.bmusic.data.lyrics.LyricsRepository
import com.bsoft.compose.bmusic.data.lyrics.LyricsResult
import com.bsoft.compose.bmusic.data.models.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val songId: Long? = null,
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    val imported: Boolean = false,
    /** A one-off message (string resource) to show, then clear with dismissMessage. */
    val message: Int? = null,
)

/** Lyrics of the song that is playing now. */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    queueManager: QueueManager,
    private val repository: LyricsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private var current: Song? = null

    init {
        viewModelScope.launch {
            queueManager.state.map { it.current }.distinctUntilChangedBy { it?.id }.collectLatest { song ->
                current = song
                if (song == null) {
                    _state.value = LyricsUiState()
                    return@collectLatest
                }
                _state.value = LyricsUiState(songId = song.id, loading = true)
                show(song, repository.load(song))
            }
        }
    }

    fun import(uri: Uri) {
        val song = current ?: return
        viewModelScope.launch {
            val result = try {
                repository.import(song, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                _state.update { it.copy(message = R.string.lyrics_import_failed) }
            } else {
                show(song, result)
            }
        }
    }

    fun removeImported() {
        val song = current ?: return
        viewModelScope.launch { show(song, repository.removeImported(song)) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Ignores results for a song that is no longer playing. */
    private fun show(song: Song, result: LyricsResult) {
        if (current?.id != song.id) return
        _state.update { it.copy(songId = song.id, loading = false, lyrics = result.lyrics, imported = result.imported) }
    }
}
