package com.bsoft.compose.bmusic.data.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How one song hands over to the next (Settings > Playback > Song transitions). */
enum class TransitionMode {
    /** Straight cut (or gapless, if the files are). */
    OFF,

    /** The ending song fades out, then the next one fades in. No overlap. */
    FADE,

    /** The next song starts under the ending one, which fades out while it fades in. */
    CROSSFADE;

    companion object {
        /**
         * The saved mode; builds from before this setting only had the "Sound fades as songs
         * end" switch, which is [FADE].
         */
        fun resolve(name: String?, legacySoundFade: Boolean): TransitionMode =
            entries.firstOrNull { it.name == name } ?: if (legacySoundFade) FADE else OFF
    }
}

const val TRANSITION_SECONDS_MIN = 1
const val TRANSITION_SECONDS_MAX = 12
const val TRANSITION_SECONDS_DEFAULT = 6

/** The setting in milliseconds, kept inside the allowed range. */
fun transitionMs(seconds: Int): Long =
    seconds.coerceIn(TRANSITION_SECONDS_MIN, TRANSITION_SECONDS_MAX) * 1_000L

/**
 * How long this track's crossfade lasts: the setting, but never more than a third of the track,
 * so short songs are still mostly heard on their own. 0 when the length is unknown.
 */
fun crossfadeLengthMs(settingMs: Long, durationMs: Long): Long =
    if (durationMs <= 0L || settingMs <= 0L) 0L else minOf(settingMs, durationMs / 3)

/**
 * Whether [next] is simply the following track of the same album (including the first track of
 * the next disc). Albums played in order are often mixed to run into each other (live albums,
 * DJ mixes, concept albums), so they stay gapless when the "Keep albums gapless" option is on.
 *
 * Tracks are MediaStore TRACK numbers: disc * 1000 + track.
 */
fun isNextTrackOfAlbum(currentAlbumId: Long, currentTrack: Int, nextAlbumId: Long, nextTrack: Int): Boolean {
    if (currentAlbumId < 0 || currentAlbumId != nextAlbumId) return false
    if (currentTrack <= 0 || nextTrack <= 0) return false
    if (nextTrack == currentTrack + 1) return true
    val nextDiscFirstTrack = (currentTrack / 1000 + 1) * 1000 + 1
    return currentTrack >= 1000 && nextTrack == nextDiscFirstTrack
}

/** What the player knows when deciding whether to crossfade into the next song. */
data class CrossfadeContext(
    val hasNext: Boolean,
    val repeatOne: Boolean,
    /** The sleep timer stops at the end of this song: let it end on its own. */
    val sleepAtEndOfTrack: Boolean,
    val durationMs: Long,
    val lengthMs: Long,
    val gaplessAlbums: Boolean,
    val nextIsNextAlbumTrack: Boolean,
)

/** Whether the current song should crossfade into the next one. */
fun shouldCrossfade(context: CrossfadeContext): Boolean = with(context) {
    when {
        !hasNext || repeatOne || sleepAtEndOfTrack -> false
        durationMs <= 0L || lengthMs < MIN_CROSSFADE_MS -> false
        gaplessAlbums && nextIsNextAlbumTrack -> false
        else -> true
    }
}

/** Shorter overlaps sound like a glitch rather than a crossfade. */
const val MIN_CROSSFADE_MS = 500L

/**
 * Equal-power crossfade curves. The outgoing song follows cos and the incoming one sin, so the
 * total loudness stays even through the overlap (a straight line would dip in the middle).
 * [progress] runs from 0 (overlap starts) to 1 (outgoing song silent).
 */
fun crossfadeOutVolume(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

fun crossfadeInVolume(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/** Fade-in at the start of each song in [TransitionMode.FADE]: short, so the start isn't lost. */
fun fadeInMsFor(fadeOutMs: Long): Long = minOf(FADE_IN_MS, fadeOutMs / 2).coerceAtLeast(300L)
