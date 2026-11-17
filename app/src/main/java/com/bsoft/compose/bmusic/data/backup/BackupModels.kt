package com.bsoft.compose.bmusic.data.backup

import com.bsoft.compose.bmusic.data.models.LibrarySearch
import com.bsoft.compose.bmusic.data.models.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * A song as stored in a backup: the MediaStore ID plus enough metadata to find the same song
 * again on another phone, where IDs differ.
 */
@Serializable
data class BackupSong(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
)

@Serializable
data class BackupPlaylist(val name: String, val songs: List<BackupSong>)

@Serializable
data class BackupPlayCount(val song: BackupSong, val played: Int, val lastPlayed: Long)

@Serializable
data class BackupListen(val song: BackupSong, val at: Long)

/** Lyrics the user imported for a song, as the original .lrc text. */
@Serializable
data class BackupLyrics(val song: BackupSong, val lrc: String)

/** The whole backup file. [version] lets future builds read older files. */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = 0L,
    val favourites: List<BackupSong> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val playCounts: List<BackupPlayCount> = emptyList(),
    val listens: List<BackupListen> = emptyList(),
    /** Added after version 1 shipped; older files simply have none, older apps ignore it. */
    val lyrics: List<BackupLyrics> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Throws [kotlinx.serialization.SerializationException] (an IllegalArgumentException) for a bad file. */
    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)
}

fun Song.toBackupSong() = BackupSong(id = id, title = title, artist = artist, album = album, durationMs = duration)

/**
 * Finds backed-up songs in the current library. The same MediaStore ID only counts when the
 * title also matches, because IDs are reused on another phone or after a rescan. Otherwise it
 * matches on title and artist, preferring a length within 3 seconds.
 */
class SongMatcher(library: List<Song>) {
    private val byId = library.associateBy { it.id }
    private val byTitleArtist = library.groupBy { key(it.title, it.artist) }

    fun match(song: BackupSong): Song? {
        byId[song.id]?.let { if (same(it.title, song.title)) return it }
        val candidates = byTitleArtist[key(song.title, song.artist)].orEmpty()
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { song.durationMs > 0 && abs(it.duration - song.durationMs) <= DURATION_TOLERANCE_MS }
            ?: candidates.first()
    }

    private fun same(a: String, b: String) = LibrarySearch.normalize(a).trim() == LibrarySearch.normalize(b).trim()

    private fun key(title: String, artist: String) =
        LibrarySearch.normalize(title).trim() + "\u0000" + LibrarySearch.normalize(artist).trim()

    private companion object {
        const val DURATION_TOLERANCE_MS = 3_000L
    }
}
