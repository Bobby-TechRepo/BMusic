package com.bsoft.compose.bmusic.data.models

/** Thin helpers over [MediaId], kept for existing callers. */
object MediaIdUtil {
    /** True when the ID is not a single song (a folder, a whole source, or unknown). */
    fun isVirtual(mediaId: String): Boolean = MediaId.parse(mediaId) !is MediaId.Track

    /** The song ID inside a media ID, or 0 when the ID does not name a song. */
    fun getSongId(mediaId: String): Long = MediaId.parse(mediaId).songIdOrNull ?: 0L
}
