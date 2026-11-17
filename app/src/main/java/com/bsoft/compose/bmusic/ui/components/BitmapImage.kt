package com.bsoft.compose.bmusic.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun BitmapImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** Artwork is decorative next to its title, so screen readers skip it by default. */
    contentDescription: String? = null,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentScale = contentScale,
        contentDescription = contentDescription,
        modifier = modifier
    )
}