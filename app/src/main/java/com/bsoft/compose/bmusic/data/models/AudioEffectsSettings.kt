package com.bsoft.compose.bmusic.data.models

/** Saved levels for the extra audio effects; 0 means off. */
data class AudioEffectsSettings(
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessGainMb: Int = 0,
)
