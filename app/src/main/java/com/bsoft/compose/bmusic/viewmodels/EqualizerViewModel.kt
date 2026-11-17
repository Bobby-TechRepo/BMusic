package com.bsoft.compose.bmusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.data.AudioEffectsManager
import com.bsoft.compose.bmusic.data.EqualizerManager
import com.bsoft.compose.bmusic.data.models.AudioEffectsSettings
import com.bsoft.compose.bmusic.data.preferences.EqualizerPreferences
import com.bsoft.compose.bmusic.data.states.EqualizerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val audioEffectsManager: AudioEffectsManager,
    private val equalizerPreferences: EqualizerPreferences) : ViewModel()
{
    private val mutableStateFlow = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = mutableStateFlow.asStateFlow()

    val isEqualizerEnabled: StateFlow<Boolean> = equalizerPreferences.isEqualizerEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false)

    val bandValues: StateFlow<List<Short>> = equalizerPreferences.values.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val selected: StateFlow<Short> = equalizerPreferences.selected.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0
    )

    init {
        // The playback service attaches the equalizer and applies the saved settings, so EQ
        // works even with no screen open. This ViewModel only mirrors its state for the UI.
        equalizerManager.setListeners(
            listener = {},
            updateListener = { refresh() }
        )
        refresh()
    }

    private fun refresh() {
        mutableStateFlow.update {
            it.copy(bands = equalizerManager.bands, presets = equalizerManager.presets, effectsSupport = audioEffectsManager.support)
        }
    }

    val effects: StateFlow<AudioEffectsSettings> = equalizerPreferences.effects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AudioEffectsSettings()
    )

    /** Applies effect levels immediately; [persist] saves them (on slider release, not every frame). */
    fun setEffects(settings: AudioEffectsSettings, persist: Boolean) {
        audioEffectsManager.apply(settings.bassBoost, settings.virtualizer, settings.loudnessGainMb)
        if (persist) equalizerPreferences.setEffects(settings)
    }

    override fun onCleared() {
        equalizerManager.clearListeners()
    }

    fun toggleEnable(){
        val init = !isEqualizerEnabled.value
        equalizerManager.enabled = init
        equalizerPreferences.enableEqualizer(init)
    }

    fun choosePreset(preset: Short){
        if(preset >= 0){
            equalizerManager.choosePreset(preset = preset)
            equalizerPreferences.setSelected(selected = preset)
        }else{
            var values = bandValues.value
            if(values.isEmpty()){
                values = state.value.bands.map { 0 }
                equalizerPreferences.setValues(values)
            }
            values.forEachIndexed { index, value -> equalizerManager.modifyBand(index.toShort(), value) }
        }
    }

    fun modifyBand(band: Short, level: Short){
        equalizerManager.modifyBand(band, level)
        equalizerPreferences.setSelected(selected = -1)

        val values: List<Short> = equalizerManager.bands.map { it.level }
        equalizerPreferences.setValues(values)
    }
}