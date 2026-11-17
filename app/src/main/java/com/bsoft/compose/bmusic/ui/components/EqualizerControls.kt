package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bsoft.compose.bmusic.data.models.EqualizerBand
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

private fun Int.formatFrequency(): String {
    return when {
        this >= 1_000_000 -> "${(this.toFloat() / 1_000_000f).toInt()} GHz"
        this >= 1_000 -> "${(this.toFloat() / 1_000f).toInt()} kHz"
        else -> "$this Hz"
    }
}

@Composable
fun EqualizerControls(modifier: Modifier = Modifier, bands: List<EqualizerBand> = emptyList(), seek: (Short, Short)-> Unit){
    Column(modifier = modifier.fillMaxWidth().height(320.dp).graphicsLayer { rotationZ = 270f }, // Rotate to make sliders vertical
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        bands.forEach { band->
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = band.frequency.formatFrequency(),
                    modifier = Modifier.rotate(90f),
                    fontSize = 10.sp,
                    color = Color.White
                )
                // The state follows the band's level, except while the user is dragging it.
                val range = band.min.toFloat()..band.max.toFloat()
                val sliderState = remember(band.band, range) { SliderState(value = band.level.toFloat(), trackRange = range) }
                LaunchedEffect(sliderState, band.level) {
                    if (!sliderState.isDragging) sliderState.value = band.level.toFloat()
                }
                Slider(
                    state = sliderState,
                    modifier = Modifier.width(200.dp),
                    onValueChange = {
                        sliderState.value = it
                        seek(band.band, it.toInt().toShort())
                    },
                    track = { trackState ->
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            progress = {
                                (trackState.value - trackState.trackRange.start) / (trackState.trackRange.endInclusive - trackState.trackRange.start)
                            })
                    },
                    thumb = {
                        Surface(modifier = Modifier.size(height = 20.dp, width = 30.dp),
                            shadowElevation =  3.dp,
                            shape = RoundedCornerShape(4.dp), color = Color.White) {
                            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                                VerticalDivider(thickness = 0.6.dp)
                                VerticalDivider(thickness = 0.6.dp)
                                VerticalDivider(thickness = 0.6.dp)
                                VerticalDivider(thickness = 0.6.dp)
                            }
                        }
                    },

                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Preview
@Composable
fun EqualizerControlsPreview(){
    BMusicTheme {
        Surface(color = Color.DarkGray) {
            EqualizerControls{
                band, level ->
            }
        }
    }
}