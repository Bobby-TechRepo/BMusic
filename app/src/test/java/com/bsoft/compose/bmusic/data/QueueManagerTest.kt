package com.bsoft.compose.bmusic.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression tests for queue identity/order behavior. Media IDs are library song IDs. */
class QueueManagerTest {
    private lateinit var queueManager: QueueManager
    private val one = MediaItem.Builder().setMediaId("1").build()
    private val two = MediaItem.Builder().setMediaId("2").build()
    private val three = MediaItem.Builder().setMediaId("3").build()

    @Before fun setUp() { queueManager = QueueManager() }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    @Test fun `setQueue preserves order and selected entry`() {
        val selected = queueManager.setQueue(listOf(one, two, three), 1, "playlist_42")
        assertEquals(1, selected)
        assertEquals(listOf(one, two, three), queueManager.currentQueue)
        assertEquals(1, queueManager.state.value.currentIndex)
        assertEquals("playlist_42", queueManager.currentContext)
    }

    @Test fun `setQueue clamps invalid start index`() {
        assertEquals(2, queueManager.setQueue(listOf(one, two, three), 99))
        assertEquals(2, queueManager.state.value.currentIndex)
    }

    @Test fun `empty queue clears current selection`() {
        queueManager.setQueue(listOf(one), 0)
        queueManager.setQueue(emptyList(), 0)
        assertTrue(queueManager.currentQueue.isEmpty())
        assertNull(queueManager.state.value.currentIndex)
    }

    @Test fun `enabling and disabling shuffle keeps the selected entry`() {
        queueManager.setQueue(listOf(one, two, three), 1)
        val shuffledIndex = queueManager.enableShuffle()
        assertEquals(0, shuffledIndex)
        assertEquals("2", queueManager.currentQueue[shuffledIndex].mediaId)
        assertEquals(3, queueManager.currentQueue.map { it.mediaId }.toSet().size)

        val originalIndex = queueManager.disableShuffle()
        assertEquals(1, originalIndex)
        assertEquals(listOf("1", "2", "3"), queueManager.currentQueue.map { it.mediaId })
    }

    @Test fun `shuffle preserves duplicate entries as distinct queue positions`() {
        val duplicateQueue = listOf(one, two, one, three)
        queueManager.setQueue(duplicateQueue, 2)
        queueManager.enableShuffle()
        assertEquals(4, queueManager.currentQueue.size)
        assertEquals(2, queueManager.currentQueue.count { it.mediaId == "1" })
        assertEquals("1", queueManager.currentQueue.first().mediaId)
        assertEquals(0, queueManager.state.value.currentIndex)
    }

    @Test fun `removeAt removes one duplicate occurrence only`() {
        queueManager.setQueue(listOf(one, two, one, three), 2)
        val removed = queueManager.removeAt(0)
        assertNotNull(removed)
        assertEquals(listOf("2", "1", "3"), queueManager.currentQueue.map { it.mediaId })
        assertEquals(1, queueManager.state.value.currentIndex)
    }

    @Test fun `remove current entry selects a valid neighboring index`() {
        queueManager.setQueue(listOf(one, two, three), 1)
        queueManager.removeAt(1)
        assertEquals(listOf("1", "3"), queueManager.currentQueue.map { it.mediaId })
        assertEquals(1, queueManager.state.value.currentIndex)
    }

    @Test fun `move updates queue order and current index`() {
        queueManager.setQueue(listOf(one, two, three), 0)
        assertTrue(queueManager.move(0, 2))
        assertEquals(listOf("2", "3", "1"), queueManager.currentQueue.map { it.mediaId })
        assertEquals(2, queueManager.state.value.currentIndex)
    }

    @Test fun `move rejects invalid positions`() {
        queueManager.setQueue(listOf(one, two), 0)
        assertFalse(queueManager.move(-1, 1))
        assertFalse(queueManager.move(0, 9))
        assertEquals(listOf("1", "2"), queueManager.currentQueue.map { it.mediaId })
    }

    @Test fun `insertAt preserves existing selection and inserts entry`() {
        queueManager.setQueue(listOf(one, two, three), 1)
        val inserted = queueManager.insertAt(1, MediaItem.Builder().setMediaId("4").build())
        assertEquals(1, inserted)
        assertEquals(listOf("1", "4", "2", "3"), queueManager.currentQueue.map { it.mediaId })
        assertEquals(2, queueManager.state.value.currentIndex)
    }

    @Test fun `restore keeps the saved play order and can unshuffle it`() {
        val manager = QueueManager()
        val playOrder = listOf(item("3"), item("1"), item("4"), item("2"))
        // "3" was originally third, "1" first, and so on.
        val index = manager.restore(playOrder, listOf(2, 0, 3, 1), 2, shuffle = true, repeatMode = Player.REPEAT_MODE_ONE, contextPrefix = "album_9")

        assertEquals(2, index)
        assertEquals(listOf("3", "1", "4", "2"), manager.currentQueue.map { it.mediaId })
        assertTrue(manager.state.value.shuffle)
        assertEquals(Player.REPEAT_MODE_ONE, manager.state.value.repeatMode)
        assertEquals("album_9", manager.currentContext)

        val sourceIndex = manager.disableShuffle()
        assertEquals(listOf("1", "2", "3", "4"), manager.currentQueue.map { it.mediaId })
        assertEquals(3, sourceIndex) // "4" was playing, originally at index 3
    }

    @Test fun `restore tolerates gaps left by deleted songs`() {
        val manager = QueueManager()
        // Saved order had 5 entries; the one with source position 2 was deleted.
        manager.restore(listOf(item("b"), item("d"), item("a")), listOf(1, 4, 0), 0, shuffle = true, repeatMode = Player.REPEAT_MODE_OFF, contextPrefix = "songs")
        manager.disableShuffle()
        assertEquals(listOf("a", "b", "d"), manager.currentQueue.map { it.mediaId })
    }

    @Test fun `restore without a valid source order uses play order`() {
        val manager = QueueManager()
        manager.restore(listOf(item("x"), item("y")), emptyList(), 1, shuffle = true, repeatMode = Player.REPEAT_MODE_OFF, contextPrefix = "songs")
        assertEquals(listOf(0, 1), manager.sourceOrder)
        assertEquals(1, manager.state.value.currentIndex)
    }

    @Test fun `repeat mode is retained in queue state`() {
        queueManager.setRepeatMode(Player.REPEAT_MODE_ONE)
        assertEquals(Player.REPEAT_MODE_ONE, queueManager.state.value.repeatMode)
    }
}
