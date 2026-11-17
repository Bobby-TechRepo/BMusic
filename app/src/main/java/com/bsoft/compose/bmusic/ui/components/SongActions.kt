package com.bsoft.compose.bmusic.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.models.Song

/**
 * Asks the app to show the song action sheet. [onRemove] adds a "Remove" action for lists the
 * song can be removed from (a custom playlist).
 */
data class SongMenuRequest(
    val song: Song,
    val onRemove: (() -> Unit)? = null,
)

/** Everything the sheet can do. The host (Main) implements these with its ViewModels. */
data class SongActionHandlers(
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val toggleFavourite: (Song) -> Unit,
    val addToPlaylist: (playlistId: Long, Song) -> Unit,
    val createPlaylistWith: (name: String, Song) -> Unit,
    val goToAlbum: ((Song) -> Unit)?,
    val goToArtist: ((Song) -> Unit)?,
    /** Opens the tag editor; null where the file can't be edited (Android 10 and older, Opus...). */
    val editTags: ((Song) -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    request: SongMenuRequest,
    isFavourite: Boolean,
    playlists: List<PlaylistEntity>,
    handlers: SongActionHandlers,
    onDismiss: () -> Unit,
) {
    val song = request.song
    val context = LocalContext.current
    var pickingPlaylist by remember(song.id) { mutableStateOf(false) }
    var creatingPlaylist by remember(song.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetContent {
            SheetHeader(
                title = song.title,
                subtitle = stringResource(R.string.song_row_subtitle, song.artist, song.album)
            ) {
                ArtworkThumb(uri = song.mediaUri, size = 56.dp, shape = RoundedCornerShape(14.dp), decodePx = 192)
            }

            if (pickingPlaylist) {
                SheetAction(
                    icon = R.drawable.ms_arrow_back,
                    label = stringResource(R.string.add_to_playlist),
                    highlighted = true
                ) { pickingPlaylist = false }
                SheetAction(icon = R.drawable.ms_add, label = stringResource(R.string.new_playlist)) {
                    creatingPlaylist = true
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        SheetAction(icon = R.drawable.ms_queue_music, label = playlist.name) {
                            handlers.addToPlaylist(playlist.id, song)
                            onDismiss()
                        }
                    }
                }
            } else {
                SheetQuickActions {
                    SheetQuickAction(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.ms_queue_play_next,
                        label = stringResource(R.string.play_next)
                    ) { handlers.playNext(song); onDismiss() }
                    SheetQuickAction(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.ms_playlist_play,
                        label = stringResource(R.string.add_to_queue)
                    ) { handlers.addToQueue(song); onDismiss() }
                    SheetQuickAction(
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = context.getString(
                                if (isFavourite) R.string.remove_from_favourites else R.string.add_to_favourites
                            )
                        },
                        icon = if (isFavourite) R.drawable.ms_favorite_fill1 else R.drawable.ms_favorite,
                        label = stringResource(R.string.favourite),
                        active = isFavourite
                    ) { handlers.toggleFavourite(song); onDismiss() }
                }
                SheetAction(icon = R.drawable.ms_playlist_add, label = stringResource(R.string.add_to_playlist), chevron = true) {
                    pickingPlaylist = true
                }
                handlers.goToAlbum?.let { goToAlbum ->
                    SheetAction(icon = R.drawable.ms_album, label = stringResource(R.string.go_to_album)) {
                        goToAlbum(song); onDismiss()
                    }
                }
                handlers.goToArtist?.let { goToArtist ->
                    SheetAction(icon = R.drawable.ms_person, label = stringResource(R.string.go_to_artist)) {
                        goToArtist(song); onDismiss()
                    }
                }
                handlers.editTags?.let { editTags ->
                    SheetAction(icon = R.drawable.ms_edit_note, label = stringResource(R.string.edit_tags)) {
                        editTags(song); onDismiss()
                    }
                }
                SheetAction(icon = R.drawable.ms_share, label = stringResource(R.string.share)) {
                    shareSong(context, song); onDismiss()
                }
                request.onRemove?.let { remove ->
                    SheetAction(
                        icon = R.drawable.ms_remove_circle,
                        label = stringResource(R.string.remove_from_playlist),
                        destructive = true
                    ) { remove(); onDismiss() }
                }
            }
        }
    }

    if (creatingPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    handlers.createPlaylistWith(name.trim(), song)
                    creatingPlaylist = false
                    onDismiss()
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { creatingPlaylist = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Shares the audio file itself (with a read grant), falling back to the title as text. */
fun shareSong(context: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, song.mediaUri)
        putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, song.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
