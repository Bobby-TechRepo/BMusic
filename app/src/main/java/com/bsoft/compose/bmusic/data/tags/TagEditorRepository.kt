package com.bsoft.compose.bmusic.data.tags

import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.bsoft.compose.bmusic.data.lyrics.LyricsRepository
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.playback.PlaybackFileGuard
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.modules.IoDispatcher
import com.bsoft.compose.bmusic.utils.Util
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the editor shows for a song. */
data class SongTagsSnapshot(
    val values: TagValues,
    /** The embedded front cover, if any. */
    val artwork: ByteArray?,
    /** e.g. "FLAC · 44.1 kHz · 1,012 kbps", for the file details line. */
    val format: String,
)

/** A change to the embedded picture. */
sealed interface ArtworkEdit {
    data object Keep : ArtworkEdit
    data object Remove : ArtworkEdit
    class Replace(val bytes: ByteArray, val mimeType: String) : ArtworkEdit
}

sealed interface TagReadResult {
    data class Loaded(val tags: SongTagsSnapshot) : TagReadResult
    /** The file type has no tag writer (e.g. Opus): nothing can be edited. */
    data object Unsupported : TagReadResult
    data class Failed(val message: String?) : TagReadResult
}

/**
 * Reads and writes the tags inside music files, with jaudiotagger.
 *
 * Android 11+ only lets an app change another app's media files after the user approves it in a
 * system dialog ([writeRequest]); there is no direct file access. So a save:
 * 1. copies the song to app storage,
 * 2. edits the copy's tags,
 * 3. writes the copy back over the original through MediaStore (the approved route),
 * 4. asks Android to re-scan the file, and refreshes BMusic's library and caches.
 * If writing back fails part-way, the untouched copy is written back, so the file is never left
 * half-written.
 */
