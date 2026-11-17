package com.bsoft.compose.bmusic.data.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LyricsRepository"

/** Imported .lrc files larger than this are not lyrics. */
private const val MAX_LRC_BYTES = 1024 * 1024

/** Lyrics for one song, and whether they came from a file the user imported. */
data class LyricsResult(val lyrics: Lyrics?, val imported: Boolean)

/**
 * Finds lyrics for a song: a .lrc file the user imported for it wins, then lyrics embedded in
 * the audio file's tags. Android's storage rules stop apps reading .lrc files that sit next to
 * the music, so those are imported once through the file picker and kept in app storage.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val directory: File get() = File(context.filesDir, "lyrics")

    /** Recently shown songs, so swiping back and forth does not re-read files. */
    private val cache = object : LinkedHashMap<Long, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LyricsResult>?) = size > 24
    }

    /** Forgets cached lyrics for this song (its tags just changed). */
    fun forget(songId: Long) {
        synchronized(cache) { cache.remove(songId) }
    }

    suspend fun load(song: Song): LyricsResult = withContext(io) {
        synchronized(cache) { cache[song.id] }?.let { return@withContext it }
        val result = readImported(song.id)?.let { LyricsResult(it, imported = true) }
            ?: LyricsResult(readEmbedded(song), imported = false)
        synchronized(cache) { cache[song.id] = result }
        result
    }

    /** Saves the picked file as this song's lyrics. Null when the file holds no lyrics. */
    suspend fun import(song: Song, uri: Uri): LyricsResult? = withContext(io) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val data = input.readNBytesCompat(MAX_LRC_BYTES + 1)
            if (data.size > MAX_LRC_BYTES) null else data
        } ?: return@withContext null
        val text = decodeText(bytes)
        if (!saveImported(song, text, overwrite = true)) return@withContext null
        load(song)
    }

    /**
     * Stores [text] as the imported lyrics of [song]. False when it holds no lyrics, or when
     * the song already has imported lyrics and [overwrite] is off (restoring keeps what's here).
     */
    suspend fun saveImported(song: Song, text: String, overwrite: Boolean): Boolean = withContext(io) {
        val file = File(directory, "${song.id}.lrc")
        if (!overwrite && file.isFile) return@withContext false
        val lyrics = LrcParser.parse(text) ?: return@withContext false
        directory.mkdirs()
        file.writeText(text)
        synchronized(cache) { cache[song.id] = LyricsResult(lyrics, imported = true) }
        true
    }

    /** Every imported .lrc file, as (song ID, text), for backups. */
    suspend fun importedLyrics(): List<Pair<Long, String>> = withContext(io) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".lrc") }.orEmpty()
            .mapNotNull { file ->
                val songId = file.name.removeSuffix(".lrc").toLongOrNull() ?: return@mapNotNull null
                try {
                    songId to file.readText()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read ${file.name} for backup", e)
                    null
                }
            }
            .sortedBy { it.first }
    }

    /** Deletes an imported file; the embedded lyrics (if any) show again. */
    suspend fun removeImported(song: Song): LyricsResult = withContext(io) {
        File(directory, "${song.id}.lrc").delete()
        synchronized(cache) { cache.remove(song.id) }
        load(song)
    }

    private fun readImported(songId: Long): Lyrics? {
        val file = File(directory, "$songId.lrc")
        if (!file.isFile) return null
        return try {
            LrcParser.parse(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read imported lyrics for $songId", e)
            null
        }
    }

    private fun readEmbedded(song: Song): Lyrics? = try {
        context.contentResolver.openInputStream(song.mediaUri)?.use { input ->
            EmbeddedLyricsReader.read(input.buffered(64 * 1024))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Deleted file, revoked permission, unreadable storage: just no lyrics.
        Log.w(TAG, "Could not read tags of ${song.id}", e)
        null
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
