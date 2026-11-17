package com.bsoft.compose.bmusic.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Coordinator contract tests: assert that app queue state and Player commands stay aligned. */
class PlaybackCoordinatorTest {
    private lateinit var manager: QueueManager
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var player: Player
    private val a = MediaItem.Builder().setMediaId("1").build()
    private val b = MediaItem.Builder().setMediaId("2").build()
    private val c = MediaItem.Builder().setMediaId("3").build()

    @Before fun setUp() {
        manager = QueueManager()
        coordinator = PlaybackCoordinator(manager)
        player = mockk(relaxed = true)
        every { player.mediaItemCount } returns 3
        every { player.currentMediaItemIndex } returns 1
        every { player.currentPosition } returns 1200L
    }

    @Test fun `replace queue installs requested items and start position`() {
        coordinator.replaceQueue(player, listOf(a, b, c), 1, "playlist_7", 4500L)
        assertEquals(listOf("1", "2", "3"), manager.currentQueue.map { it.mediaId })
        assertEquals(1, manager.state.value.currentIndex)
        assertEquals("playlist_7", manager.currentContext)
        verify { player.setMediaItems(manager.currentQueue, 1, 4500L) }
        verify { player.prepare() }
    }

    @Test fun `remove queue entry updates logical queue and player`() {
        manager.setQueue(listOf(a, b, c), 1)
        coordinator.removeAt(player, 0)
        assertEquals(listOf("2", "3"), manager.currentQueue.map { it.mediaId })
        verify { player.removeMediaItem(0) }
    }

    @Test fun `move queue entry updates logical order and player`() {
        manager.setQueue(listOf(a, b, c), 0)
        coordinator.move(player, 0, 2)
        assertEquals(listOf("2", "3", "1"), manager.currentQueue.map { it.mediaId })
        verify { player.moveMediaItem(0, 2) }
    }

    @Test fun `add next inserts after current entry`() {
        manager.setQueue(listOf(a, b, c), 1)
        coordinator.addNext(player, MediaItem.Builder().setMediaId("4").build())
        assertEquals(listOf("1", "2", "4", "3"), manager.currentQueue.map { it.mediaId })
        verify { player.addMediaItem(2, any()) }
    }

    @Test fun `invalid remove and move are ignored`() {
        manager.setQueue(listOf(a, b, c), 1)
        coordinator.removeAt(player, -1)
        coordinator.move(player, 0, 99)
        assertEquals(listOf("1", "2", "3"), manager.currentQueue.map { it.mediaId })
        verify(exactly = 0) { player.removeMediaItem(any()) }
        verify(exactly = 0) { player.moveMediaItem(any(), any()) }
    }

    @Test fun `enabling shuffle keeps the playing entry and never replaces the timeline`() {
        val items = (1..6).map { MediaItem.Builder().setMediaId("$it").build() }
        manager.setQueue(items, 2)
        every { player.mediaItemCount } returns 6
        every { player.currentMediaItemIndex } returns 2

        coordinator.setShuffle(player, true)

        assertTrue(manager.state.value.shuffle)
        assertEquals("3", manager.currentQueue.first().mediaId) // playing song moves to the front
        // Everything around the playing entry is rebuilt; the playing entry itself is untouched.
        verify { player.removeMediaItems(3, 6) }
        verify { player.removeMediaItems(0, 2) }
        verify { player.addMediaItems(1, manager.currentQueue.subList(1, 6)) }
        verify(exactly = 0) { player.setMediaItems(any(), any<Int>(), any<Long>()) }
    }

    @Test fun `disabling shuffle restores source order around the playing entry`() {
        val items = (1..5).map { MediaItem.Builder().setMediaId("$it").build() }
        manager.setQueue(items, 3)
        manager.enableShuffle() // playing entry "4" is now at index 0
        every { player.mediaItemCount } returns 5
        every { player.currentMediaItemIndex } returns 0

        coordinator.setShuffle(player, false)

        assertEquals(listOf("1", "2", "3", "4", "5"), manager.currentQueue.map { it.mediaId })
        assertEquals(3, manager.state.value.currentIndex)
        verify { player.removeMediaItems(1, 5) }
        verify { player.addMediaItems(0, manager.currentQueue.subList(0, 3)) }
        verify { player.addMediaItems(4, manager.currentQueue.subList(4, 5)) }
        verify(exactly = 0) { player.setMediaItems(any(), any<Int>(), any<Long>()) }
    }

    @Test fun `setting the same shuffle mode does nothing`() {
        manager.setQueue(listOf(a, b, c), 1)
        coordinator.setShuffle(player, false)
        verify(exactly = 0) { player.removeMediaItems(any(), any()) }
        verify(exactly = 0) { player.setMediaItems(any(), any<Int>(), any<Long>()) }
    }

    @Test fun `addAt inserts a batch in order`() {
        manager.setQueue(listOf(a, b, c), 0)
        val d = MediaItem.Builder().setMediaId("4").build()
        val e = MediaItem.Builder().setMediaId("5").build()
        coordinator.addAt(player, 1, listOf(d, e))
        assertEquals(listOf("1", "4", "5", "2", "3"), manager.currentQueue.map { it.mediaId })
        verify { player.addMediaItems(1, listOf(d, e)) }
    }

    @Test fun `removeRange removes from the end backwards`() {
        manager.setQueue(listOf(a, b, c), 0)
        coordinator.removeRange(player, 1, 3)
        assertEquals(listOf("1"), manager.currentQueue.map { it.mediaId })
        verify { player.removeMediaItem(2) }
        verify { player.removeMediaItem(1) }
    }

    @Test fun `removeRange over everything clears the queue`() {
        manager.setQueue(listOf(a, b, c), 0)
        coordinator.removeRange(player, 0, 3)
        assertTrue(manager.currentQueue.isEmpty())
        verify { player.clearMediaItems() }
    }

    @Test fun `install sets items without preparing`() {
        coordinator.install(player, listOf(a, b, c), 2, "album_4", 0L)
        assertEquals("album_4", manager.currentContext)
        verify { player.setMediaItems(manager.currentQueue, 2, 0L) }
        verify(exactly = 0) { player.prepare() }
    }

    @Test fun `clear queue clears player and app queue`() {
        manager.setQueue(listOf(a, b, c), 1, "playlist_7")
        coordinator.clearQueue(player)
        assertTrue(manager.currentQueue.isEmpty())
        verify { player.clearMediaItems() }
    }

    @Test fun `installing the loaded queue again keeps a shuffled order`() {
        val items = (1..6).map { MediaItem.Builder().setMediaId("$it").build() }
        every { player.mediaItemCount } returns 6
        every { player.currentMediaItemIndex } returns 0
        manager.setQueue(items, 0, "album_1")
        coordinator.setShuffle(player, true)
        val shuffled = manager.currentQueue.map { it.mediaId }

        // What playback resumption does: hand the current queue back with a start point.
        coordinator.install(player, manager.currentQueue.toList(), 3, "album_1", 5_000L)

        assertEquals(shuffled, manager.currentQueue.map { it.mediaId })
        assertTrue(manager.state.value.shuffle)
        assertEquals(3, manager.state.value.currentIndex)
        verify { player.seekTo(3, 5_000L) }
        verify(exactly = 0) { player.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) }
    }

    @Test fun `installing a different list replaces the queue`() {
        manager.setQueue(listOf(a, b, c), 0)
        coordinator.install(player, listOf(c, b, a), 0, "songs", 0L)
        assertEquals(listOf("3", "2", "1"), manager.currentQueue.map { it.mediaId })
        verify { player.setMediaItems(manager.currentQueue, 0, 0L) }
    }
}
