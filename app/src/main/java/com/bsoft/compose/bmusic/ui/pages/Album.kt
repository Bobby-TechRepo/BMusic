package com.bsoft.compose.bmusic.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.ui.components.AlbumTile
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import com.bsoft.compose.bmusic.ui.components.TestTags
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

/** The Albums tab: a grid of album covers (two columns on phones). */
@Composable
fun AlbumPage(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    albums: List<Album>,
    chosen: (album: Album) -> Unit
) {
    if (albums.isEmpty()) {
        StatusScreen(
            modifier = modifier,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body)
        )
        return
    }
    val gridColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        GridCells.Fixed(count = 2)
    } else {
        GridCells.Adaptive(minSize = 180.dp)
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize().testTag(TestTags.ALBUMS_GRID),
        columns = gridColumns,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumTile(album = album) { chosen(album) }
        }
    }
}

@Preview
@Composable
fun AlbumPagePreview(){
    BMusicTheme {
        Surface {
            Text("Album Page Preview")
        }
    }
}
