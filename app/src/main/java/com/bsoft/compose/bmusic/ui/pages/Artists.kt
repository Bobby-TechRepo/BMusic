package com.bsoft.compose.bmusic.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.ui.components.ArtistRow
import com.bsoft.compose.bmusic.ui.components.ArtistTile
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import com.bsoft.compose.bmusic.ui.components.TestTags
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/** The Artists tab: a list on phones, a grid of round pictures on larger screens. */
@Composable
fun ArtistsPage(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    artists: List<Artist>,
    chosen: (artist: Artist) -> Unit
) {
    if (artists.isEmpty()) {
        StatusScreen(
            modifier = modifier,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body)
        )
        return
    }
    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        LazyColumn(
            modifier = modifier.fillMaxSize().testTag(TestTags.ARTISTS_LIST),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistRow(artist = artist) { chosen(artist) }
            }
        }
    } else {
        LazyVerticalGrid(
            modifier = modifier.fillMaxSize().testTag(TestTags.ARTISTS_LIST),
            columns = GridCells.Adaptive(minSize = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistTile(artist = artist) { chosen(artist) }
            }
        }
    }
}

@Preview
@Composable
fun ArtistsPagePreview(){
    BMusicTheme {
        Surface {
            Text("Artists Page Preview")
        }
    }
}
