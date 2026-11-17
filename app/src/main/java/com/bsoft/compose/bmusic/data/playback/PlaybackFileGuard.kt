package com.bsoft.compose.bmusic.data.playback

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets a song's file be rewritten (tag editor) safely while it may be playing. The player is
 * reading that file; changing it underneath would make it decode garbage or fail. So, for the
 * duration of [editing], the playback service lets go of the song (remembering where it was)
 * and afterwards reloads it from the new file, with its new title, artist and artwork.
 */
@Singleton
class PlaybackFileGuard @Inject constructor() {

    /** Implemented by the playback service while it runs. */
    interface Handler {
        /** Stops reading [songId]'s file if it is the current song; true if it did. */
        suspend fun release(songId: Long): Boolean

        /** Reloads the current song from its (rewritten) file and resumes where it was. */
        suspend fun restore(songId: Long)
    }

    @Volatile
    var handler: Handler? = null

    suspend fun <T> editing(songId: Long, block: suspend () -> T): T {
        val current = handler
        val released = current?.release(songId) == true
        try {
            return block()
        } finally {
            // Also after a failed write: the original bytes are back, so playing them is safe.
            if (released) current?.restore(songId)
        }
    }
}
