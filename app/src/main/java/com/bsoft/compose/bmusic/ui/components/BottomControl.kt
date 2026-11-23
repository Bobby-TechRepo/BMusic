package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.states.PlayingState
import com.bsoft.compose.bmusic.data.states.QueueState

/**
 * The mini player at the bottom of Home: a floating card with the artwork, title and artist,
 * play/pause and next, and a thin progress line along its bottom edge. Tapping it opens the
 * player. Hidden until something is in the queue.
 */
@Composable
fun BottomControl(
    modifier: Modifier = Modifier,
    state: PlayingState,
    queueState: QueueState,
    clicked: () -> Unit,
    onNext: (() -> Unit)? = null,
    playToggle: () -> Unit,
) {
    val song = queueState.current ?: return
    val scheme = MaterialTheme.colorScheme
    val artist = song.artist.takeUnless { it.isBlank() || it == "<unknown>" } ?: stringResource(R.string.unknown_artist)
    val progress = song.duration.takeIf { it > 0 }?.let { (state.position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .testTag(TestTags.MINI_PLAYER),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.clickable(onClickLabel = stringResource(R.string.cd_open_player)) { clicked() }) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArtworkThumb(uri = song.mediaUri, size = 48.dp, shape = RoundedCornerShape(14.dp), decodePx = 144)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = song.title.ifBlank { stringResource(R.string.unknown) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledIconButton(
                    modifier = Modifier.size(44.dp),
                    onClick = playToggle,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(if (state.playing) R.drawable.ms_pause_fill1 else R.drawable.ms_play_arrow_fill1),
                        contentDescription = stringResource(if (state.playing) R.string.cd_pause else R.string.cd_play)
                    )
                }
                if (onNext != null) {
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ms_skip_next_fill1),
                            tint = scheme.onSurface,
                            contentDescription = stringResource(R.string.cd_next)
                        )
                    }
                }
            }
            // Progress along the bottom edge, inset to follow the rounded corners.
            Box(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
            }
        }
    }
}
