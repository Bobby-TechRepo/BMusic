// Android deprecated Virtualizer in favour of the system Spatializer, but it still works on the
// phones that support it and is the only surround effect an app can apply on older Android.
@file:Suppress("DEPRECATION")

package com.bsoft.compose.bmusic.data

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

private const val TAG = "AudioEffectsManager"

/** Bass boost and virtualizer strength range (platform units). */
const val EFFECT_STRENGTH_MAX = 1000

/** Loudness boost range in millibels (0 to +10 dB). */
const val LOUDNESS_GAIN_MAX_MB = 1000

/** Which effects this device and audio session support. */
data class AudioEffectsSupport(val bassBoost: Boolean = false, val virtualizer: Boolean = false, val loudness: Boolean = false)

/**
 * Bass boost, virtualizer ("surround") and loudness boost on the player's audio session. Like
 * [EqualizerManager], creation never throws: unsupported effects are simply unavailable. Each
 * effect is enabled only while its level is above zero.
 */
class AudioEffectsManager {
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    var support = AudioEffectsSupport()
        private set

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        bassBoost = create("bass boost") { BassBoost(0, audioSessionId) }?.takeIf { it.strengthSupported }
        virtualizer = create("virtualizer") { Virtualizer(0, audioSessionId) }?.takeIf { it.strengthSupported }
        loudness = create("loudness") { LoudnessEnhancer(audioSessionId) }
        support = AudioEffectsSupport(bassBoost != null, virtualizer != null, loudness != null)
    }

    fun setBassBoost(strength: Int) {
        val effect = bassBoost ?: return
        runCatching {
            effect.setStrength(strength.coerceIn(0, EFFECT_STRENGTH_MAX).toShort())
            effect.enabled = strength > 0
        }.onFailure { Log.w(TAG, "Bass boost failed", it) }
    }

    fun setVirtualizer(strength: Int) {
        val effect = virtualizer ?: return
        runCatching {
            effect.setStrength(strength.coerceIn(0, EFFECT_STRENGTH_MAX).toShort())
            effect.enabled = strength > 0
        }.onFailure { Log.w(TAG, "Virtualizer failed", it) }
    }

    fun setLoudness(gainMb: Int) {
        val effect = loudness ?: return
        runCatching {
            effect.setTargetGain(gainMb.coerceIn(0, LOUDNESS_GAIN_MAX_MB))
            effect.enabled = gainMb > 0
        }.onFailure { Log.w(TAG, "Loudness boost failed", it) }
    }

    fun apply(bassStrength: Int, virtualizerStrength: Int, loudnessGainMb: Int) {
        setBassBoost(bassStrength)
        setVirtualizer(virtualizerStrength)
        setLoudness(loudnessGainMb)
    }

    fun release() {
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        bassBoost = null
        virtualizer = null
        loudness = null
        support = AudioEffectsSupport()
    }

    private inline fun <T> create(name: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "$name unavailable", e)
        null
    }
}
