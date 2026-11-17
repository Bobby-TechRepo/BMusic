package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/**
 * A song on the dark, artwork-backed screens: the play queue, albums, artists and playlists.
 * Same row as everywhere else ([SongRow]), in white-on-dark colours.
 */
@Composable
fun QueueSongView(
    modifier: Modifier = Modifier, playCount: Int? = null, selectionMode: Boolean = false, movable: Boolean = false, moving: Boolean = false,
    checked: Boolean = false, showMenu: Boolean = false, currentPlaying: Boolean = false, menuClicked: (Song) -> Unit = {}, song: Song,
    /** Long-press handler, e.g. to start multi-select. */
    onLongClick: (() -> Unit)? = null,
    /** Shows a remove button (used by the play queue). */
    onRemove: (() -> Unit)? = null,
    /** Album lists show the track position instead of artwork. */
    trackNumber: Int? = null,
    /** Songs already played in the queue are dimmed. */
    dimmed: Boolean = false,
    /** Animate the now-playing bars (false while paused). */
    playing: Boolean = currentPlaying,
    /** Gesture for the drag handle shown when [movable], e.g. `Modifier.detectReorder(state)`. */
    dragHandleModifier: Modifier = Modifier,
    clicked: () -> Unit
) {
    SongRow(
        song = song,
        modifier = modifier,
        colors = SongRowDefaults.onDark(),
        current = currentPlaying,
        playing = playing,
        trackNumber = trackNumber,
        playCount = playCount,
        selectionMode = selectionMode,
        selected = checked,
        dragging = moving,
        dimmed = dimmed,
        showDragHandle = movable,
        dragHandleModifier = dragHandleModifier,
        onMenu = if (showMenu && !selectionMode) ({ menuClicked(song) }) else null,
        onRemove = onRemove,
        onLongClick = onLongClick,
        onClick = clicked
    )
}

@Preview
@Composable
private fun QueueSongViewPreview() {
    BMusicTheme {
        Column(modifier = Modifier.background(Color(0xFF15161A))) {
            QueueSongView(currentPlaying = true, showMenu = true, song = Song(id = 0, displayName = "Display Name", title = "Song Title", artist = "Artist name", album = "Album Name", duration = 185_000)) {}
            QueueSongView(trackNumber = 2, showMenu = true, song = Song(id = 1, displayName = "Display Name", title = "Second Song", artist = "Artist name", album = "Album Name", duration = 201_000)) {}
        }
    }
}
