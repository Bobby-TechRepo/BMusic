package com.bsoft.compose.bmusic.viewmodels

import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.data.tags.ArtworkEdit
import com.bsoft.compose.bmusic.data.tags.TagField
import com.bsoft.compose.bmusic.data.tags.TagProblem
import com.bsoft.compose.bmusic.data.tags.TagReadResult
import com.bsoft.compose.bmusic.data.tags.TagValues
import com.bsoft.compose.bmusic.data.tags.TagEditorRepository
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

data class TagEditorState(
    val song: Song? = null,
    val loading: Boolean = true,
    val unsupported: Boolean = false,
    val readFailed: Boolean = false,
    val original: TagValues = TagValues(),
    val values: TagValues = TagValues(),
    /** The picture shown: the embedded one, a newly picked one, or null. */
    val artwork: ByteArray? = null,
    val artworkEdit: ArtworkEdit = ArtworkEdit.Keep,
    val format: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
) {
    val changed: Set<TagField> get() = values.changesFrom(original)
    val dirty: Boolean get() = changed.isNotEmpty() || artworkEdit != ArtworkEdit.Keep
    val problems: Map<TagField, TagProblem> get() = values.problems()
    val canSave: Boolean get() = dirty && problems.isEmpty() && !saving && !loading
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: TagEditorRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TagEditorState())
    val state: StateFlow<TagEditorState> = _state.asStateFlow()

    private var loadedId: Long? = null

    fun load(songId: Long) {
        if (loadedId == songId) return
        loadedId = songId
        viewModelScope.launch {
            val song = withContext(io) { songRepository.findSongById(songId) }
            if (song == null) {
                _state.value = TagEditorState(loading = false, readFailed = true)
                return@launch
            }
            _state.value = TagEditorState(song = song, loading = true)
            _state.value = when (val result = repository.read(song)) {
                is TagReadResult.Loaded -> TagEditorState(
                    song = song,
                    loading = false,
                    original = result.tags.values,
                    values = result.tags.values,
                    artwork = result.tags.artwork,
                    format = result.tags.format,
                )
                TagReadResult.Unsupported -> TagEditorState(song = song, loading = false, unsupported = true)
                is TagReadResult.Failed -> TagEditorState(song = song, loading = false, readFailed = true)
            }
        }
    }

    fun update(field: TagField, value: String) {
        _state.update { it.copy(values = it.values.with(field, value), saveError = null) }
    }

    /** Uses the picked image as the cover: scaled to at most [MAX_ARTWORK_PX] and saved as JPEG. */
    fun setArtwork(uri: Uri) {
        // The editor only opens on Android 11+, where ImageDecoder always exists.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        viewModelScope.launch {
            val bytes = withContext(io) { runCatching { encodeArtwork(uri) }.onFailure { Log.w(TAG, "Bad picture", it) }.getOrNull() }
                ?: return@launch
            _state.update { it.copy(artwork = bytes, artworkEdit = ArtworkEdit.Replace(bytes, "image/jpeg"), saveError = null) }
        }
    }

    fun removeArtwork() {
        _state.update { it.copy(artwork = null, artworkEdit = ArtworkEdit.Remove, saveError = null) }
    }

    /** The system approval dialog to show before [save]; null where editing isn't possible. */
    fun writeRequest(): IntentSender? {
        val song = _state.value.song ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) repository.writeRequest(song) else null
    }

    /** The user declined the system dialog. */
    fun onWriteDenied(message: String) {
        _state.update { it.copy(saveError = message) }
    }

    /** Writes the changes. Call once the user approved [writeRequest]. */
    fun save() {
        val current = _state.value
        val song = current.song ?: return
        if (!current.canSave) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val error = repository.write(song, current.values, current.changed, current.artworkEdit)
            _state.update {
                if (error == null) it.copy(saving = false, saved = true)
                else it.copy(saving = false, saveError = error)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun encodeArtwork(uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_ARTWORK_PX) {
                val scale = MAX_ARTWORK_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            // Software bitmap: hardware bitmaps cannot be compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val TAG = "TagEditorViewModel"
        /** Large enough for any screen, small enough not to bloat the file. */
        const val MAX_ARTWORK_PX = 1200
        const val JPEG_QUALITY = 90
    }
}
