package com.bsoft.compose.bmusic.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.ui.components.DetailArtwork
import com.bsoft.compose.bmusic.ui.components.DetailBackdrop
import com.bsoft.compose.bmusic.ui.components.DetailHeader
import com.bsoft.compose.bmusic.ui.components.DetailTopBar
import com.bsoft.compose.bmusic.ui.components.QueueSongView
import com.bsoft.compose.bmusic.ui.components.SongMenuRequest
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import com.bsoft.compose.bmusic.ui.components.TransparentStatusBarHandler
import com.bsoft.compose.bmusic.ui.components.headerScrolledAway
import com.bsoft.compose.bmusic.ui.components.headerThresholdPx
import com.bsoft.compose.bmusic.ui.components.totalDurationText
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import com.bsoft.compose.bmusic.ui.components.TestTags

/**
 * An album: large artwork, Play / Shuffle, then its songs in disc and track order, numbered.
 * [nowPlayingMediaId] and [isPlaying] mark the playing song and turn Play into Pause.
 */
@Composable
fun AlbumScreen(
    modifier: Modifier = Modifier, id: Long, viewModel: SongsViewModel,
    play: (Album, Int) -> Unit, playAll: (MediaItem, Boolean) -> Unit,
    openSongMenu: (SongMenuRequest) -> Unit = {},
    nowPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    togglePlayPause: () -> Unit = {},
    back: () -> Unit
) {
    TransparentStatusBarHandler()
    val albumState by viewModel.albumState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.queryAlbum(id) }

    val listState = rememberLazyListState()
    val threshold = headerThresholdPx()
    val collapsed by remember { derivedStateOf { headerScrolledAway(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, threshold) } }
    val totalMs = remember(albumState.songs) { albumState.songs.sumOf { it.duration } }

    // "album_5#2" means song 2 of album 5 is playing.
    val playingHere = nowPlayingMediaId?.startsWith(MediaSource.Album(id).prefix + "#") == true
    val currentIndex = if (playingHere) nowPlayingMediaId.orEmpty().substringAfter('#').substringBefore('|').toIntOrNull() else null

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(bitmap = albumState.bitmap, fallback = R.drawable.album_bg)

        val error = albumState.error
        if (error != null) {
            StatusScreen(
                modifier = Modifier.padding(top = 80.dp),
                title = stringResource(R.string.error_title), body = error, icon = R.drawable.marketeq__caution,
                primaryLabel = stringResource(R.string.back), onPrimary = back
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(TestTags.DETAIL_LIST),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    val album = albumState.album
                    DetailHeader(
                        artwork = { DetailArtwork(albumState.bitmap, placeholder = R.drawable.ms_album) },
                        title = album?.name ?: stringResource(R.string.unknown),
                        subtitle = album?.artist,
                        meta = stringResource(
                            R.string.song_row_subtitle,
                            pluralStringResource(R.plurals.search_songs_count, albumState.songs.size, albumState.songs.size),
                            totalDurationText(totalMs)
                        ),
                        playing = playingHere && isPlaying,
                        enabled = album != null && albumState.songs.isNotEmpty(),
                        onPlay = {
                            if (playingHere) togglePlayPause()
                            else album?.let { playAll(it.toMediaItem(0), false) }
                        },
                        onShuffle = {
                            album?.let { a -> albumState.songs.indices.randomOrNull()?.let { start -> playAll(a.toMediaItem(start), true) } }
                        }
                    )
                }
                itemsIndexed(albumState.songs, key = { _, song -> song.id }) { index, song ->
                    QueueSongView(
                        song = song,
                        trackNumber = index + 1,
                        currentPlaying = currentIndex == index,
                        playing = isPlaying,
                        showMenu = true,
                        menuClicked = { openSongMenu(SongMenuRequest(it)) }
                    ) {
                        albumState.album?.let { album -> play(album, index) }
                    }
                }
            }
        }

        DetailTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = albumState.album?.name ?: "",
            collapsed = collapsed,
            onBack = back
        )
    }
}
