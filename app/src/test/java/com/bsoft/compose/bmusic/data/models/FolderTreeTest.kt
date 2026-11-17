package com.bsoft.compose.bmusic.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private var nextId = 1L
    private fun song(path: String) = Song(
        id = nextId++,
        displayName = path.substringAfterLast('/'),
        title = path.substringAfterLast('/').substringBeforeLast('.'),
        artist = "A",
        album = "B",
        duration = 200_000,
        path = path
    )

    @Test
    fun singleVolumeShowsItsFoldersAtTheTop() {
        val root = FolderTree.build(listOf(
            song("/storage/emulated/0/Music/Rock/b.mp3"),
            song("/storage/emulated/0/Music/Rock/a.mp3"),
            song("/storage/emulated/0/Download/c.mp3"),
        ))

        assertEquals("", root.path)
        assertEquals(listOf("Download", "Music"), root.folders.map { it.name })
        assertEquals(3, root.songCount)
        val rock = root.find("/storage/emulated/0/Music/Rock")!!
        assertEquals(listOf("a.mp3", "b.mp3"), rock.songs.map { it.displayName })
        assertEquals(2, root.find("/storage/emulated/0/Music")!!.songCount)
    }

    @Test
    fun twoVolumesAreListedInternalFirst() {
        val root = FolderTree.build(listOf(
            song("/storage/1A2B-3C4D/Albums/x.flac"),
            song("/storage/emulated/0/Music/y.mp3"),
        ))

        assertEquals(listOf(VolumeKind.INTERNAL, VolumeKind.EXTERNAL), root.folders.map { it.volume })
        assertEquals("/storage/emulated/0", root.folders[0].path)
        assertEquals("1A2B-3C4D", root.folders[1].name)
        assertEquals(listOf("Albums"), root.folders[1].folders.map { it.name })
    }

    @Test
    fun allSongsPlaysThisFolderFirstThenSubFoldersInOrder() {
        val root = FolderTree.build(listOf(
            song("/storage/emulated/0/Music/Z/z1.mp3"),
            song("/storage/emulated/0/Music/top.mp3"),
            song("/storage/emulated/0/Music/A/a1.mp3"),
            song("/storage/emulated/0/Music/A/Deep/d1.mp3"),
        ))

        val music = root.find("/storage/emulated/0/Music")!!
        assertEquals(listOf("top.mp3", "a1.mp3", "d1.mp3", "z1.mp3"), music.allSongs().map { it.displayName })
    }

    @Test
    fun foldersAndSongsSortIgnoringCase() {
        val root = FolderTree.build(listOf(
            song("/storage/emulated/0/beta/x.mp3"),
            song("/storage/emulated/0/Alpha/x.mp3"),
            song("/storage/emulated/0/Alpha/B.mp3"),
            song("/storage/emulated/0/Alpha/a.mp3"),
        ))

        assertEquals(listOf("Alpha", "beta"), root.folders.map { it.name })
        assertEquals(listOf("a.mp3", "B.mp3", "x.mp3"), root.folders[0].songs.map { it.displayName })
    }

    @Test
    fun songsWithoutAPathAreLeftOut() {
        val root = FolderTree.build(listOf(song(""), song("/storage/emulated/0/Music/a.mp3")))
        assertEquals(1, root.songCount)
    }

    @Test
    fun trailGoesFromRootToTheFolder() {
        val root = FolderTree.build(listOf(song("/storage/emulated/0/Music/Rock/80s/a.mp3")))

        val trail = root.trail("/storage/emulated/0/Music/Rock/80s")
        assertEquals(listOf("", "Music", "Rock", "80s"), trail.map { it.name })
        assertTrue(root.trail("/nowhere").isEmpty())
        assertNull(root.find("/nowhere"))
    }

    @Test
    fun keysAreStableAndFindTheFolder() {
        val root = FolderTree.build(listOf(song("/storage/emulated/0/Music/Rock/a.mp3"), song("/storage/emulated/0/Music/Pop/b.mp3")))
        val rock = root.find("/storage/emulated/0/Music/Rock")!!

        assertEquals(folderKey("/storage/emulated/0/Music/Rock"), rock.key)
        assertNotEquals(rock.key, root.find("/storage/emulated/0/Music/Pop")!!.key)
        assertEquals(rock, root.findByKey(rock.key))
        assertTrue(rock.key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun pathsOutsideStorageStillGroup() {
        val root = FolderTree.build(listOf(song("/mnt/media/a.mp3"), song("/storage/emulated/0/Music/b.mp3")))

        assertEquals(listOf(VolumeKind.INTERNAL, VolumeKind.OTHER), root.folders.map { it.volume })
        assertEquals("/mnt/media", root.folders[1].folders[0].folders[0].path)
    }
}
