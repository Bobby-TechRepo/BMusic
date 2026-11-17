package com.bsoft.compose.bmusic.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    private val strong = 35f // ~3.6 g
    private val still = 9.8f // gravity only

    @Test
    fun `resting phone never shakes`() {
        val detector = ShakeDetector()
        repeat(100) { assertFalse(detector.onSample(0f, 0f, still, it * 20L)) }
    }

    @Test
    fun `two jolts within the window are a shake`() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(strong, 0f, 0f, 0))
        assertTrue(detector.onSample(strong, 0f, 0f, 300))
    }

    @Test
    fun `samples from one jolt count once`() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(strong, 0f, 0f, 0))
        assertFalse(detector.onSample(strong, 0f, 0f, 40)) // same jolt
        assertFalse(detector.onSample(strong, 0f, 0f, 80)) // same jolt
    }

    @Test
    fun `jolts too far apart are not a shake`() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(strong, 0f, 0f, 0))
        assertFalse(detector.onSample(strong, 0f, 0f, 2_000))
    }

    @Test
    fun `cooldown prevents skipping several songs at once`() {
        val detector = ShakeDetector()
        detector.onSample(strong, 0f, 0f, 0)
        assertTrue(detector.onSample(strong, 0f, 0f, 300))
        assertFalse(detector.onSample(strong, 0f, 0f, 600))
        assertFalse(detector.onSample(strong, 0f, 0f, 900))
        // After the cooldown a new shake works again.
        assertFalse(detector.onSample(strong, 0f, 0f, 2_000))
        assertTrue(detector.onSample(strong, 0f, 0f, 2_300))
    }
}
