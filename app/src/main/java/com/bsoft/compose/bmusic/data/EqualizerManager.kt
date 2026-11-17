package com.bsoft.compose.bmusic.data

import android.media.audiofx.Equalizer
import android.util.Log
import com.bsoft.compose.bmusic.data.models.EqualizerBand
import com.bsoft.compose.bmusic.data.models.EqualizerPresent

private const val TAG = "EqualizerManager"

/**
 * Owns the platform [Equalizer] attached to the player's audio session.
 *
 * The playback service attaches it and applies the saved settings with [applySettings], so the
 * user's EQ is active even when playback starts without any screen open (Bluetooth, Auto).
 * Screens only observe it through [setListeners] and must call [clearListeners] when they go away.
 */
class EqualizerManager {
    private var _equalizer: Equalizer? = null

    val equalizer: Equalizer?
        get() = _equalizer

    /** False when the device or the current audio session does not support an equalizer. */
    val isAvailable: Boolean
        get() = _equalizer != null

    var enabled: Boolean
        get() = runCatching { _equalizer?.enabled }.getOrNull() ?: false
        set(value) {
            runCatching { _equalizer?.enabled = value }
                .onFailure { Log.w(TAG, "Could not toggle equalizer", it) }
        }

    val bandCount: Int
        get() = (runCatching { _equalizer?.numberOfBands }.getOrNull() ?: 0).toInt()

    val bands: List<EqualizerBand>
        get() = _equalizer?.let {
            runCatching {
                val range = it.bandLevelRange
                val min = range[0]
                val max = range[1]
                (0 until it.numberOfBands).map { band ->
                    EqualizerBand(
                        band = band.toShort(),
                        frequency = it.getCenterFreq(band.toShort()) / 1000,
                        level = it.getBandLevel(band.toShort()),
                        min = min, max = max
                    )
                }
            }.getOrElse { e ->
                Log.w(TAG, "Could not read equalizer bands", e)
                emptyList()
            }
        } ?: emptyList()

    val presets: List<EqualizerPresent>
        get() = _equalizer?.let {
            runCatching {
                (0 until it.numberOfPresets).map { preset ->
                    EqualizerPresent(preset = preset.toShort(), name = it.getPresetName(preset.toShort()))
                }
            }.getOrElse { e ->
                Log.w(TAG, "Could not read equalizer presets", e)
                emptyList()
            }
        } ?: emptyList()

    private var _listener: (() -> Unit)? = null
    private var _updateListener: (() -> Unit)? = null

    fun setListeners(listener: () -> Unit, updateListener: () -> Unit) {
        _listener = listener
        _updateListener = updateListener
    }

    /** Drop references to a screen's callbacks so a cleared ViewModel is not kept alive. */
    fun clearListeners() {
        _listener = null
        _updateListener = null
    }

    /**
     * Attaches a new equalizer to [audioSessionID]. Never throws: some devices or sessions
     * reject audio effects, in which case the equalizer is simply unavailable.
     */
    fun attach(audioSessionID: Int) {
        runCatching { _equalizer?.release() }
        _equalizer = null
        if (audioSessionID <= 0) {
            // 0 is the deprecated global output mix; never attach to it.
            _updateListener?.invoke()
            return
        }
        _equalizer = try {
            Equalizer(0, audioSessionID)
        } catch (e: Exception) {
            // IllegalArgumentException, UnsupportedOperationException or RuntimeException
            // depending on the device; playback must continue without EQ.
            Log.w(TAG, "Equalizer unavailable for session $audioSessionID", e)
            null
        }
        _listener?.invoke()
        _updateListener?.invoke()
    }

    /**
     * Applies saved settings. [selected] >= 0 picks a preset; a negative value applies the
     * custom [values], one level per band.
     */
    fun applySettings(isEnabled: Boolean, selected: Short, values: List<Short>) {
        val eq = _equalizer ?: return
        runCatching {
            if (selected >= 0 && selected < eq.numberOfPresets) {
                eq.usePreset(selected)
            } else if (selected < 0) {
                val count = eq.numberOfBands.toInt()
                values.take(count).forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level) }
            }
            eq.enabled = isEnabled
        }.onFailure { Log.w(TAG, "Could not apply equalizer settings", it) }
        _updateListener?.invoke()
    }

    fun release() {
        runCatching { _equalizer?.release() }
        _equalizer = null
    }

    fun choosePreset(preset: Short) {
        runCatching { _equalizer?.usePreset(preset) }
            .onFailure { Log.w(TAG, "Could not use preset $preset", it) }
        _updateListener?.invoke()
    }

    fun modifyBand(band: Short, level: Short) {
        runCatching { _equalizer?.setBandLevel(band, level) }
            .onFailure { Log.w(TAG, "Could not set band $band", it) }
        _updateListener?.invoke()
    }
}
