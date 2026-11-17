package com.bsoft.compose.bmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/**
 * A centred message with up to two actions, for permission, error and empty states.
 * Fills its parent and draws an opaque background so it can cover other content.
 */
@Composable
fun StatusScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.solar__music_notes_bold_duotone,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                modifier = Modifier.size(72.dp),
                imageVector = ImageVector.vectorResource(icon),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (primaryLabel != null) {
                Spacer(Modifier.height(32.dp))
                Button(onClick = onPrimary) { Text(primaryLabel) }
            }
            if (secondaryLabel != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Preview
@Composable
private fun StatusScreenPreview() {
    BMusicTheme {
        StatusScreen(
            title = "Allow access to your music",
            body = "BMusic needs permission to read the audio files on this device.",
            primaryLabel = "Allow access",
            secondaryLabel = "Open settings"
        )
    }
}
