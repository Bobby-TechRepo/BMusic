package com.bsoft.compose.bmusic.ui.components

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.utils.Util
import com.bsoft.compose.bmusic.utils.toTimeFormat
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import com.bsoft.compose.bmusic.utils.rememberReduceMotion

/*
 * The library's list building blocks: one song row used everywhere (library, playlists, albums,
 * artists, queue, add-songs), album tiles and artist rows, so every list looks and behaves the
 * same.
 */

/** Colours of a [SongRow]; [SongRowDefaults.onDark] is for the artwork-backed screens. */
@Immutable
data class SongRowColors(
    val title: Color,
    val subtitle: Color,
    val accent: Color,
    val icon: Color,
    val selectedContainer: Color,
    val draggingContainer: Color,
    val placeholder: Color,
    val placeholderIcon: Color,
)

object SongRowDefaults {
    @Composable
    fun colors(): SongRowColors {
        val scheme = MaterialTheme.colorScheme
        return SongRowColors(
            title = scheme.onSurface,
            subtitle = scheme.onSurfaceVariant,
            accent = scheme.primary,
            icon = scheme.onSurfaceVariant,
            selectedContainer = scheme.secondaryContainer,
            draggingContainer = scheme.surfaceContainerHighest,
            placeholder = scheme.secondaryContainer,
            placeholderIcon = scheme.onSecondaryContainer,
        )
    }

    /** White text on the blurred-artwork backgrounds of the player, albums, artists and playlists. */
    @Composable
    fun onDark(): SongRowColors = SongRowColors(
        title = Color.White,
        subtitle = Color.White.copy(alpha = 0.68f),
        accent = MaterialTheme.colorScheme.inversePrimary,
        icon = Color.White.copy(alpha = 0.75f),
        selectedContainer = Color.White.copy(alpha = 0.14f),
        draggingContainer = Color(0xFF2A2C33),
        placeholder = Color.White.copy(alpha = 0.10f),
        placeholderIcon = Color.White.copy(alpha = 0.6f),
    )
}

/**
 * A song in a list: artwork (or track number), title, "artist · length", and the controls the
 * list needs. The playing song shows animated bars over its artwork and an accent title.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    modifier: Modifier = Modifier,
    colors: SongRowColors = SongRowDefaults.colors(),
    current: Boolean = false,
    /** Animate the bars of the [current] song (false while paused). */
    playing: Boolean = current,
    /** Album lists show the track position instead of artwork. */
    trackNumber: Int? = null,
    playCount: Int? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    dragging: Boolean = false,
    /** Already played (the queue dims songs before the current one). */
    dimmed: Boolean = false,
    showDragHandle: Boolean = false,
    /** Gesture for the drag handle, e.g. `Modifier.detectReorder(state)`: dragging starts there. */
    dragHandleModifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val isSelected = selected
    val nowPlayingLabel = stringResource(R.string.cd_now_playing)
    val menuLabel = stringResource(R.string.cd_song_options)
    val removeLabel = stringResource(R.string.cd_remove_from_queue)
    val background = when {
        dragging -> colors.draggingContainer
        selectionMode && selected -> colors.selectedContainer
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .then(if (dragging) Modifier.shadow(12.dp, shape) else Modifier)
            .testTag(TestTags.SONG_ROW)
            .clip(shape)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics {
                if (selectionMode) this.selected = isSelected
                // TalkBack: "Now playing", and the row's buttons as actions on the row itself.
                if (current) stateDescription = nowPlayingLabel
                customActions = buildList {
                    if (onMenu != null && !selectionMode) add(CustomAccessibilityAction(menuLabel) { onMenu(); true })
                    if (onRemove != null) add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                }
            }
            .heightIn(min = 64.dp)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .alpha(if (dimmed && !current) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (trackNumber != null) {
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (current) {
                    PlayingBars(color = colors.accent, animate = playing, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = trackNumber.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.subtitle
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                ArtworkThumb(
                    uri = song.mediaUri,
                    size = 52.dp,
                    shape = RoundedCornerShape(12.dp),
                    placeholderColor = colors.placeholder,
                    placeholderTint = colors.placeholderIcon
                )
                if (current) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingBars(color = Color.White, animate = playing, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = if (current) colors.accent else colors.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(stringResource(R.string.song_row_subtitle, song.artist, song.duration.toTimeFormat()))
                playCount?.let { append(" · ").append(pluralStringResource(R.plurals.played_times, it, it)) }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            selectionMode -> Icon(
                modifier = Modifier.padding(end = 8.dp).size(24.dp),
                imageVector = ImageVector.vectorResource(if (selected) R.drawable.ms_check_circle_fill1 else R.drawable.ms_radio_button_unchecked),
                tint = if (selected) colors.accent else colors.icon,
                contentDescription = null
            )
            onMenu != null -> IconButton(onClick = onMenu) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ms_more_vert),
                    tint = colors.icon,
                    contentDescription = stringResource(R.string.cd_song_options)
                )
            }
        }
        onRemove?.let { remove ->
            IconButton(onClick = remove) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_close),
                    tint = colors.icon,
                    contentDescription = stringResource(R.string.cd_remove_from_queue)
                )
            }
        }
        if (showDragHandle) {
            // Hold and drag the handle to move the song; the rest of the row scrolls the list.
            Box(
                modifier = Modifier.size(48.dp).then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ms_drag_indicator),
                    tint = colors.icon,
                    contentDescription = stringResource(R.string.cd_reorder)
                )
            }
        }
    }
}

