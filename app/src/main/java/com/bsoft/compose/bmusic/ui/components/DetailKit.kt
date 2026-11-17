package com.bsoft.compose.bmusic.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import com.bsoft.compose.bmusic.data.models.Song

/*
 * The album, artist and playlist screens: blurred artwork behind everything, a large header
 * with the artwork, title and Play / Shuffle, and a slim top bar whose title appears once the
 * header has scrolled away.
 */

/** Height of [DetailTopBar] below the status bar. */
val DetailTopBarHeight: Dp = 64.dp

/** The screen background: the artwork, blurred, under a dark gradient for legible white text. */
@Composable
fun DetailBackdrop(bitmap: Bitmap?, @DrawableRes fallback: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF101114))) {
        val imageModifier = Modifier.fillMaxSize().blur(radius = 28.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
        if (bitmap != null) {
            BitmapImage(bitmap = bitmap, modifier = imageModifier, contentScale = ContentScale.Crop)
        } else {
            Image(modifier = imageModifier, painter = painterResource(fallback), contentScale = ContentScale.Crop, contentDescription = null)
        }
        // Blur needs Android 12+; the gradient keeps text readable everywhere.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.45f to Color.Black.copy(alpha = 0.62f),
                    1f to Color.Black.copy(alpha = 0.88f)
                )
            )
        )
    }
}

/**
 * Back button, the [title] (shown once [collapsed]) and [actions]. Transparent over the header,
 * a dark bar once the list scrolls under it.
 */
@Composable
fun DetailTopBar(
    title: String,
    collapsed: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background by animateColorAsState(if (collapsed) Color(0xE6151619) else Color.Transparent, label = "bar")
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "title")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(DetailTopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ms_arrow_back),
                tint = Color.White,
                contentDescription = stringResource(R.string.back)
            )
        }
        Text(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).alpha(titleAlpha),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

/** A white action icon for [DetailTopBar]. */
@Composable
fun DetailBarAction(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = ImageVector.vectorResource(icon), tint = Color.White, contentDescription = contentDescription)
    }
}

/** Space a list needs at the top so its first item starts below [DetailTopBar]. */
@Composable
fun detailTopInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailTopBarHeight

/**
 * The large header: [artwork], [title], [subtitle], [meta] ("12 songs · 48 min") and the Play
 * (Pause while this list plays) and Shuffle buttons.
 */
@Composable
fun DetailHeader(
    artwork: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    meta: String?,
    playing: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = detailTopInset(), bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        artwork()
        Spacer(Modifier.height(20.dp))
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!meta.isNullOrBlank()) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                enabled = enabled,
                onClick = onPlay,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111))
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = ImageVector.vectorResource(if (playing) R.drawable.ms_pause_fill1 else R.drawable.ms_play_arrow_fill1),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(if (playing) R.string.detail_pause else R.string.play),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                enabled = enabled,
                onClick = onShuffle,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White)
            ) {
                Icon(modifier = Modifier.size(20.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_shuffle), contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(R.string.shuffle),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Large artwork with a soft shadow for [DetailHeader]; round for artists. */
@Composable
fun DetailArtwork(bitmap: Bitmap?, @DrawableRes placeholder: Int, round: Boolean = false, size: Dp = 208.dp) {
    val shape = if (round) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    androidx.compose.material3.Surface(
        modifier = Modifier.size(size),
        shape = shape,
        color = Color.White.copy(alpha = 0.10f),
        shadowElevation = 16.dp
    ) {
        if (bitmap != null) {
            BitmapImage(bitmap = bitmap, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(size * 0.36f),
                    imageVector = ImageVector.vectorResource(placeholder),
                    tint = Color.White.copy(alpha = 0.7f),
                    contentDescription = null
                )
            }
        }
    }
}

/**
 * Playlist cover: a 2x2 collage of the first four different albums, the single album's art when
 * there are fewer, or the playlist background with a queue icon when it is empty.
 */
@Composable
fun PlaylistArtwork(songs: List<Song>, @DrawableRes bg: Int, size: Dp = 200.dp) {
    val covers = remember(songs) {
        songs.asSequence()
            .distinctBy { if (it.albumId >= 0) it.albumId else -it.id - 1 }
            .map { it.mediaUri }
            .take(4)
            .toList()
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val decodePx = with(LocalDensity.current) { size.roundToPx() }
    androidx.compose.material3.Surface(
        modifier = Modifier.size(size),
        shape = shape,
        color = Color.White.copy(alpha = 0.10f),
        shadowElevation = 16.dp
    ) {
        when {
            covers.size >= 4 -> Column {
                for (row in 0 until 2) {
                    Row {
                        for (col in 0 until 2) {
                            ArtworkThumb(
                                uri = covers[row * 2 + col],
                                size = size / 2,
                                shape = androidx.compose.ui.graphics.RectangleShape,
                                decodePx = decodePx / 2,
                                placeholderColor = Color.White.copy(alpha = 0.08f),
                                placeholderTint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            covers.isNotEmpty() -> ArtworkThumb(
                uri = covers.first(),
                size = size,
                shape = androidx.compose.ui.graphics.RectangleShape,
                decodePx = decodePx,
                placeholderColor = Color.White.copy(alpha = 0.08f),
                placeholderTint = Color.White.copy(alpha = 0.7f)
            )
            else -> Box(contentAlignment = Alignment.Center) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(bg),
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Icon(
                    modifier = Modifier.size(size * 0.36f),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_queue_music),
                    tint = Color.White,
                    contentDescription = null
                )
            }
        }
    }
}

/** A section title on the dark detail screens ("Albums", "Songs"). */
@Composable
fun DetailSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )
}

/** "2 h 05 min" or "48 min" for the total length of a list. */
@Composable
fun totalDurationText(totalMs: Long): String {
    val minutes = totalMs / 60_000
    return if (minutes < 60) stringResource(R.string.stats_minutes, minutes.toInt())
    else stringResource(R.string.stats_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
}

/** Whether the large header has scrolled far enough to show the title in the top bar. */
fun headerScrolledAway(firstVisibleIndex: Int, firstVisibleOffset: Int, thresholdPx: Int): Boolean =
    firstVisibleIndex > 0 || firstVisibleOffset > thresholdPx

/** Pixels the header scrolls before the bar shows the title (about the artwork's height). */
@Composable
fun headerThresholdPx(): Int = with(LocalDensity.current) { 260.dp.roundToPx() }
