package com.bsoft.compose.bmusic.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFadeTest {
    private val eps = 0.001f

    @Test
    fun `full volume in the middle of a track`() {
        assertEquals(1f, fadeVolume(60_000, 200_000), eps)
    }

    @Test
    fun `ramps up at the start`() {
        assertEquals(0f, fadeVolume(0, 200_000), eps)
        assertEquals(0.5f, fadeVolume(FADE_IN_MS / 2, 200_000), eps)
        assertEquals(1f, fadeVolume(FADE_IN_MS, 200_000), eps)
    }

    @Test
    fun `ramps down at the end`() {
        assertEquals(0.5f, fadeVolume(200_000 - FADE_OUT_MS / 2, 200_000), eps)
        assertEquals(0f, fadeVolume(200_000, 200_000), eps)
    }

    @Test
    fun `unknown duration is never silenced`() {
        assertEquals(1f, fadeVolume(0, 0), eps)
        assertEquals(1f, fadeVolume(5_000, -1), eps)
    }

    @Test
    fun `short tracks fade over at most a third each way`() {
        // 3 s track: fades are capped at 1 s, so the middle second plays at full volume.
        assertEquals(1f, fadeVolume(1_500, 3_000), eps)
        assertEquals(0.5f, fadeVolume(500, 3_000), eps)
    }
}
