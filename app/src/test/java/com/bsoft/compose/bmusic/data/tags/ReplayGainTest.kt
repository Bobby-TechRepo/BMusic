package com.bsoft.compose.bmusic.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {
    private fun near(expected: Float, actual: Float) = assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) < 0.001f)

    @Test
    fun `parses the usual tag text`() {
        val gain = ReplayGain.fromTags(
            mapOf(
                "REPLAYGAIN_TRACK_GAIN" to "-6.48 dB",
                "REPLAYGAIN_TRACK_PEAK" to "0.988",
                "REPLAYGAIN_ALBUM_GAIN" to "+1,5 dB",
                "REPLAYGAIN_ALBUM_PEAK" to "1.2",
            )
        )
        assertEquals(ReplayGain(-6.48f, 0.988f, 1.5f, 1.2f), gain)
    }

    @Test
    fun `opus R128 gains are converted to the ReplayGain reference`() {
        val gain = ReplayGain.fromTags(mapOf("R128_TRACK_GAIN" to "-2560", "R128_ALBUM_GAIN" to "256"))
        near(-5f, gain.trackGainDb!!)
        near(6f, gain.albumGainDb!!)
    }

    @Test
    fun `junk values are ignored`() {
        val gain = ReplayGain.fromTags(mapOf("REPLAYGAIN_TRACK_GAIN" to "loud", "REPLAYGAIN_TRACK_PEAK" to "0"))
        assertNull(gain.trackGainDb)
        assertNull(gain.trackPeak)
        assertTrue(gain.isEmpty)
    }

    @Test
    fun `negative gain turns the volume down`() {
        near(0.5012f, ReplayGain(trackGainDb = -6f).volumeFactor(ReplayGainMode.TRACK))
    }

    @Test
    fun `volume never goes above full`() {
        assertEquals(1f, ReplayGain(trackGainDb = 4f).volumeFactor(ReplayGainMode.TRACK))
    }

    @Test
    fun `peak prevents clipping`() {
        // -1 dB would be 0.891, but a peak of 1.25 allows at most 0.8.
        near(0.8f, ReplayGain(trackGainDb = -1f, trackPeak = 1.25f).volumeFactor(ReplayGainMode.TRACK))
    }

    @Test
    fun `modes pick their value and fall back to the other`() {
        val both = ReplayGain(trackGainDb = -6f, albumGainDb = -12f)
        near(0.2512f, both.volumeFactor(ReplayGainMode.ALBUM))
        near(0.5012f, ReplayGain(trackGainDb = -6f).volumeFactor(ReplayGainMode.ALBUM))
        near(0.2512f, ReplayGain(albumGainDb = -12f).volumeFactor(ReplayGainMode.TRACK))
        assertEquals(1f, both.volumeFactor(ReplayGainMode.OFF))
        assertEquals(1f, ReplayGain().volumeFactor(ReplayGainMode.TRACK))
    }

    @Test
    fun `mode names round trip and unknown means off`() {
        ReplayGainMode.entries.forEach { assertEquals(it, ReplayGainMode.fromName(it.name)) }
        assertEquals(ReplayGainMode.OFF, ReplayGainMode.fromName(null))
        assertEquals(ReplayGainMode.OFF, ReplayGainMode.fromName("LOUD"))
    }
}
