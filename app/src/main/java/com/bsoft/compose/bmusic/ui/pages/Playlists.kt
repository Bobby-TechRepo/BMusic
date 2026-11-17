package com.bsoft.compose.bmusic.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.ui.components.MessageToast
import com.bsoft.compose.bmusic.ui.components.PlaylistEntityView
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.PlaylistTransferViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import com.bsoft.compose.bmusic.ui.components.TestTags
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height

/** MIME types file managers use for .m3u / .m3u8 files. */
private val M3U_MIME_TYPES = arrayOf(
    "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl",
    "application/octet-stream", "text/plain"
)

enum class PlaylistsOptions(val titleRes: Int, @DrawableRes val bg: Int, @DrawableRes val icon: Int){
    Favourites(titleRes = R.string.playlist_favourites, bg = R.drawable.favourites, icon = R.drawable.ms_favorite_fill1),
    RecentlyAdded(titleRes = R.string.playlist_recently_added, bg = R.drawable.recently_added, icon = R.drawable.ms_new_releases),
    MostPlayed(titleRes = R.string.playlist_most_played, bg = R.drawable.most_played, icon = R.drawable.ms_bar_chart),
    RecentlyPlayed(titleRes = R.string.playlist_recently_played, bg = R.drawable.recently_played, icon = R.drawable.ms_history),
    Rediscover(titleRes = R.string.playlist_rediscover, bg = R.drawable.playlist_bg, icon = R.drawable.ms_auto_awesome),
    NeverPlayed(titleRes = R.string.playlist_never_played, bg = R.drawable.album_bg, icon = R.drawable.ms_explore),
}

@Composable
fun PlaylistsPage(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    viewModel: SongsViewModel = hiltViewModel(),
    transferViewModel: PlaylistTransferViewModel = hiltViewModel(),
    playingViewModel: PlayingViewModel = hiltViewModel(),
    openPlaylist: (PlaylistsOptions) -> Unit,
    openCustomPlaylist: (PlaylistEntity) -> Unit,
    openPlaylistMenu: (PlaylistEntity) -> Unit
) {
    val songsState by viewModel.state.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val transferMessage by transferViewModel.message.collectAsStateWithLifecycle()
    MessageToast(transferMessage) { transferViewModel.dismissMessage() }
    // M3U files have no single MIME type across file managers, so offer the usual ones.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { transferViewModel.import(it) }
    }

    val itemsList = listOf(
        Pair(PlaylistsOptions.Favourites, favourites.size),
        Pair(PlaylistsOptions.RecentlyAdded, songsState.last.size),
        Pair(PlaylistsOptions.MostPlayed, null),
        Pair(PlaylistsOptions.RecentlyPlayed, null),
        Pair(PlaylistsOptions.Rediscover, null),
        Pair(PlaylistsOptions.NeverPlayed, null)
    )

    val gridColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        GridCells.Fixed(2)
    } else {
        GridCells.Adaptive(minSize = 200.dp)
    }

    // One scrolling grid: built-in tiles, then the custom playlists at full width.
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize().testTag(TestTags.PLAYLISTS_GRID),
        columns = gridColumns,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itemsList.size, key = { itemsList[it].first.name }) { index ->
            val (option, count) = itemsList[index]
            SmartPlaylistTile(option = option, count = count) { openPlaylist(option) }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.custom_playlists), style = MaterialTheme.typography.titleMedium)
                    if (playlists.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(R.plurals.playlist_count, playlists.size, playlists.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(M3U_MIME_TYPES) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_file_open), contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_m3u))
                }
            }
        }

        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ms_queue_music),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.no_custom_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(count = playlists.size, key = { playlists[it].id }, span = { GridItemSpan(maxLineSpan) }) { index ->
            val playlist = playlists[index]
            PlaylistEntityView(playlistEntity = playlist, clicked = { openCustomPlaylist(playlist) }, menu = { openPlaylistMenu(playlist) })
        }
    }
}

/** A built-in playlist: its picture with a dark gradient, an icon badge, the name and a count. */
@Composable
private fun SmartPlaylistTile(option: PlaylistsOptions, count: Int?, onClick: () -> Unit) {
    // Every tile is the same height. It only grows with large font sizes, so the name and
    // count always fit; the picture never sets the height (it just fills the tile).
    val fontScale = LocalDensity.current.fontScale
    Surface(
        modifier = Modifier.fillMaxWidth().height(132.dp * fontScale.coerceAtLeast(1f)),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier.matchParentSize(),
                painter = painterResource(option.bg),
                contentScale = ContentScale.Crop,
                contentDescription = null
            )
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.72f)))
                )
            )
            Surface(
                modifier = Modifier.padding(12.dp).size(36.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.20f),
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(modifier = Modifier.size(20.dp), imageVector = ImageVector.vectorResource(option.icon), contentDescription = null)
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    text = stringResource(option.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                count?.let {
                    Text(
                        text = pluralStringResource(R.plurals.search_songs_count, it, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