@Singleton
class TagEditorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val songRepository: SongRepository,
    private val audioTagRepository: AudioTagRepository,
    private val lyricsRepository: LyricsRepository,
    private val fileGuard: PlaybackFileGuard,
) {
    init {
        // jaudiotagger: use Android-safe code paths, and keep its very chatty logging quiet.
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** Editing needs MediaStore write requests (Android 11) and a known file type. */
    fun canEdit(song: Song): Boolean =
        isSupportedDevice && extensionOf(song) in EDITABLE_EXTENSIONS

    /** The system "Allow BMusic to modify this file?" dialog for [song]. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun writeRequest(song: Song): IntentSender =
        MediaStore.createWriteRequest(context.contentResolver, listOf(song.mediaUri)).intentSender

    suspend fun read(song: Song): TagReadResult = withContext(io) {
        if (extensionOf(song) !in EDITABLE_EXTENSIONS) return@withContext TagReadResult.Unsupported
        val work = workFile(song, "read")
        try {
            copyIn(song, work)
            val audio = AudioFileIO.read(work)
            val tag: Tag? = audio.tag
            val header = audio.audioHeader
            val values = TagValues(
                title = tag.text(FieldKey.TITLE).ifEmpty { song.title },
                artist = tag.text(FieldKey.ARTIST),
                album = tag.text(FieldKey.ALBUM),
                albumArtist = tag.text(FieldKey.ALBUM_ARTIST),
                genre = tag.text(FieldKey.GENRE),
                year = tag.text(FieldKey.YEAR),
                track = tag.text(FieldKey.TRACK),
                disc = tag.text(FieldKey.DISC_NO),
                lyrics = tag.text(FieldKey.LYRICS),
            )
            val format = listOfNotNull(
                header?.format?.takeIf { it.isNotBlank() },
                header?.sampleRateAsNumber?.takeIf { it > 0 }?.let { "%.1f kHz".format(it / 1000f) },
                header?.bitRateAsNumber?.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" · ")
            TagReadResult.Loaded(SongTagsSnapshot(values, tag?.firstArtwork?.binaryData, format))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tags of ${song.displayName}", e)
            TagReadResult.Failed(e.message)
        } finally {
            work.delete()
        }
    }

    /**
     * Writes [values] (only the [changed] fields) and [artwork] into the song's file. Call after
     * the user approved [writeRequest]. Returns null on success, or an error message.
     */
    suspend fun write(song: Song, values: TagValues, changed: Set<TagField>, artwork: ArtworkEdit): String? =
        fileGuard.editing(song.id) { writeFile(song, values, changed, artwork) }

    private suspend fun writeFile(song: Song, values: TagValues, changed: Set<TagField>, artwork: ArtworkEdit): String? = withContext(io) {
        val original = workFile(song, "original")
        val edited = workFile(song, "edited")
        try {
            copyIn(song, original)
            original.copyTo(edited, overwrite = true)

            val audio = AudioFileIO.read(edited)
            val tag = audio.tagOrCreateAndSetDefault
            val clean = values.cleaned()
            for (field in changed) tag.put(field, clean[field])
            when (artwork) {
                ArtworkEdit.Keep -> Unit
                ArtworkEdit.Remove -> tag.deleteArtworkField()
                is ArtworkEdit.Replace -> {
                    tag.deleteArtworkField()
                    tag.setField(ArtworkFactory.getNew().apply {
                        binaryData = artwork.bytes
                        mimeType = artwork.mimeType
                        pictureType = FRONT_COVER
                    })
                }
            }
            audio.commit()

            try {
                copyOut(edited, song)
            } catch (e: Exception) {
                // Put the original bytes back so the song is never left half-written.
                runCatching { copyOut(original, song) }
                throw e
            }
            refreshAfterWrite(song)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not write tags of ${song.displayName}", e)
            e.message ?: e.javaClass.simpleName
        } finally {
            original.delete()
            edited.delete()
        }
    }

    private suspend fun refreshAfterWrite(song: Song) {
        // MediaStore re-reads the file when the write closes; scanning makes sure (and tells us
        // when it is done, so the library refresh sees the new values).
        if (song.path.isNotEmpty()) {
            withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine { done ->
                    MediaScannerConnection.scanFile(context, arrayOf(song.path), null) { _, _ ->
                        if (done.isActive) done.resume(Unit)
                    }
                }
            }
        }
        audioTagRepository.forget(song.id)
        lyricsRepository.forget(song.id)
        Util.invalidateArtwork(song.mediaUri, song.artworkUri)
        songRepository.invalidate()
    }

    private fun copyIn(song: Song, target: File) {
        val input = context.contentResolver.openInputStream(song.mediaUri)
            ?: error("Cannot open ${song.displayName}")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
    }

    private fun copyOut(source: File, song: Song) {
        // "wt": write and truncate, so a shorter file has no leftover bytes at the end.
        val output = context.contentResolver.openOutputStream(song.mediaUri, "wt")
            ?: error("Cannot write ${song.displayName}")
        output.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    }

    /** A scratch file with the song's extension (jaudiotagger picks the format from it). */
    private fun workFile(song: Song, name: String): File {
        val dir = File(context.cacheDir, "tag-edit").apply { mkdirs() }
        return File(dir, "$name-${song.id}.${extensionOf(song)}")
    }

    private fun extensionOf(song: Song): String =
        fileExtension(song.path).ifEmpty { fileExtension(song.displayName) }

    private fun Tag?.text(key: FieldKey): String =
        this?.let { runCatching { it.getFirst(key) }.getOrNull() }.orEmpty()

    /** Empty clears the field; numbers drop their "/total" part. */
    private fun Tag.put(field: TagField, value: String) {
        val key = when (field) {
            TagField.TITLE -> FieldKey.TITLE
            TagField.ARTIST -> FieldKey.ARTIST
            TagField.ALBUM -> FieldKey.ALBUM
            TagField.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
            TagField.GENRE -> FieldKey.GENRE
            TagField.YEAR -> FieldKey.YEAR
            TagField.TRACK -> FieldKey.TRACK
            TagField.DISC -> FieldKey.DISC_NO
            TagField.LYRICS -> FieldKey.LYRICS
        }
        val text = if (field == TagField.TRACK || field == TagField.DISC) tagNumber(value) else value
        if (text.isEmpty()) deleteField(key) else setField(key, text)
    }

    companion object {
        private const val TAG = "TagEditor"
        /** ID3 / FLAC picture type 3: front cover. */
        private const val FRONT_COVER = 3
        private const val SCAN_TIMEOUT_MS = 5_000L

        /** MediaStore write requests exist from Android 11. */
        val isSupportedDevice: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
