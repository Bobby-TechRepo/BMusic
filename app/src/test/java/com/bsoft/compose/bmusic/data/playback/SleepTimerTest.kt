package com.bsoft.compose.bmusic.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000L
    private val timer = SleepTimer(clock = { now })

    @Test
    fun `starts off with full volume`() {
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertNull(timer.remainingMs())
        assertFalse(timer.isExpired())
        assertEquals(1f, timer.volumeFactor())
    }

    @Test
    fun `countdown reports remaining time and expires`() {
        timer.start(15)
        assertEquals(15 * 60_000L, timer.remainingMs())
        now += 10 * 60_000L
        assertEquals(5 * 60_000L, timer.remainingMs())
        assertFalse(timer.isExpired())
        now += 6 * 60_000L
        assertEquals(0L, timer.remainingMs())
        assertTrue(timer.isExpired())
    }

    @Test
    fun `start clamps to at least one minute`() {
        timer.start(0)
        assertEquals(60_000L, timer.remainingMs())
    }

    @Test
    fun `extend adds time to a running countdown`() {
        timer.start(15)
        now += 5 * 60_000L
        timer.extend(10)
        assertEquals(20 * 60_000L, timer.remainingMs())
        val state = timer.state.value as SleepTimerState.Counting
        assertEquals(25 * 60_000L, state.totalMs)
    }

    @Test
    fun `extend starts a countdown when off`() {
        timer.extend(10)
        assertEquals(10 * 60_000L, timer.remainingMs())
    }

    @Test
    fun `volume fades over the last ten seconds`() {
        timer.start(1)
        assertEquals(1f, timer.volumeFactor())
        now += 60_000L - SLEEP_FADE_MS / 2
        assertEquals(0.5f, timer.volumeFactor(), 0.001f)
        now += SLEEP_FADE_MS
        assertEquals(0f, timer.volumeFactor())
    }

    @Test
    fun `end of track has no countdown`() {
        timer.start(30)
        timer.stopAfterCurrentTrack()
        assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
        assertNull(timer.remainingMs())
        assertFalse(timer.isExpired())
        assertEquals(1f, timer.volumeFactor())
    }

    @Test
    fun `cancel turns it off`() {
        timer.start(30)
        timer.cancel()
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertNull(timer.remainingMs())
    }
}
