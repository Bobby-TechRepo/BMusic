package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn

/** The "new playlist" sheet: a name field and a Create button. */
@Composable
fun PlaylistForm(modifier: Modifier = Modifier, error: String? = null, add: (String) -> Unit) {
    val textFieldState = rememberTextFieldState()
    val coroutine = rememberCoroutineScope()

    val buttonEnabled by remember {
        derivedStateOf { textFieldState.text.isNotBlank() }
    }

    SheetContent(modifier = modifier) {
        SheetHeader(title = stringResource(R.string.add_custom_playlist)) {
            SheetIconTile(R.drawable.ms_playlist_add)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = textFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.playlist_name)) },
                placeholder = { Text(stringResource(R.string.specify_playlist_name)) },
                isError = error != null
            )
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                        imageVector = ImageVector.vectorResource(R.drawable.marketeq__caution),
                        contentDescription = null
                    )
                    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = buttonEnabled,
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    val title = textFieldState.text.toString().trim()
                    coroutine.launch {
                        try {
                            add(title)
                            textFieldState.clearText()
                        } catch (error: Exception) {
                            // The caller reports failures through [error].
                        }
                    }
                }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_add),
                    contentDescription = null
                )
                Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.btn_create))
            }
        }
    }
}

@Preview
@Composable
private fun PlaylistFormPreview() {
    BMusicTheme {
        Surface {
            PlaylistForm(error = "This is an error, try again") {}
        }
    }
}
