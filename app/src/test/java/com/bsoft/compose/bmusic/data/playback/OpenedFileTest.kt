package com.bsoft.compose.bmusic.data.playback

import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.models.folderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenedFileTest {

    private fun song(id: Long, path: String) = Song(
        id = id, displayName = path.substringAfterLast('/'), title = "t$id", artist = "a", album = "b",
        duration = 1000, path = path
    )

    private val library = listOf(
        song(1, "/storage/emulated/0/Music/Rock/b.mp3"),
        song(2, "/storage/emulated/0/Music/Rock/a.mp3"),
        song(3, "/storage/emulated/0/Music/Rock/Live/c.mp3"),
        song(4, "/storage/emulated/0/Download/d.mp3"),
    )

    private fun media(id: String) = OpenedFileInfo("content", "media", id, "/external/audio/media/$id")

    @Test
    fun mediaStoreLinksFindTheLibrarySong() {
        assertEquals(1L, findLibrarySong(media("1"), library)?.id)
        assertNull(findLibrarySong(media("99"), library))
        assertNull(findLibrarySong(media("not-a-number"), library))
    }

    @Test
    fun fileLinksMatchByPath() {
        val info = OpenedFileInfo("file", null, "d.mp3", "/storage/emulated/0/Download/d.mp3")
        assertEquals(4L, findLibrarySong(info, library)?.id)
        assertNull(findLibrarySong(info.copy(path = "/sdcard/other.mp3"), library))
    }

    @Test
    fun otherAppsLinksPlayOnTheirOwn() {
        val download = OpenedFileInfo("content", "com.android.providers.downloads.documents", "msf:31", null)
        assertEquals(OpenedFilePlan.External, planFor(download, library))
    }

    @Test
    fun libraryFilesPlayWithTheirFolderFromThatSong() {
        val plan = planFor(media("1"), library) as OpenedFilePlan.Library
        // Folder order: a.mp3, b.mp3, then the Live sub-folder.
        assertEquals(listOf(2L, 1L, 3L), plan.songs.map { it.id })
        assertEquals(1, plan.startIndex)
        assertEquals(MediaSource.Folder(folderKey("/storage/emulated/0/Music/Rock")).prefix, plan.contextPrefix)
    }
}
