package com.bsoft.compose.bmusic.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.utils.Util
import java.util.Locale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn

/*
 * Small building blocks shared by the redesigned search, settings and stats screens, so they
 * look like one app: the same artwork thumbnails, section headers, avatars and empty states.
 */

/** Album or song artwork from MediaStore, with a tinted music-note placeholder. */
@Composable
fun ArtworkThumb(
    uri: Uri?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    @DrawableRes placeholder: Int = R.drawable.ms_music_note,
    placeholderColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    placeholderTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    /** Decode size in pixels; raise it for large artwork. */
    decodePx: Int = 160,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = uri?.let { Util.loadArtwork(context, it, Size(decodePx, decodePx)) }
    }
    Surface(modifier = modifier.size(size), shape = shape, color = placeholderColor) {
        val image = bitmap
        if (image != null) {
            BitmapImage(bitmap = image, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(size * 0.5f),
                    imageVector = ImageVector.vectorResource(placeholder),
                    tint = placeholderTint,
                    contentDescription = null
                )
            }
        }
    }
}

/** A round avatar with the first letter of [name]; the colour stays the same for a name. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (background, foreground) = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "?",
            color = foreground,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** A section title with an optional action on the right ("See all", "Clear"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f).semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Centred icon, title and message, for "nothing here yet" screens. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(40.dp),
                imageVector = ImageVector.vectorResource(icon),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                contentDescription = null
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The rounded search field shown on Home. It only opens the search screen, where the real
 * field (same shape) gets the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.search_library_hint)
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = ImageVector.vectorResource(R.drawable.ms_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 14.dp),
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * [text] with the part matching [query] in bold [color]. Case-insensitive; if the match only
 * exists after removing accents, the text is returned plain.
 */
fun highlightMatch(text: String, query: String, color: Color): AnnotatedString {
    val needle = query.trim()
    val start = if (needle.isEmpty()) -1 else text.indexOf(needle, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(start, start + needle.length)) }
        append(text.substring(start + needle.length))
    }
}

/** A tappable row with a leading visual, used by search results. */
@Composable
fun MediaRow(
    title: AnnotatedString,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}
