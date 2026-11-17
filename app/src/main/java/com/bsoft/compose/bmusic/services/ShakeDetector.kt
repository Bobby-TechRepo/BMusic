package com.bsoft.compose.bmusic.services

import kotlin.math.sqrt

/**
 * Recognises a deliberate shake from accelerometer samples: [shakesNeeded] jolts stronger than
 * [thresholdG] within [windowMs]. After a shake it ignores input for [cooldownMs], so one
 * vigorous shake skips one song, not three. Pure logic; feed it from a SensorEventListener.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.7f,
    private val windowMs: Long = 600L,
    private val shakesNeeded: Int = 2,
    private val cooldownMs: Long = 1_500L,
) {
    private var firstJoltAt = Long.MIN_VALUE
    private var lastJoltAt = Long.MIN_VALUE
    private var jolts = 0
    private var lastShakeAt = Long.MIN_VALUE

    /** Returns true exactly when a shake completes. Values are in m/s², time in ms. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (lastShakeAt != Long.MIN_VALUE && timeMs - lastShakeAt < cooldownMs) return false
        val gForce = sqrt(x * x + y * y + z * z) / GRAVITY
        if (gForce < thresholdG) return false
        // One physical jolt produces several samples; count it once.
        if (lastJoltAt != Long.MIN_VALUE && timeMs - lastJoltAt < MIN_JOLT_GAP_MS) return false
        lastJoltAt = timeMs

        if (jolts == 0 || timeMs - firstJoltAt > windowMs) {
            firstJoltAt = timeMs
            jolts = 1
        } else {
            jolts++
        }
        if (jolts >= shakesNeeded) {
            jolts = 0
            lastShakeAt = timeMs
            return true
        }
        return false
    }

    private companion object {
        const val GRAVITY = 9.80665f
        const val MIN_JOLT_GAP_MS = 120L
    }
}
