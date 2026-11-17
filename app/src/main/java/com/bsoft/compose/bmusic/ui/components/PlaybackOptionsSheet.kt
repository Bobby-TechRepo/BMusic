package com.bsoft.compose.bmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.playback.SleepTimerState
import com.bsoft.compose.bmusic.utils.toTimeFormat
import kotlinx.coroutines.delay

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Sleep timer and playback speed, opened from the player screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackOptionsSheet(
    sleepState: SleepTimerState,
    remainingMs: () -> Long?,
    speed: Float,
    onStartSleep: (minutes: Int) -> Unit,
    onSleepAfterTrack: () -> Unit,
    onExtendSleep: (minutes: Int) -> Unit,
    onCancelSleep: () -> Unit,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // Ticks once a second while a countdown runs.
    val remaining by produceState(initialValue = remainingMs(), sleepState) {
        while (true) {
            value = remainingMs()
            delay(1_000)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetContent {
            OptionsSection(
                icon = R.drawable.ms_bedtime,
                title = stringResource(R.string.sleep_timer),
                status = when (sleepState) {
                    is SleepTimerState.Counting -> stringResource(R.string.sleep_timer_stops_in, (remaining ?: 0L).toTimeFormat())
                    SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_after_song_active)
                    SleepTimerState.Off -> null
                }
            ) {
                when (sleepState) {
                    is SleepTimerState.Counting -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { onExtendSleep(10) }) { Text(stringResource(R.string.sleep_timer_add_10)) }
                            TextButton(onClick = onCancelSleep) { Text(stringResource(R.string.sleep_timer_off)) }
                        }
                    }
                    SleepTimerState.EndOfTrack -> {
                        TextButton(onClick = onCancelSleep) { Text(stringResource(R.string.sleep_timer_off)) }
                    }
                    SleepTimerState.Off -> {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SLEEP_MINUTES.forEach { minutes ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onStartSleep(minutes) },
                                    shape = RoundedCornerShape(50),
                                    label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) }
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = onSleepAfterTrack,
                                shape = RoundedCornerShape(50),
                                label = { Text(stringResource(R.string.sleep_timer_end_of_song)) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            OptionsSection(
                icon = R.drawable.ms_speed,
                title = stringResource(R.string.playback_speed),
                status = speedLabel(speed)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SPEEDS.forEach { option ->
                        val selected = kotlin.math.abs(option - speed) < 0.01f
                        FilterChip(
                            selected = selected,
                            onClick = { onSpeed(option) },
                            shape = RoundedCornerShape(50),
                            leadingIcon = if (selected) ({
                                Icon(
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    imageVector = ImageVector.vectorResource(R.drawable.ms_check),
                                    contentDescription = null
                                )
                            }) else null,
                            label = { Text(speedLabel(option)) }
                        )
                    }
                }
            }
        }
    }
}

/** A titled section with an icon tile and a one-line status, then its controls. */
@Composable
private fun OptionsSection(
    @DrawableRes icon: Int,
    title: String,
    status: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetIconTile(icon, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        content()
    }
}
