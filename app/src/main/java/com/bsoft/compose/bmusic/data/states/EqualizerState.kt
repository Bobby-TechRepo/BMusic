package com.bsoft.compose.bmusic.data.states

import com.bsoft.compose.bmusic.data.AudioEffectsSupport
import com.bsoft.compose.bmusic.data.models.EqualizerBand
import com.bsoft.compose.bmusic.data.models.EqualizerPresent

data class EqualizerState(
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<EqualizerPresent> = emptyList(),
    /** Which extra effects the device supports for the current audio session. */
    val effectsSupport: AudioEffectsSupport = AudioEffectsSupport())