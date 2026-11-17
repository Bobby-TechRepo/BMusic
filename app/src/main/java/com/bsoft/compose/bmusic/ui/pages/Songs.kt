package com.bsoft.compose.bmusic.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.models.SongSort
import com.bsoft.compose.bmusic.data.states.QueueState
import com.bsoft.compose.bmusic.ui.components.AlphabeticList
import com.bsoft.compose.bmusic.ui.components.SongView
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import com.bsoft.compose.bmusic.ui.components.sectionLetter
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import com.bsoft.compose.bmusic.ui.components.TestTags
import androidx.compose.foundation.layout.heightIn

/** Label shown for each sort order. */
private fun SongSort.labelRes(): Int = when (this) {
    SongSort.TITLE -> R.string.sort_title
    SongSort.ARTIST -> R.string.sort_artist
    SongSort.ALBUM -> R.string.sort_album
    SongSort.DATE_ADDED -> R.string.sort_date_added
    SongSort.DURATION -> R.string.sort_duration
}

/**
 * The Songs tab. [songs] are already in [sort] order; tapping a song plays this exact order.
 */
@Composable
fun SongsPage(
    modifier: Modifier = Modifier,
    songs: List<Song> = emptyList(),
    sort: SongSort = SongSort.TITLE,
    onSortChange: (SongSort) -> Unit = {},
    queueState: QueueState,
    /** Whether playback is running, to animate the now-playing bars. */
    playing: Boolean = false,
    onMenu: (Song) -> Unit = {},
    /** Plays every song in random order. */
    onShuffleAll: () -> Unit = {},
    chosen: (index: Int, song: Song) -> Unit
) {
    if (songs.isEmpty()) {
        StatusScreen(
            modifier = modifier,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body)
        )
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = pluralStringResource(R.plurals.song_count, songs.size, songs.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var expanded by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text(stringResource(sort.labelRes())) },
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                            imageVector = ImageVector.vectorResource(R.drawable.ms_sort),
                            contentDescription = stringResource(R.string.sort_by, stringResource(sort.labelRes()))
                        )
                    },
                    shape = RoundedCornerShape(50)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    SongSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            onClick = { onSortChange(option); expanded = false },
                            trailingIcon = if (option == sort) ({
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ms_check),
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = null
                                )
                            }) else null
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = onShuffleAll,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_shuffle),
                    contentDescription = null
                )
                Text(modifier = Modifier.padding(start = 6.dp), text = stringResource(R.string.shuffle))
            }
        }
        AlphabeticList(
            modifier = Modifier.weight(1f).testTag(TestTags.SONGS_LIST),
            items = songs,
            header = { sectionLetter(sort.indexText(it)) },
            key = { it.id },
            showIndex = sort.alphabetical
        ) { index, item ->
            SongView(
                song = item,
                currentPlaying = queueState.current?.id == item.id,
                playing = playing,
                onMenu = { onMenu(item) }
            ) {
                chosen(index, item)
            }
        }
    }
}

@Preview
@Composable
fun SongsPagePreview(){
    BMusicTheme {
        Surface {
            Text("Songs Page Preview")
        }
    }
}
