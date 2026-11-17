package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.data.states.PlayingState
import com.bsoft.compose.bmusic.data.states.QueueState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.utils.rememberReduceMotion
import com.bsoft.compose.bmusic.utils.toTimeFormat

data class SeekerState(val value: Long = 0, val use: Boolean = false )

/**
 * The player's seek bar: a wavy line that moves while playing, with a round thumb.
 *
 * Material 3 1.5 only allows a custom thumb and track on the [SliderState] form of [Slider], so
 * the bar keeps a state whose range follows the song length and whose value follows playback
 * (or the finger while dragging).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Seeker(modifier: Modifier = Modifier, playingState: PlayingState, queueState: QueueState, seek: (Long)-> Unit){
    var localState by remember { mutableStateOf(SeekerState()) }

    LaunchedEffect(playingState.position) {
        if(localState.use){
            localState = SeekerState(use = false)
        }
    }

    // A zero-length range is not allowed, so unknown durations use 0..1 (the bar stays empty).
    val duration = (queueState.current?.duration?.toFloat() ?: 0f).coerceAtLeast(1f)
    val shown = (if (localState.use) localState.value else playingState.position).toFloat().coerceIn(0f, duration)
    val sliderState = remember(duration) { SliderState(value = shown, trackRange = 0f..duration) }
    LaunchedEffect(sliderState, shown) {
        if (!sliderState.isDragging) sliderState.value = shown
    }

    // TalkBack reads "1:23 of 3:45" instead of a bare percentage.
    val positionLabel = stringResource(
        R.string.seek_position,
        shown.toLong().toTimeFormat(),
        (queueState.current?.duration ?: 0L).toTimeFormat()
    )
    val reduceMotion = rememberReduceMotion()

    Slider(
        state = sliderState,
        modifier = modifier.semantics { stateDescription = positionLabel },
        onValueChange = {
            sliderState.value = it
            seek(it.toLong())
            localState = SeekerState(value = it.toLong(), use = true)
        },
        thumb = { state ->
            Surface(modifier = Modifier.size(20.dp),
                shadowElevation =  1.dp,
                border = BorderStroke( width = 2.dp,
                    color = if(state.isDragging) MaterialTheme.colorScheme.primary else Color.White),
                shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            }
        },
        track = { state ->
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(),
                stroke = Stroke(width = 3f),
                trackStroke = Stroke(width = 3f),
                amplitude = { if (playingState.playing && !localState.use && !reduceMotion) 1.0f else 0.0f },
                color = Color.White, progress = { state.value / state.trackRange.endInclusive })
        }
    )
}
