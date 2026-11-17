package com.bsoft.compose.bmusic.ui.components.alerts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bsoft.compose.bmusic.R

@Composable
fun PlaylistDeleteAlertDialog(playlist: Long, playlistName: String, onDismissRequest: () -> Unit, onConfirmation: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(R.string.delete_confirmation_title)) },
        text = { Text(text = stringResource(R.string.delete_playlist_message, playlistName)) },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(onClick = { onConfirmation() }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}