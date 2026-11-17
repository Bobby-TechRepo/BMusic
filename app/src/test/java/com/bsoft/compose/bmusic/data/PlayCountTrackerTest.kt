package com.bsoft.compose.bmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayCountTrackerTest {

    private val step = 3_000L

    /** Simulates one-second ticks of continuous playback from [from] to [to]. */
    private fun PlayCountTracker.play(from: Long, to: Long): List<Long> {
        val counted = mutableListOf<Long>()
        var position = from
        while (position < to) {
            position += 1_000L
            onProgress(position, step)?.let(counted::add)
        }
        return counted
    }

    @Test
    fun `counts once after 30 seconds of a long track`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 7, durationMs = 240_000, positionMs = 0)

        assertEquals(emptyList<Long>(), tracker.play(0, 29_000))
        assertEquals(listOf(7L), tracker.play(29_000, 31_000))
        // Never counted twice for the same start.
        assertEquals(emptyList<Long>(), tracker.play(31_000, 200_000))
    }

    @Test
    fun `short track counts after half its length`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 3, durationMs = 40_000, positionMs = 0)
        assertEquals(20_000L, tracker.thresholdMs)
        assertEquals(listOf(3L), tracker.play(0, 21_000))
    }

    @Test
    fun `quick skip does not count`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 1, durationMs = 200_000, positionMs = 0)
        assertEquals(emptyList<Long>(), tracker.play(0, 5_000))
        tracker.start(songId = 2, durationMs = 200_000, positionMs = 0) // skipped to next
        assertEquals(emptyList<Long>(), tracker.play(0, 5_000))
    }

    @Test
    fun `restored position does not count as listening`() {
        val tracker = PlayCountTracker()
        // App restart restores the song at 2:00; nothing has been heard yet.
        tracker.start(songId = 9, durationMs = 240_000, positionMs = 120_000)
        assertNull(tracker.onProgress(121_000, step))
        assertEquals(emptyList<Long>(), tracker.play(121_000, 140_000))
        assertEquals(listOf(9L), tracker.play(140_000, 152_000))
    }

    @Test
    fun `seeking forward is not listening`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 4, durationMs = 300_000, positionMs = 0)
        tracker.play(0, 5_000)
        assertNull(tracker.onProgress(200_000, step)) // seek jump ignored
        assertEquals(emptyList<Long>(), tracker.play(200_000, 220_000))
    }

    @Test
    fun `repeat of the same song counts again`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 5, durationMs = 60_000, positionMs = 0)
        assertEquals(listOf(5L), tracker.play(0, 31_000))
        tracker.start(songId = 5, durationMs = 60_000, positionMs = 0)
        assertEquals(listOf(5L), tracker.play(0, 31_000))
    }

    @Test
    fun `reset stops counting`() {
        val tracker = PlayCountTracker()
        tracker.start(songId = 6, durationMs = 200_000, positionMs = 0)
        tracker.reset()
        assertEquals(emptyList<Long>(), tracker.play(0, 60_000))
    }
}
