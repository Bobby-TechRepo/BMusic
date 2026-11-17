package com.bsoft.compose.bmusic.data.playback

import com.bsoft.compose.bmusic.data.models.FolderTree
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.Song

/*
 * "Open with BMusic": a music file tapped in a file manager, a download, a chat attachment...
 * If it is a song of the library, it plays with the rest of its folder, like any folder in the
 * Folders tab. Otherwise the file plays on its own.
 */

/** What Android told us about the opened file (pure values, so the rules are unit-testable). */
data class OpenedFileInfo(
    val scheme: String?,
    val authority: String?,
    /** The last path segment, e.g. the MediaStore ID in content://media/external/audio/media/42. */
    val lastSegment: String?,
    /** For file:// links, the absolute path. */
    val path: String?,
)

/** How to play an opened file. */
sealed interface OpenedFilePlan {
    /** A library song: play [songs] (its folder) from [startIndex], as [contextPrefix]. */
    data class Library(val songs: List<Song>, val startIndex: Int, val contextPrefix: String) : OpenedFilePlan

    /** Not in the library (or not readable as such): play the file by itself. */
    data object External : OpenedFilePlan
}

/** The library song an opened file is, if any: by MediaStore ID, or by path for file:// links. */
fun findLibrarySong(info: OpenedFileInfo, library: List<Song>): Song? {
    if (info.scheme == "content" && info.authority == "media") {
        val id = info.lastSegment?.toLongOrNull() ?: return null
        return library.firstOrNull { it.id == id }
    }
    if (info.scheme == "file") {
        val path = info.path?.takeIf { it.isNotEmpty() } ?: return null
        return library.firstOrNull { it.path == path }
    }
    // Other apps' links (file managers, downloads, chat apps) don't say which library song
    // they are; those files play on their own.
    return null
}

/** Plays a library song together with the rest of its folder, starting at it. */
fun planFor(info: OpenedFileInfo, library: List<Song>): OpenedFilePlan {
    val song = findLibrarySong(info, library) ?: return OpenedFilePlan.External
    val directory = song.path.substringBeforeLast('/', missingDelimiterValue = "")
    val folder = directory.takeIf { it.isNotEmpty() }?.let { FolderTree.build(library).find(it) }
    if (folder == null) return OpenedFilePlan.Library(listOf(song), 0, MediaSource.Custom.prefix)
    // A folder's own songs come first in allSongs(), so the song's index there is its position.
    val songs = folder.allSongs()
    val start = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    return OpenedFilePlan.Library(songs, start, MediaSource.Folder(folder.key).prefix)
}
