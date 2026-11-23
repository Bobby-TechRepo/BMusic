package com.bsoft.compose.bmusic.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.data.models.LibrarySearch
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.SearchResults
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.ui.components.ArtworkThumb
import com.bsoft.compose.bmusic.ui.components.EmptyState
import com.bsoft.compose.bmusic.ui.components.InitialAvatar
import com.bsoft.compose.bmusic.ui.components.MediaRow
import com.bsoft.compose.bmusic.ui.components.SectionHeader
import com.bsoft.compose.bmusic.ui.components.SongMenuRequest
import com.bsoft.compose.bmusic.ui.components.highlightMatch
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.SearchViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import androidx.compose.foundation.layout.heightIn

/** What the results are narrowed to. */
private enum class SearchFilter(@StringRes val label: Int) {
    ALL(R.string.search_filter_all),
    SONGS(R.string.search_filter_songs),
    ALBUMS(R.string.search_filter_albums),
    ARTISTS(R.string.search_filter_artists),
    PLAYLISTS(R.string.search_filter_playlists),
}

/** How many of each kind the "All" view shows before "See all". */
private const val PREVIEW_COUNT = 4

/** The single best match, shown large at the top of "All". */
private sealed interface TopResult {
    data class OfSong(val song: Song, val index: Int) : TopResult
    data class OfAlbum(val album: Album) : TopResult
    data class OfArtist(val artist: Artist) : TopResult
}

/** An exact name match wins (artist, then album, then song); otherwise the best song. */
private fun topResultOf(results: SearchResults, query: String): TopResult? {
    val needle = LibrarySearch.normalize(query).trim()
    results.artists.firstOrNull { LibrarySearch.normalize(it.name) == needle }?.let { return TopResult.OfArtist(it) }
    results.albums.firstOrNull { LibrarySearch.normalize(it.name) == needle }?.let { return TopResult.OfAlbum(it) }
    results.songs.firstOrNull()?.let { return TopResult.OfSong(it, 0) }
    results.artists.firstOrNull()?.let { return TopResult.OfArtist(it) }
    results.albums.firstOrNull()?.let { return TopResult.OfAlbum(it) }
    return null
}

/**
 * Searches songs, albums, artists and playlists as you type. Shows recent searches before
 * typing; with results, a top match, then each kind (filter chips narrow it to one).
 */
