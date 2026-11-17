package com.bsoft.compose.bmusic.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.tags.TagField
import com.bsoft.compose.bmusic.data.tags.TagProblem
import com.bsoft.compose.bmusic.viewmodels.TagEditorState
import com.bsoft.compose.bmusic.viewmodels.TagEditorViewModel

/**
 * Edits the tags stored inside a song's file: title, artist, album, album artist, genre, year,
 * track, disc, lyrics and the cover picture. Saving asks Android for permission to change the
 * file (a system dialog), then writes the file and refreshes the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    songId: Long,
    viewModel: TagEditorViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    back: () -> Unit,
) {
    LaunchedEffect(songId) { viewModel.load(songId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }

    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.save()
        else viewModel.onWriteDenied(context.getString(R.string.tag_editor_permission_denied))
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setArtwork)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            onSaved()
            back()
        }
    }

    fun leave() { if (state.dirty && !state.saving) confirmDiscard = true else back() }
    BackHandler(enabled = state.dirty && !state.saving) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tag_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ms_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Button(
                            modifier = Modifier.padding(end = 8.dp),
                            enabled = state.canSave,
                            onClick = { viewModel.writeRequest()?.let { writeLauncher.launch(IntentSenderRequest.Builder(it).build()) } }
                        ) { Text(stringResource(R.string.tag_editor_save)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
                state.unsupported -> Message(stringResource(R.string.tag_editor_unsupported))
                state.readFailed -> Message(stringResource(R.string.tag_editor_read_failed))
                else -> EditorForm(
                    state = state,
                    onChange = viewModel::update,
                    onPickPicture = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemovePicture = viewModel::removeArtwork
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.tag_editor_discard_title)) },
            text = { Text(stringResource(R.string.tag_editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; back() }) { Text(stringResource(R.string.tag_editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.tag_editor_keep_editing)) }
            }
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        modifier = Modifier.padding(32.dp),
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EditorForm(
    state: TagEditorState,
    onChange: (TagField, String) -> Unit,
    onPickPicture: () -> Unit,
    onRemovePicture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArtworkEditor(artwork = state.artwork, onPick = onPickPicture, onRemove = onRemovePicture)

        state.saveError?.let { error ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    text = stringResource(R.string.tag_editor_save_failed, error),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        TagTextField(state, TagField.TITLE, R.string.tag_title, onChange)
        TagTextField(state, TagField.ARTIST, R.string.tag_artist, onChange)
        TagTextField(state, TagField.ALBUM, R.string.tag_album, onChange)
        TagTextField(state, TagField.ALBUM_ARTIST, R.string.tag_album_artist, onChange)
        TagTextField(state, TagField.GENRE, R.string.tag_genre, onChange)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TagTextField(state, TagField.YEAR, R.string.tag_year, onChange, Modifier.weight(1.3f), number = true)
            TagTextField(state, TagField.TRACK, R.string.tag_track, onChange, Modifier.weight(1f), number = true)
            TagTextField(state, TagField.DISC, R.string.tag_disc, onChange, Modifier.weight(1f), number = true)
        }
        TagTextField(state, TagField.LYRICS, R.string.tag_lyrics, onChange, singleLine = false)

        val song = state.song
        if (song != null) {
            Text(
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                text = listOf(state.format, song.path).filter { it.isNotBlank() }.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtworkEditor(artwork: ByteArray?, onPick: () -> Unit, onRemove: () -> Unit) {
    val image = remember(artwork) {
        artwork?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(128.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 2.dp
        ) {
            if (image != null) {
                Image(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                    bitmap = image,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        imageVector = ImageVector.vectorResource(R.drawable.ms_album),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentDescription = null
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPick) {
                Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_image), contentDescription = null)
                Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.tag_change_picture))
            }
            if (artwork != null) {
                OutlinedButton(onClick = onRemove) {
                    Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_hide_image), contentDescription = null)
                    Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.tag_remove_picture))
                }
            }
        }
    }
}

@Composable
private fun TagTextField(
    state: TagEditorState,
    field: TagField,
    label: Int,
    onChange: (TagField, String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    singleLine: Boolean = true,
) {
    val problem = state.problems[field]
    OutlinedTextField(
        modifier = modifier,
        value = state.values[field],
        onValueChange = { onChange(field, it) },
        label = { Text(stringResource(label), maxLines = 1) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        enabled = !state.saving,
        isError = problem != null,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        supportingText = if (problem != null) ({
            Text(
                stringResource(
                    when (problem) {
                        TagProblem.NOT_A_YEAR -> R.string.tag_error_year
                        TagProblem.NOT_A_NUMBER -> R.string.tag_error_number
                        TagProblem.TITLE_REQUIRED -> R.string.tag_error_title
                    }
                )
            )
        }) else null
    )
}
