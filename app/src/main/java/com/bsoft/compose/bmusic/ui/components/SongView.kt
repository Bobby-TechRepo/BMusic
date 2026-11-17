package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/** A song on a regular (theme-coloured) screen: the Songs tab and Add songs. See [SongRow]. */
@Composable
fun SongView(
    modifier: Modifier = Modifier, currentPlaying: Boolean = false, selectable: Boolean = false, selected: Boolean = false, song: Song,
    /** Shows a menu button that opens the song menu. */
    onMenu: (() -> Unit)? = null,
    /** Animate the now-playing bars (false while paused). */
    playing: Boolean = currentPlaying,
    clicked: () -> Unit
) {
    SongRow(
        song = song,
        modifier = modifier,
        current = currentPlaying,
        playing = playing,
        selectionMode = selectable,
        selected = selected,
        onMenu = onMenu,
        onClick = clicked
    )
}

@Preview
@Composable
private fun SongViewPreview() {
    BMusicTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SongView(currentPlaying = true, song = Song(id = 0, displayName = "Display Name", title = "Song Title", artist = "Artist name", album = "Album Name", duration = 185_000), onMenu = {}) {}
                SongView(selectable = true, selected = true, song = Song(id = 1, displayName = "Display Name", title = "Another Song", artist = "Artist name", album = "Album Name", duration = 241_000)) {}
            }
        }
    }
}
