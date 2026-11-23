package com.bsoft.compose.bmusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.Artist
import androidx.compose.runtime.saveable.rememberSaveable
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.SongSort
import com.bsoft.compose.bmusic.data.models.sortedForDisplay
import com.bsoft.compose.bmusic.data.serializables.AppSettingsData
import com.bsoft.compose.bmusic.ui.components.BottomControl
import com.bsoft.compose.bmusic.ui.components.SongMenuRequest
import com.bsoft.compose.bmusic.ui.components.PlaylistForm
import com.bsoft.compose.bmusic.ui.components.PlaylistMenu
import com.bsoft.compose.bmusic.ui.components.alerts.PlaylistDeleteAlertDialog
import com.bsoft.compose.bmusic.ui.pages.AlbumPage
import com.bsoft.compose.bmusic.ui.pages.ArtistsPage
import com.bsoft.compose.bmusic.ui.pages.FoldersPage
import com.bsoft.compose.bmusic.ui.pages.PlaylistsOptions
import com.bsoft.compose.bmusic.ui.pages.PlaylistsPage
import com.bsoft.compose.bmusic.ui.pages.SongsPage
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import com.bsoft.compose.bmusic.utils.HomeDestination
import com.bsoft.compose.bmusic.utils.Route
import com.bsoft.compose.bmusic.utils.ScreenOverlay
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import com.bsoft.compose.bmusic.ui.components.TestTags
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bsoft.compose.bmusic.ui.components.HomeHeader
import com.bsoft.compose.bmusic.ui.components.HomeTabChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    viewModel: SongsViewModel = hiltViewModel(),
    playingViewModel: PlayingViewModel = hiltViewModel(),
    toScreen: (Route) -> Unit,
    toAlbum: (Album) -> Unit,
    toArtist: (Artist) -> Unit,
    toPlaylist: (PlaylistsOptions?, Pair<Long, String>?) -> Unit,
    toAddSongs: (Long, String) -> Unit,
    /** Saved settings; null while they load. Drives "remember last tab" and the song sort. */
    settings: AppSettingsData? = null,
    onTabChanged: (Int) -> Unit = {},
    onSongSortChanged: (SongSort) -> Unit = {},
    openSearch: () -> Unit = {},
    openSongMenu: (SongMenuRequest) -> Unit = {},
    /**
     * Tablet layout: Home sits in the phone-sized library section. The navigation rail belongs to
     * the whole layout (it drives [requestedTab]), Stats and Settings live on the player, and
     * the player is always on screen, so there are no chips, header buttons or mini player.
     */
    tabletLibrary: Boolean = false,
    requestedTab: Int? = null,
    onPageShown: (Int) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { HomeDestination.entries.size })
    var currentDestination by remember { mutableStateOf(HomeDestination.PLAYLISTS) }

    // "Remember last open tab": jump to the saved tab once settings arrive, then save changes.
    var restoredTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings != null) {
        val saved = settings ?: return@LaunchedEffect
        if (!restoredTab) {
            restoredTab = true
            if (saved.rememberLastTab && saved.lastTab in HomeDestination.entries.indices) {
                pagerState.scrollToPage(saved.lastTab)
            }
        }
    }
    LaunchedEffect(requestedTab) {
        val tab = requestedTab ?: return@LaunchedEffect
        if (tab in HomeDestination.entries.indices && tab != pagerState.currentPage) pagerState.scrollToPage(tab)
    }
    LaunchedEffect(pagerState.currentPage) {
        onPageShown(pagerState.currentPage)
        currentDestination = HomeDestination.entries[pagerState.currentPage]
        if (restoredTab && settings?.rememberLastTab == true && settings.lastTab != pagerState.currentPage) {
            onTabChanged(pagerState.currentPage)
        }
    }


    val songState by viewModel.state.collectAsStateWithLifecycle()
    val songSort = SongSort.fromName(settings?.songSort)
    val sortedSongs = remember(songState.songs, songSort) { songState.songs.sortedForDisplay(songSort) }
    val playState by playingViewModel.state.collectAsStateWithLifecycle()
    val queueState by playingViewModel.queueState.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(ScreenOverlay()) }
    var renamingPlaylist by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    val playlistFormSheetState = rememberBottomSheetState(
        enabledValues = setOf(SheetValue.Expanded, SheetValue.Hidden),
        initialValue = SheetValue.Expanded
    )

    val playlistMenuSheetState = rememberBottomSheetState(
        enabledValues = setOf(SheetValue.Expanded, SheetValue.Hidden),
        initialValue = SheetValue.Expanded
    )

    val isMobile = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    
    // Force Navigation Rail for Tablet and None for Mobile (to use Tabs instead)
    val navSuiteType = if (isMobile || tabletLibrary) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    }

    NavigationSuiteScaffold(
        layoutType = navSuiteType,
        navigationSuiteItems = {
            HomeDestination.entries.forEach { destination ->
                val selected = currentDestination == destination
                item(
                    selected = selected,
                    onClick = {
                        currentDestination = destination
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(HomeDestination.entries.indexOf(destination))
                        }
                    },
                    icon = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = ImageVector.vectorResource(if (selected) destination.selectedIcon else destination.icon),
                            contentDescription = stringResource(destination.labelRes)
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                HomeHeader(
                    modifier = Modifier.statusBarsPadding(),
                    onStats = if (tabletLibrary) null else ({ toScreen(Route.Stats) }),
                    onSettings = if (tabletLibrary) null else ({ toScreen(Route.Settings) }),
                    onSearch = openSearch
                )
            },
            floatingActionButton = {
                if (pagerState.currentPage == 0) {
                    FloatingActionButton(
                        onClick = { showOverlay = showOverlay.copy(item = ScreenOverlay.Item.PlaylistForm) },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ms_playlist_add),
                            contentDescription = stringResource(R.string.create_playlist)
                        )
                    }
                }
            },
            bottomBar = {
                if (!tabletLibrary) BottomControl(
                    state = playState,
                    queueState = queueState,
                    clicked = { toScreen(Route.Playing) },
                    onNext = { playingViewModel.next() }
                ) {
                    playingViewModel.togglePlayPause()
                }
            }
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                // Phones: pill tabs. Tablets use the navigation rail instead.
                if (isMobile && !tabletLibrary) {
                    HomeTabChips(
                        selected = pagerState.currentPage,
                        onSelect = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                HorizontalPager(
                    modifier = Modifier.fillMaxSize().weight(1f).testTag(TestTags.HOME_PAGER),
                    state = pagerState,
                    userScrollEnabled = isMobile && !tabletLibrary // Only allow swipe on phones
                ) { page ->
                    when (page) {
                        0 -> PlaylistsPage(
                            windowSizeClass = windowSizeClass,
                            viewModel = viewModel,
                            playingViewModel = playingViewModel,
                            openPlaylist = { toPlaylist(it, null) },
                            openCustomPlaylist = { openCustomPlaylist ->
                                toPlaylist(null, Pair(openCustomPlaylist.id, openCustomPlaylist.name))
                            },
                            openPlaylistMenu = { playlistMenu ->
                                showOverlay = showOverlay.copy(
                                    item = ScreenOverlay.Item.PlaylistMenu,
                                    id = playlistMenu.id,
                                    name = playlistMenu.name
                                )
                            }
                        )
                        1 -> SongsPage(
                            modifier = Modifier.fillMaxSize(),
                            songs = sortedSongs,
                            sort = songSort,
                            onSortChange = onSongSortChanged,
                            queueState = queueState,
                            playing = playState.playing,
                            onMenu = { openSongMenu(SongMenuRequest(it)) },
                            onShuffleAll = {
                                sortedSongs.indices.randomOrNull()?.let { start ->
                                    playingViewModel.playLibraryList(sortedSongs, start, true, MediaSource.Songs.prefix)
                                }
                            }
                        ) { index, _ ->
                            // Queue exactly what is on screen, in the chosen order; keep shuffle as is.
                            playingViewModel.playLibraryList(sortedSongs, index, null, MediaSource.Songs.prefix)
                        }
                        2 -> AlbumPage(
                            windowSizeClass = windowSizeClass,
                            albums = songState.albums
                        ) { album ->
                            toAlbum(album)
                        }
                        3 -> ArtistsPage(
                            windowSizeClass = windowSizeClass,
                            artists = songState.artists,
                            chosen = { artist -> toArtist(artist) }
                        )
                        4 -> FoldersPage(
                            songs = songState.songs,
                            queueState = queueState,
                            playing = playState.playing,
                            onMenu = { openSongMenu(SongMenuRequest(it)) },
                            play = { folder, songs, index, shuffle ->
                                playingViewModel.playLibraryList(songs, index, shuffle, MediaSource.Folder(folder.key).prefix)
                            }
                        )
                    }
                }
            }
        }

        if (showOverlay.item == ScreenOverlay.Item.PlaylistForm) {
            ModalBottomSheet(
                sheetState = playlistFormSheetState,
                onDismissRequest = { showOverlay = ScreenOverlay() }
            ) {
                PlaylistForm {
                    viewModel.createPlaylist(it)
                    showOverlay = ScreenOverlay()
                }
            }
        }

        if (showOverlay.item == ScreenOverlay.Item.PlaylistMenu) {
            ModalBottomSheet(
                sheetState = playlistMenuSheetState,
                onDismissRequest = { showOverlay = ScreenOverlay() }
            ) {
                PlaylistMenu(
                    playlist = showOverlay.id,
                    playlistName = showOverlay.name,
                    play = { playlistId ->
                        coroutineScope.launch {
                            val songs = viewModel.playlistRepository.getSongsFromPlayList(playlistId).map { it.song }
                            playingViewModel.playLibraryList(songs, 0, false, "playlist_$playlistId")
                            showOverlay = ScreenOverlay()
                        }
                    },
                    shuffle = { playlistId ->
                        coroutineScope.launch {
                            val songs = viewModel.playlistRepository.getSongsFromPlayList(playlistId).map { it.song }
                            songs.indices.randomOrNull()?.let { start -> playingViewModel.playLibraryList(songs, start, true, "playlist_$playlistId") }
                            showOverlay = ScreenOverlay()
                        }
                    },
                    edit = { playlistId ->
                        val name = showOverlay.name
                        showOverlay = ScreenOverlay()
                        toPlaylist(null, Pair(playlistId, name))
                    },
                    rename = { playlistId ->
                        renameText = showOverlay.name
                        renamingPlaylist = Pair(playlistId, showOverlay.name)
                        showOverlay = ScreenOverlay()
                    },
                    open = {
                        val id = showOverlay.id
                        val name = showOverlay.name
                        showOverlay = ScreenOverlay()
                        toPlaylist(null, Pair(id, name))
                    },
                    addSongs = {
                        val id = showOverlay.id
                        val name = showOverlay.name
                        showOverlay = ScreenOverlay()
                        toAddSongs(id, name)
                    }
                )
            }
        }

        renamingPlaylist?.let { target ->
            AlertDialog(
                onDismissRequest = { renamingPlaylist = null },
                title = { Text(stringResource(R.string.rename_playlist)) },
                text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) }) },
                confirmButton = { TextButton(onClick = { viewModel.renamePlaylist(target.first, renameText); renamingPlaylist = null }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { renamingPlaylist = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (showOverlay.item == ScreenOverlay.Item.PlaylistDeleteDialog) {
            PlaylistDeleteAlertDialog(
                playlist = showOverlay.id,
                playlistName = showOverlay.name,
                onConfirmation = {
                    viewModel.deletePlaylist(showOverlay.id)
                    showOverlay = ScreenOverlay()
                },
                onDismissRequest = {
                    showOverlay = ScreenOverlay()
                }
            )
        }
    }
}