@Composable
fun SearchScreen(
    songsViewModel: SongsViewModel,
    playingViewModel: PlayingViewModel,
    openSongMenu: (SongMenuRequest) -> Unit,
    toAlbum: (Album) -> Unit,
    toArtist: (Artist) -> Unit,
    toPlaylist: (PlaylistEntity) -> Unit,
    /** Null hides the back arrow (tablets, where the navigation rail is always on screen). */
    back: (() -> Unit)?,
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val songsState by songsViewModel.state.collectAsStateWithLifecycle()
    val playlists by songsViewModel.playlists.collectAsStateWithLifecycle()
    val queueState by playingViewModel.queueState.collectAsStateWithLifecycle()
    val recent by searchViewModel.recentSearches.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SearchFilter.ALL.name) }
    val filter = SearchFilter.valueOf(filterName)

    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val results = remember(query, songsState, playlists) {
        LibrarySearch.search(query, songsState.songs, songsState.albums, songsState.artists, playlists)
    }
    val topResult = remember(results, query) { topResultOf(results, query) }

    val listState = rememberLazyListState()
    // Scrolling the results means reading them: get the keyboard out of the way.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }
    // A new query starts at the top.
    LaunchedEffect(query, filter) { listState.scrollToItem(0) }

    // Opening a result is what makes a query worth keeping in the history.
    fun keep() = searchViewModel.remember(query)
    val playSong: (Int) -> Unit = { index ->
        keep()
        playingViewModel.playLibraryList(results.songs, index, null, MediaSource.Custom.prefix)
    }
    val openAlbum: (Album) -> Unit = { keep(); toAlbum(it) }
    val openArtist: (Artist) -> Unit = { keep(); toArtist(it) }
    val openPlaylist: (PlaylistEntity) -> Unit = { keep(); toPlaylist(it) }

    Scaffold(
        topBar = {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                focusRequester = focusRequester,
                onBack = back,
                onSearch = { keep(); keyboard?.hide() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            AnimatedVisibility(visible = query.isNotBlank() && !results.isEmpty, enter = fadeIn(), exit = fadeOut()) {
                FilterRow(selected = filter, results = results, onSelect = { filterName = it.name })
            }
            when {
                query.isBlank() && recent.isEmpty() -> EmptyState(
                    icon = R.drawable.ms_search,
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(R.string.search_empty_message)
                )
                query.isBlank() -> RecentSearches(
                    recent = recent,
                    onPick = { query = it },
                    onRemove = searchViewModel::forget,
                    onClear = searchViewModel::clearHistory
                )
                results.isEmpty -> EmptyState(
                    icon = R.drawable.ms_search,
                    title = stringResource(R.string.search_no_results, query.trim()),
                    message = stringResource(R.string.search_no_results_hint)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    val currentId = queueState.current?.id
                    when (filter) {
                        SearchFilter.ALL -> {
                            topResult?.let { top ->
                                item(key = "top") {
                                    SectionHeader(stringResource(R.string.search_top_result))
                                    TopResultCard(top, query, onSong = playSong, onAlbum = openAlbum, onArtist = openArtist)
                                }
                            }
                            songRows(results.songs.take(PREVIEW_COUNT), query, currentId, openSongMenu, playSong,
                                header = results.songs.size to { filterName = SearchFilter.SONGS.name })
                            if (results.albums.isNotEmpty()) item(key = "albums") {
                                SectionHeader(
                                    stringResource(R.string.search_filter_albums),
                                    action = stringResource(R.string.search_see_all).takeIf { results.albums.size > PREVIEW_COUNT },
                                    onAction = { filterName = SearchFilter.ALBUMS.name }
                                )
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(results.albums.take(12), key = { it.id }) { album -> AlbumCard(album, query) { openAlbum(album) } }
                                }
                            }
                            if (results.artists.isNotEmpty()) item(key = "artists") {
                                SectionHeader(
                                    stringResource(R.string.search_filter_artists),
                                    action = stringResource(R.string.search_see_all).takeIf { results.artists.size > PREVIEW_COUNT },
                                    onAction = { filterName = SearchFilter.ARTISTS.name }
                                )
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(results.artists.take(12), key = { it.id }) { artist -> ArtistBubble(artist, query) { openArtist(artist) } }
                                }
                            }
                            playlistRows(results.playlists.take(PREVIEW_COUNT), query, openPlaylist,
                                header = results.playlists.size to { filterName = SearchFilter.PLAYLISTS.name })
                        }
                        SearchFilter.SONGS -> songRows(results.songs, query, currentId, openSongMenu, playSong, header = null)
                        SearchFilter.ALBUMS -> itemsIndexed(results.albums, key = { _, a -> "album-${a.id}" }) { _, album ->
                            MediaRow(
                                title = highlightMatch(album.name, query, MaterialTheme.colorScheme.primary),
                                subtitle = album.artist,
                                leading = { ArtworkThumb(album.artworkUri, size = 56.dp) },
                                onClick = { openAlbum(album) }
                            )
                        }
                        SearchFilter.ARTISTS -> itemsIndexed(results.artists, key = { _, a -> "artist-${a.id}" }) { _, artist ->
                            MediaRow(
                                title = highlightMatch(artist.name, query, MaterialTheme.colorScheme.primary),
                                subtitle = pluralStringResource(R.plurals.search_songs_count, artist.songCount, artist.songCount) +
                                    " · " + pluralStringResource(R.plurals.search_albums_count, artist.albumCount, artist.albumCount),
                                leading = { InitialAvatar(artist.name, size = 56.dp) },
                                onClick = { openArtist(artist) }
                            )
                        }
                        SearchFilter.PLAYLISTS -> playlistRows(results.playlists, query, openPlaylist, header = null)
                    }
                }
            }
        }
    }
}

