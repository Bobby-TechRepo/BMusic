package com.bsoft.compose.bmusic.data.playback

/** Length of the fade-in at the start of each track. */
const val FADE_IN_MS = 1_500L

/** Length of the fade-out before each track ends. */
const val FADE_OUT_MS = 4_000L

/**
 * Player volume for the "Sound fades as songs end" setting: ramps up over the first
 * [fadeInMs] of a track and down over its last [fadeOutMs]. Returns 1 when the duration is
 * unknown, so streams of unknown length are never silenced.
 */
fun fadeVolume(
    positionMs: Long,
    durationMs: Long,
    fadeInMs: Long = FADE_IN_MS,
    fadeOutMs: Long = FADE_OUT_MS,
): Float {
    if (durationMs <= 0L) return 1f
    val position = positionMs.coerceIn(0L, durationMs)
    // Very short tracks: never fade longer than a third of the track each way.
    val fadeIn = minOf(fadeInMs, durationMs / 3).coerceAtLeast(1L)
    val fadeOut = minOf(fadeOutMs, durationMs / 3).coerceAtLeast(1L)
    val rampUp = position.toFloat() / fadeIn
    val rampDown = (durationMs - position).toFloat() / fadeOut
    return minOf(1f, rampUp, rampDown).coerceIn(0f, 1f)
}
