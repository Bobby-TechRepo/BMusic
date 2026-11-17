package com.bsoft.compose.bmusic.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionsTest {

    private val base = CrossfadeContext(
        hasNext = true, repeatOne = false, sleepAtEndOfTrack = false,
        durationMs = 240_000, lengthMs = 6_000, gaplessAlbums = true, nextIsNextAlbumTrack = false
    )

    @Test
    fun oldSoundFadeSwitchBecomesFade() {
        assertEquals(TransitionMode.FADE, TransitionMode.resolve("", legacySoundFade = true))
        assertEquals(TransitionMode.OFF, TransitionMode.resolve("", legacySoundFade = false))
        assertEquals(TransitionMode.CROSSFADE, TransitionMode.resolve("CROSSFADE", legacySoundFade = true))
        assertEquals(TransitionMode.OFF, TransitionMode.resolve("NONSENSE", legacySoundFade = false))
    }

    @Test
    fun secondsAreClamped() {
        assertEquals(1_000L, transitionMs(0))
        assertEquals(6_000L, transitionMs(6))
        assertEquals(12_000L, transitionMs(40))
    }

    @Test
    fun crossfadeNeverTakesMoreThanAThirdOfTheSong() {
        assertEquals(6_000L, crossfadeLengthMs(6_000, 240_000))
        assertEquals(3_000L, crossfadeLengthMs(6_000, 9_000))
        assertEquals(0L, crossfadeLengthMs(6_000, 0))
    }

    @Test
    fun crossfadesOrdinarySongs() {
        assertTrue(shouldCrossfade(base))
    }

    @Test
    fun noCrossfadeWithoutANextSongOnRepeatOneOrBeforeSleep() {
        assertFalse(shouldCrossfade(base.copy(hasNext = false)))
        assertFalse(shouldCrossfade(base.copy(repeatOne = true)))
        assertFalse(shouldCrossfade(base.copy(sleepAtEndOfTrack = true)))
    }

    @Test
    fun noCrossfadeForUnknownLengthOrTinyOverlap() {
        assertFalse(shouldCrossfade(base.copy(durationMs = 0)))
        assertFalse(shouldCrossfade(base.copy(lengthMs = 300)))
    }

    @Test
    fun albumsInOrderStayGaplessOnlyWhenAsked() {
        assertFalse(shouldCrossfade(base.copy(nextIsNextAlbumTrack = true)))
        assertTrue(shouldCrossfade(base.copy(nextIsNextAlbumTrack = true, gaplessAlbums = false)))
    }

    @Test
    fun nextAlbumTrackDetection() {
        assertTrue(isNextTrackOfAlbum(5, 3, 5, 4))
        assertTrue(isNextTrackOfAlbum(5, 1003, 5, 1004))
        // Last track of disc 1 to the first of disc 2.
        assertTrue(isNextTrackOfAlbum(5, 1012, 5, 2001))
        assertFalse(isNextTrackOfAlbum(5, 3, 5, 5))
        assertFalse(isNextTrackOfAlbum(5, 3, 6, 4))
        assertFalse(isNextTrackOfAlbum(-1, 3, -1, 4))
        assertFalse(isNextTrackOfAlbum(5, 0, 5, 1))
    }

    @Test
    fun equalPowerCurvesKeepLoudnessEven() {
        assertEquals(1f, crossfadeOutVolume(0f), 0.0001f)
        assertEquals(0f, crossfadeInVolume(0f), 0.0001f)
        assertEquals(0f, crossfadeOutVolume(1f), 0.0001f)
        assertEquals(1f, crossfadeInVolume(1f), 0.0001f)
        for (step in 0..10) {
            val p = step / 10f
            val power = crossfadeOutVolume(p) * crossfadeOutVolume(p) + crossfadeInVolume(p) * crossfadeInVolume(p)
            assertEquals(1f, power, 0.0001f)
        }
        // Out-of-range progress is clamped.
        assertEquals(1f, crossfadeInVolume(3f), 0.0001f)
    }

    @Test
    fun fadeInIsShortButNeverInstant() {
        assertEquals(FADE_IN_MS, fadeInMsFor(6_000))
        assertEquals(500L, fadeInMsFor(1_000))
        assertEquals(300L, fadeInMsFor(200))
    }
}