/** Back arrow (when [onBack] is set) and a rounded field with a search icon and a clear button. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.material_symbols__arrow_back_ios_new_rounded),
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
                Box(modifier = Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_library_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() })
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = ImageVector.vectorResource(R.drawable.ms_close),
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(selected: SearchFilter, results: SearchResults, onSelect: (SearchFilter) -> Unit) {
    val counts = mapOf(
        SearchFilter.SONGS to results.songs.size,
        SearchFilter.ALBUMS to results.albums.size,
        SearchFilter.ARTISTS to results.artists.size,
        SearchFilter.PLAYLISTS to results.playlists.size,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchFilter.entries.filter { it == SearchFilter.ALL || (counts[it] ?: 0) > 0 }, key = { it.name }) { option ->
            val count = counts[option]
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = CircleShape,
                label = { Text(if (count == null) stringResource(option.label) else stringResource(R.string.search_filter_count, stringResource(option.label), count)) },
                leadingIcon = if (option == selected) {
                    { Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_check), contentDescription = null) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(stringResource(R.string.search_recent), action = stringResource(R.string.search_clear_history), onAction = onClear)
        }
        items(recent, key = { it }) { text ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPick(text) }.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onRemove(text) }) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = ImageVector.vectorResource(R.drawable.ms_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(R.string.cd_remove_recent_search, text)
                    )
                }
            }
        }
    }
}

/** Songs with artwork; the playing one is marked. [header] = (total, open "See all"). */
private fun LazyListScope.songRows(
    songs: List<Song>,
    query: String,
    currentId: Long?,
    openSongMenu: (SongMenuRequest) -> Unit,
    play: (Int) -> Unit,
    header: Pair<Int, () -> Unit>?,
) {
    if (songs.isEmpty()) return
    if (header != null) item(key = "songs-header") {
        SectionHeader(
            stringResource(R.string.search_filter_songs),
            action = stringResource(R.string.search_see_all).takeIf { header.first > songs.size },
            onAction = header.second
        )
    }
    itemsIndexed(songs, key = { _, song -> "song-${song.id}" }) { index, song ->
        val playing = song.id == currentId
        MediaRow(
            title = if (playing) AnnotatedString(song.title) else highlightMatch(song.title, query, MaterialTheme.colorScheme.primary),
            subtitle = stringResource(R.string.search_song_detail, song.artist, song.album),
            leading = { ArtworkThumb(song.mediaUri, size = 52.dp) },
            onClick = { play(index) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = ImageVector.vectorResource(R.drawable.ms_graphic_eq),
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = stringResource(R.string.cd_now_playing)
                        )
                    }
                    IconButton(onClick = { openSongMenu(SongMenuRequest(song)) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.circum__menu_kebab),
                            contentDescription = stringResource(R.string.cd_song_options)
                        )
                    }
                }
            }
        )
    }
}

private fun LazyListScope.playlistRows(
    playlists: List<PlaylistEntity>,
    query: String,
    open: (PlaylistEntity) -> Unit,
    header: Pair<Int, () -> Unit>?,
) {
    if (playlists.isEmpty()) return
    if (header != null) item(key = "playlists-header") {
        SectionHeader(
            stringResource(R.string.search_filter_playlists),
            action = stringResource(R.string.search_see_all).takeIf { header.first > playlists.size },
            onAction = header.second
        )
    }
    itemsIndexed(playlists, key = { _, p -> "playlist-${p.id}" }) { _, playlist ->
        MediaRow(
            title = highlightMatch(playlist.name, query, MaterialTheme.colorScheme.primary),
            subtitle = pluralStringResource(R.plurals.search_songs_count, playlist.count, playlist.count),
            leading = {
                Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            modifier = Modifier.size(26.dp),
                            imageVector = ImageVector.vectorResource(R.drawable.ms_queue_music),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            contentDescription = null
                        )
                    }
                }
            },
            onClick = { open(playlist) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopResultCard(
    top: TopResult,
    query: String,
    onSong: (Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val (title, kind, open) = when (top) {
        is TopResult.OfSong -> Triple(top.song.title, stringResource(R.string.search_type_song, top.song.artist)) { onSong(top.index) }
        is TopResult.OfAlbum -> Triple(top.album.name, stringResource(R.string.search_type_album, top.album.artist)) { onAlbum(top.album) }
        is TopResult.OfArtist -> Triple(top.artist.name, stringResource(R.string.search_type_artist)) { onArtist(top.artist) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = open
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (top) {
                is TopResult.OfSong -> ArtworkThumb(top.song.mediaUri, size = 84.dp, shape = RoundedCornerShape(16.dp))
                is TopResult.OfAlbum -> ArtworkThumb(top.album.artworkUri, size = 84.dp, shape = RoundedCornerShape(16.dp))
                is TopResult.OfArtist -> InitialAvatar(top.artist.name, size = 84.dp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = highlightMatch(title, query, accent),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        text = kind,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (top is TopResult.OfSong) {
                FilledIconButton(modifier = Modifier.size(48.dp), onClick = open) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ms_play_circle),
                        contentDescription = stringResource(R.string.play)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, query: String, onClick: () -> Unit) {
    Column(modifier = Modifier.width(136.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ArtworkThumb(album.artworkUri, size = 136.dp, shape = RoundedCornerShape(16.dp), placeholder = R.drawable.ms_album)
        Text(
            text = highlightMatch(album.name, query, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistBubble(artist: Artist, query: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InitialAvatar(artist.name, size = 88.dp)
        Text(
            text = highlightMatch(artist.name, query, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
