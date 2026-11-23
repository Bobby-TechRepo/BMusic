package com.bsoft.compose.bmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.AudioEffectsSupport
import com.bsoft.compose.bmusic.data.EFFECT_STRENGTH_MAX
import com.bsoft.compose.bmusic.data.LOUDNESS_GAIN_MAX_MB
import com.bsoft.compose.bmusic.data.models.AudioEffectsSettings
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.data.models.EqualizerPresent
import com.bsoft.compose.bmusic.data.states.EqualizerState
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

@Composable
fun EqualizerView(
    modifier: Modifier = Modifier, enabled: Boolean = false, state: EqualizerState = EqualizerState(), selectedBand: Short,
    customValues: List<Short> = emptyList(), toggleEnable: ()-> Unit, bandModified: (Short, Short)-> Unit, selectedPreset: (EqualizerPresent)->Unit,
    effects: AudioEffectsSettings = AudioEffectsSettings(),
    /** Called while dragging (persist = false) and on release (persist = true). */
    onEffectsChange: (AudioEffectsSettings, persist: Boolean) -> Unit = { _, _ -> }
) {
    Column(modifier = modifier.fillMaxSize().padding(top = 30.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // Switch to enable/disable equalizer
        EqualizerSwitch(enabled = enabled){
            toggleEnable()
        }

        // Equalizer sliders (shown only when enabled)
        AnimatedVisibility(visible = enabled, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            EqualizerControls(bands = state.bands){
                    band, level -> bandModified(band, level)
            }
        }

        // Presets (shown only when enabled)
        AnimatedVisibility(visible = enabled, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            PresetsView(selectedBand = selectedBand, customValues = customValues, presets = state.presets){ preset ->
                selectedPreset(preset)
            }
        }

        // Bass boost, surround and loudness work independently of the equalizer switch.
        AudioEffectsControls(support = state.effectsSupport, effects = effects, onChange = onEffectsChange)
    }
}

@Composable
private fun AudioEffectsControls(
    support: AudioEffectsSupport,
    effects: AudioEffectsSettings,
    onChange: (AudioEffectsSettings, persist: Boolean) -> Unit
) {
    // Local copy so the knobs turn smoothly; saved values flow back in through [effects].
    var live by remember(effects) { mutableStateOf(effects) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sound_effects), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        if (!support.bassBoost && !support.virtualizer && !support.loudness) R.string.effects_unsupported
                        else R.string.effects_knob_hint
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val count = listOf(support.bassBoost, support.virtualizer, support.loudness).count { it }
            val percentFormat = stringResource(R.string.percent_value)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Up to 104dp each, smaller on narrow phones so three always fit in one row.
                val knobSize = if (count == 0) 104.dp else minOf(104.dp, maxWidth / count - 16.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (support.bassBoost) {
                        RotaryKnob(
                            value = live.bassBoost, max = EFFECT_STRENGTH_MAX, label = stringResource(R.string.bass_boost), size = knobSize,
                            valueText = { String.format(percentFormat, it * 100 / EFFECT_STRENGTH_MAX) },
                            onValueChange = { live = live.copy(bassBoost = it); onChange(live, false) },
                            onValueChangeFinished = { onChange(live, true) }
                        )
                    }
                    if (support.virtualizer) {
                        RotaryKnob(
                            value = live.virtualizer, max = EFFECT_STRENGTH_MAX, label = stringResource(R.string.virtualizer), size = knobSize,
                            valueText = { String.format(percentFormat, it * 100 / EFFECT_STRENGTH_MAX) },
                            onValueChange = { live = live.copy(virtualizer = it); onChange(live, false) },
                            onValueChangeFinished = { onChange(live, true) }
                        )
                    }
                    if (support.loudness) {
                        RotaryKnob(
                            value = live.loudnessGainMb, max = LOUDNESS_GAIN_MAX_MB, label = stringResource(R.string.loudness_boost), size = knobSize,
                            valueText = { String.format(percentFormat, it * 100 / LOUDNESS_GAIN_MAX_MB) },
                            onValueChange = { live = live.copy(loudnessGainMb = it); onChange(live, false) },
                            onValueChangeFinished = { onChange(live, true) }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun EqualizerViewPreview(){
    BMusicTheme {
        Surface(color = Color.DarkGray) {
            //EqualizerView(, selectedPreset = {}, toggleEnable = {}, bandModified = { band, level -> })
        }
    }
}