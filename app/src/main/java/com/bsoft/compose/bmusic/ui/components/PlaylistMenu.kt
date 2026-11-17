package com.bsoft.compose.bmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/** One action in a sheet; kept for callers outside the sheets, now drawn as a [SheetAction]. */
@Composable
fun PlaylistMenuItem(modifier: Modifier = Modifier, title: String, @DrawableRes icon: Int, clicked: () -> Unit) {
    SheetAction(modifier = modifier, icon = icon, label = title, onClick = clicked)
}

/** The playlist options sheet: Play / Shuffle / Add songs tiles, then the other actions. */
@Composable
fun PlaylistMenu(
    modifier: Modifier = Modifier,
    playlist: Long,
    playlistName: String,
    /** Shown under the name, e.g. "12 songs · 48 min". */
    subtitle: String? = null,
    open: ((Long) -> Unit)? = null,
    edit: ((Long) -> Unit)? = null,
    play: (Long) -> Unit = {},
    shuffle: (Long) -> Unit = {},
    rename: (Long) -> Unit = {},
    addSongs: (Long) -> Unit = {},
    export: ((Long) -> Unit)? = null,
    delete: (Long) -> Unit = {}
) {
    SheetContent(modifier = modifier) {
        SheetHeader(title = playlistName, subtitle = subtitle) {
            SheetIconTile(R.drawable.ms_queue_music)
        }
        SheetQuickActions {
            SheetQuickAction(modifier = Modifier.weight(1f), icon = R.drawable.ms_play_arrow_fill1, label = stringResource(R.string.play)) {
                play(playlist)
            }
            SheetQuickAction(modifier = Modifier.weight(1f), icon = R.drawable.ms_shuffle, label = stringResource(R.string.shuffle)) {
                shuffle(playlist)
            }
            SheetQuickAction(modifier = Modifier.weight(1f), icon = R.drawable.ms_playlist_add, label = stringResource(R.string.add_songs)) {
                addSongs(playlist)
            }
        }
        Column {
            open?.let {
                SheetAction(icon = R.drawable.ms_open_in_new, label = stringResource(R.string.open)) { open(playlist) }
            }
            SheetAction(icon = R.drawable.ms_drive_file_rename_outline, label = stringResource(R.string.rename)) { rename(playlist) }
            edit?.let {
                SheetAction(icon = R.drawable.ms_edit, label = stringResource(R.string.edit)) { edit(playlist) }
            }
            export?.let {
                SheetAction(icon = R.drawable.ic_export, label = stringResource(R.string.export_m3u)) { export(playlist) }
            }
            SheetAction(icon = R.drawable.ms_delete, label = stringResource(R.string.delete), destructive = true) { delete(playlist) }
        }
    }
}

@Preview
@Composable
private fun PlaylistMenuPreview() {
    BMusicTheme {
        Surface {
            PlaylistMenu(playlist = 0, playlistName = "Placeholder Playlist", subtitle = "12 songs")
        }
    }
}
