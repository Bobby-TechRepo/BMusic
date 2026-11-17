package com.bsoft.compose.bmusic

import androidx.media3.common.MediaItem
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBusinessLogicTest {

    @Test
    fun `Song_fromMediaItem should extract ID from contextual mediaId`() {
        // Case 1: Contextual ID with song ID after pipe
        val mediaItem1 = MediaItem.Builder().setMediaId("recently_played#10|12345").build()
        val song1 = Song.fromMediaItem(mediaItem1)
        assertEquals(12345L, song1.id)

        // Case 2: Pure song ID
        val mediaItem2 = MediaItem.Builder().setMediaId("67890").build()
        val song2 = Song.fromMediaItem(mediaItem2)
        assertEquals(67890L, song2.id)

        // Case 3: Contextual ID with ONLY index (The failing case)
        // If it's a virtual/contextual item like "songs#5", it doesn't contain a song ID.
        // Currently, it returns 5L. It should probably return 0L or similar to indicate invalid song.
        // Or we should update the logic to always include the song ID if we need it.
        // But for now, extracting '5' as a song ID is definitely a bug if it's just an index.
        val mediaItem3 = MediaItem.Builder().setMediaId("songs#5").build()
        val song3 = Song.fromMediaItem(mediaItem3)
        assertEquals(0L, song3.id)
    }

    @Test
    fun `QueueManager_currentOriginalIndex should be safe with empty queue`() {
        val queueManager = QueueManager()
        // Default state: empty queue, currentIndex = null
        assertEquals(0, queueManager.currentOriginalIndex)
    }

    @Test
    fun `QueueManager_getCurrentContextMediaId should be safe when queue is empty`() {
        val queueManager = QueueManager()
        val contextId = queueManager.getCurrentContextMediaId()
        // Current: "songs#0|-1" (if index defaults to 0 and current is null)
        assertEquals("songs#0|-1", contextId)
    }

    @Test
    fun `QueueManager_shuffle safety`() {
        val queueManager = QueueManager()
        // Enable shuffle on empty queue
        queueManager.enableShuffle()
        assertEquals(0, queueManager.currentQueue.size)
        
        // Disable shuffle on empty queue
        queueManager.disableShuffle()
        assertEquals(0, queueManager.currentQueue.size)
    }

    @Test
    fun `MediaIdUtil_shouldDetectVirtualAndRealItems`() {
        // Virtual items
        val isVirtual1 = com.bsoft.compose.bmusic.data.models.MediaIdUtil.isVirtual("songs#0")
        val isVirtual2 = com.bsoft.compose.bmusic.data.models.MediaIdUtil.isVirtual("recently_played#10|12345")
        val isVirtual3 = com.bsoft.compose.bmusic.data.models.MediaIdUtil.isVirtual("album_5#2")
        
        // Real items
        val isVirtualReal = com.bsoft.compose.bmusic.data.models.MediaIdUtil.isVirtual("12345")

        assertEquals(true, isVirtual1)
        assertEquals(true, isVirtual2)
        assertEquals(true, isVirtual3)
        assertEquals(false, isVirtualReal)
    }

    @Test
    fun `QueueManager_shouldFilterOutVirtualItems`() {
        val queueManager = QueueManager()
        val virtualItem = MediaItem.Builder().setMediaId("songs#0").build()
        val realItem = MediaItem.Builder().setMediaId("12345").build()
        
        queueManager.setQueue(listOf(virtualItem, realItem), 0)
        
        // QueueManager should only contain the real item
        assertEquals(1, queueManager.currentQueue.size)
        assertEquals("12345", queueManager.currentQueue.first().mediaId)
    }

    @Test
    fun `PlaybackService_simulatedOnSetMediaItemsGuard`() {
        // Simulate onSetMediaItems guard logic for poisoned queue
        val virtualItem = MediaItem.Builder().setMediaId("songs#0").build()
        
        // If fetch returns null or empty for a virtual item, we don't set it in the queue
        val fetchResult: List<MediaItem>? = null // simulated failed fetch
        
        val queueManager = QueueManager()
        val finalItems = if (com.bsoft.compose.bmusic.data.models.MediaIdUtil.isVirtual(virtualItem.mediaId)) {
            if (fetchResult.isNullOrEmpty()) {
                emptyList()
            } else {
                fetchResult
            }
        } else {
            listOf(virtualItem)
        }
        
        if (finalItems.isNotEmpty()) {
            queueManager.setQueue(finalItems, 0)
        }
        
        assertEquals(0, queueManager.currentQueue.size)
    }
}
