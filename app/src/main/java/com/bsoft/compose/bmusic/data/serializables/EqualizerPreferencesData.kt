package com.bsoft.compose.bmusic.data.serializables

import kotlinx.serialization.Serializable

@Serializable
data class EqualizerPreferencesData(
    val isEqualizerEnabled: Boolean = false,
    val selected: Short = 0,
    val values: List<Short> = emptyList(),
    /** Bass boost strength, 0 (off) to 1000. */
    val bassBoost: Int = 0,
    /** Virtualizer ("surround") strength, 0 (off) to 1000. */
    val virtualizer: Int = 0,
    /** Loudness boost in millibels, 0 (off) to 1000. */
    val loudnessGainMb: Int = 0,
) {
    companion object{
    }
}