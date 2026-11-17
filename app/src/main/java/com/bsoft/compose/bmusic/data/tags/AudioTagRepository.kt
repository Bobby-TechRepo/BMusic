package com.bsoft.compose.bmusic.data.tags

import android.content.Context
import android.util.Log
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.modules.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioTagRepository"

/** ReplayGain values of songs, read from their tags and remembered for the session. */
@Singleton
class AudioTagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val cache = object : LinkedHashMap<Long, ReplayGain>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ReplayGain>?) = size > 128
    }

    /** Forgets what was read from this song's file (its tags just changed). */
    fun forget(songId: Long) {
        synchronized(cache) { cache.remove(songId) }
    }

    suspend fun replayGain(song: Song): ReplayGain = withContext(io) {
        synchronized(cache) { cache[song.id] }?.let { return@withContext it }
        val gain = try {
            context.contentResolver.openInputStream(song.mediaUri)?.use { input ->
                EmbeddedTagReader.read(input.buffered(64 * 1024)).replayGain
            } ?: ReplayGain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tags of ${song.id}", e)
            ReplayGain()
        }
        synchronized(cache) { cache[song.id] = gain }
        gain
    }
}
