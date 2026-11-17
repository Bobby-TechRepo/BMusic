package com.bsoft.compose.bmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSamplingTest {
    @Test
    fun largeSquareCoverIsSampledDownButStaysAboveTarget() {
        // 3000 / 16 = 187 >= 160, 3000 / 32 = 93 < 160.
        assertEquals(16, artworkSampleSize(3000, 3000, 160, 160))
    }

    @Test
    fun imageSmallerThanTargetIsNotSampled() {
        assertEquals(1, artworkSampleSize(120, 120, 160, 160))
        assertEquals(1, artworkSampleSize(300, 300, 160, 160))
    }

    @Test
    fun exactPowerOfTwoFitIsAllowed() {
        assertEquals(2, artworkSampleSize(320, 320, 160, 160))
    }

    @Test
    fun theShorterSideDecides() {
        // Wide banner: height limits the sample so neither side drops below target.
        assertEquals(2, artworkSampleSize(4000, 400, 160, 160))
    }

    @Test
    fun unknownOrInvalidSizesFallBackToOne() {
        assertEquals(1, artworkSampleSize(0, 0, 160, 160))
        assertEquals(1, artworkSampleSize(-1, 500, 160, 160))
        assertEquals(1, artworkSampleSize(500, 500, 0, 160))
    }
}
