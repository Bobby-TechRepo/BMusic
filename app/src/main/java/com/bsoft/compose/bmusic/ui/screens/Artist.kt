package com.bsoft.compose.bmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.ui.components.AlbumTile
import com.bsoft.compose.bmusic.ui.components.DetailArtwork
import com.bsoft.compose.bmusic.ui.components.DetailBackdrop
import com.bsoft.compose.bmusic.ui.components.DetailHeader
import com.bsoft.compose.bmusic.ui.components.DetailSectionTitle
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

/** An artist: round picture, Play / Shuffle, their albums in a row, then all their songs. */
@Composable
fun ArtistScreen(
    modifier: Modifier = Modifier, id: Long, viewModel: SongsViewModel, play: (Artist, Int) -> Unit, playAll: (MediaItem, Boolean) -> Unit,
    toAlbum: (Album) -> Unit, openSongMenu: (SongMenuRequest) -> Unit = {},
    nowPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    togglePlayPause: () -> Unit = {},
    back: () -> Unit
) {
    TransparentStatusBarHandler()
    val artistState by viewModel.artistState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.queryArtist(id) }

    val listState = rememberLazyListState()
    val threshold = headerThresholdPx()
    val collapsed by remember { derivedStateOf { headerScrolledAway(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, threshold) } }
    val totalMs = remember(artistState.songs) { artistState.songs.sumOf { it.duration } }

    val playingHere = nowPlayingMediaId?.startsWith(MediaSource.Artist(id).prefix + "#") == true
    val currentIndex = if (playingHere) nowPlayingMediaId.orEmpty().substringAfter('#').substringBefore('|').toIntOrNull() else null

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(bitmap = artistState.bitmap, fallback = R.drawable.artist_bg)

        val error = artistState.error
        if (error != null) {
            StatusScreen(
                modifier = Modifier.padding(top = 80.dp),
                title = stringResource(R.string.error_title), body = error, icon = R.drawable.marketeq__caution,
                primaryLabel = stringResource(R.string.back), onPrimary = back
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag(TestTags.DETAIL_LIST), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") {
                    val artist = artistState.artist
                    DetailHeader(
                        artwork = { DetailArtwork(artistState.bitmap, placeholder = R.drawable.ms_person, round = true) },
                        title = artist?.name ?: stringResource(R.string.unknown),
                        subtitle = null,
                        meta = stringResource(
                            R.string.song_row_subtitle,
                            pluralStringResource(R.plurals.search_songs_count, artistState.songs.size, artistState.songs.size),
                            totalDurationText(totalMs)
                        ),
                        playing = playingHere && isPlaying,
                        enabled = artist != null && artistState.songs.isNotEmpty(),
                        onPlay = {
                            if (playingHere) togglePlayPause()
                            else artist?.let { playAll(it.toMediaItem(0), false) }
                        },
                        onShuffle = {
                            artist?.let { a -> artistState.songs.indices.randomOrNull()?.let { start -> playAll(a.toMediaItem(start), true) } }
                        }
                    )
                }
                if (artistState.albums.isNotEmpty()) {
                    item(key = "albums") {
                        DetailSectionTitle(stringResource(R.string.search_filter_albums))
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(artistState.albums, key = { it.id }) { album ->
                                AlbumTile(album = album, modifier = Modifier.width(148.dp), onDark = true) { toAlbum(album) }
                            }
                        }
                    }
                }
                item(key = "songs-title") { DetailSectionTitle(stringResource(R.string.search_filter_songs)) }
                itemsIndexed(artistState.songs, key = { _, song -> song.id }) { index, song ->
                    QueueSongView(
                        song = song,
                        currentPlaying = currentIndex == index,
                        playing = isPlaying,
                        showMenu = true,
                        menuClicked = { openSongMenu(SongMenuRequest(it)) }
                    ) {
                        artistState.artist?.let { artist -> play(artist, index) }
                    }
                }
            }
        }

        DetailTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = artistState.artist?.name ?: "",
            collapsed = collapsed,
            onBack = back
        )
    }
}