/** Three bouncing bars: "this is the song that is playing". Still while paused or with animations off. */
@Composable
fun PlayingBars(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val moving = animate && !rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 520, delayMillis = delay), RepeatMode.Reverse),
            label = "bar$delay"
        )
    }
    val still = listOf(0.55f, 0.9f, 0.4f)
    Canvas(modifier = modifier) {
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * 2) / 3
        repeat(3) { i ->
            val fraction = if (moving) phases[i].value else still[i]
            val height = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

/** An album in a grid or row: square artwork, name, and "artist · N songs". */
@Composable
fun AlbumTile(
    album: Album,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    onClick: () -> Unit,
) {
    val titleColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (onDark) Color.White.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .testTag(TestTags.ALBUM_TILE)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickableCompat(onClick)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val context = LocalContext.current
        var bitmap by remember(album.id) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(album.id) { bitmap = Util.loadArtwork(context, album.artworkUri, Size(360, 360)) }
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            color = if (onDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.secondaryContainer
        ) {
            val image = bitmap
            if (image != null) {
                BitmapImage(bitmap = image, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.fillMaxSize(0.38f),
                        imageVector = ImageVector.vectorResource(R.drawable.ms_album),
                        tint = if (onDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSecondaryContainer,
                        contentDescription = null
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.song_row_subtitle,
                    album.artist,
                    pluralStringResource(R.plurals.search_songs_count, album.songCount, album.songCount)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The artist's picture (artwork of one of their albums) or, without one, their initial. */
@Composable
fun ArtistAvatar(artist: Artist, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(artist.id) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(artist.id) { mutableStateOf(false) }
    LaunchedEffect(artist.id) {
        bitmap = Util.loadArtwork(context, artist.artworkUri, Size(240, 240))
        loaded = true
    }
    val image = bitmap
    if (image != null) {
        BitmapImage(
            bitmap = image,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        InitialAvatar(artist.name, modifier = modifier.alpha(if (loaded) 1f else 0.6f), size = size)
    }
}

/** An artist in a list: avatar, name, "N albums · M songs" and a chevron. */
@Composable
fun ArtistRow(artist: Artist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .testTag(TestTags.ARTIST_ROW)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickableCompat(onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ArtistAvatar(artist, size = 56.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.song_row_subtitle,
                    pluralStringResource(R.plurals.search_albums_count, artist.albumCount, artist.albumCount),
                    pluralStringResource(R.plurals.search_songs_count, artist.songCount, artist.songCount)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ms_chevron_right),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null
        )
    }
}

/** An artist in a grid (tablets): a large round picture with the name and counts below. */
@Composable
fun ArtistTile(artist: Artist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickableCompat(onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ArtistAvatar(artist, size = 144.dp)
        Spacer(Modifier.size(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = pluralStringResource(R.plurals.search_songs_count, artist.songCount, artist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(role = Role.Button, onClick = onClick)
