package com.bsoft.compose.bmusic.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import com.bsoft.compose.bmusic.ui.components.SongActionHandlers
import com.bsoft.compose.bmusic.ui.components.SongActionsSheet
import com.bsoft.compose.bmusic.ui.components.SongMenuRequest
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import kotlinx.coroutines.launch
import com.bsoft.compose.bmusic.ui.screens.*
import com.bsoft.compose.bmusic.utils.Route
import com.bsoft.compose.bmusic.viewmodels.EqualizerViewModel
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.SettingsViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import com.bsoft.compose.bmusic.data.tags.EDITABLE_EXTENSIONS
import com.bsoft.compose.bmusic.data.tags.TagEditorRepository
import com.bsoft.compose.bmusic.data.tags.fileExtension
import com.bsoft.compose.bmusic.ui.screens.TagEditorScreen
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.navigation3.runtime.NavKey
import com.bsoft.compose.bmusic.utils.HomeDestination
import com.bsoft.compose.bmusic.utils.mergeForPhone
import com.bsoft.compose.bmusic.utils.openOnRight
import com.bsoft.compose.bmusic.utils.splitForTablet
import com.bsoft.compose.bmusic.data.models.MediaSource
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.bsoft.compose.bmusic.utils.ShortcutAction
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.bsoft.compose.bmusic.viewmodels.OpenedFile
import com.bsoft.compose.bmusic.viewmodels.OpenedFileViewModel
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun Main(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    viewModel: SongsViewModel,
    playingViewModel: PlayingViewModel,
    equalizerViewModel: EqualizerViewModel,
    settingsViewModel: SettingsViewModel,
    /** Hardware-keyboard shortcuts from the activity. */
    shortcuts: Flow<ShortcutAction> = emptyFlow(),
    /** A music file opened from another app, to play; null when there is none. */
    openedFile: StateFlow<Uri?> = MutableStateFlow(null),
    onOpenedFileHandled: () -> Unit = {},
    openedFileViewModel: OpenedFileViewModel = hiltViewModel(),
) {
    val settingsState by settingsViewModel.settings.collectAsStateWithLifecycle()
    val songsState by viewModel.state.collectAsStateWithLifecycle()
    // Saved across configuration changes and process death (language switch, dark mode).
    val rootBackStack = rememberNavBackStack(Route.Home)
    fun back() { if (rootBackStack.size > 1) rootBackStack.removeAt(rootBackStack.lastIndex) }

    // Tablet layout (landscape, >= 840 dp wide): a phone-sized library section on the left and the
    // player on the right, where only Settings and Listening stats can be swapped in for it.
    // Each side has its own back stack; see utils/Panes.kt.
    val tablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val rightStack = rememberNavBackStack(Route.Playing)
    // Effects started earlier (shortcuts, opened files) must see the current layout.
    val isTablet by rememberUpdatedState(tablet)
    var libraryTab by rememberSaveable { mutableIntStateOf(-1) }
    val isRightSide: (NavKey) -> Boolean = { it is Route.Playing || it is Route.Settings || it is Route.Stats }
    LaunchedEffect(tablet) {
        if (tablet) {
            val split = splitForTablet(rootBackStack.toList(), Route.Home, Route.Playing, isRightSide)
            if (split.left != rootBackStack.toList()) { rootBackStack.clear(); rootBackStack.addAll(split.left) }
            if (split.right != rightStack.toList()) { rightStack.clear(); rightStack.addAll(split.right) }
        } else {
            val merged = mergeForPhone(rootBackStack.toList(), rightStack.toList(), Route.Playing)
            if (merged != rootBackStack.toList()) { rootBackStack.clear(); rootBackStack.addAll(merged) }
            if (rightStack.toList() != listOf(Route.Playing)) { rightStack.clear(); rightStack.add(Route.Playing) }
        }
    }
    /** Opens a screen: on tablets the player, Settings and Stats open on the right. */
    fun navigate(route: Route) {
        if (isTablet && isRightSide(route)) {
            val next = openOnRight(rightStack.toList(), route, Route.Playing)
            if (next != rightStack.toList()) { rightStack.clear(); rightStack.addAll(next) }
        } else if (rootBackStack.lastOrNull() != route) {
            rootBackStack.add(route)
        }
    }
    fun backRight() { if (rightStack.size > 1) rightStack.removeAt(rightStack.lastIndex) }
    /** Tablet rail: back to Home in the library section, on the chosen tab. */
    fun showLibraryTab(index: Int) {
        while (rootBackStack.size > 1) rootBackStack.removeAt(rootBackStack.lastIndex)
        libraryTab = index
    }


    LaunchedEffect(shortcuts) {
        shortcuts.collect { action ->
            when (action) {
                ShortcutAction.PLAY_PAUSE -> playingViewModel.togglePlayPause()
                ShortcutAction.NEXT -> playingViewModel.next()
                ShortcutAction.PREVIOUS -> playingViewModel.previous()
                ShortcutAction.SEEK_FORWARD -> playingViewModel.forward()
                ShortcutAction.SEEK_BACK -> playingViewModel.rewind()
                ShortcutAction.SEARCH -> navigate(Route.Search)
            }
        }
    }

    // "Open with BMusic": play the file once the player service is connected, then show the player.
    val pendingFile by openedFile.collectAsStateWithLifecycle()
    LaunchedEffect(pendingFile) {
        val uri = pendingFile ?: return@LaunchedEffect
        playingViewModel.state.first { it.connected }
        when (val opened = openedFileViewModel.resolve(uri)) {
            is OpenedFile.Library -> playingViewModel.playLibraryList(
                opened.plan.songs, opened.plan.startIndex, null, opened.plan.contextPrefix
            )
            is OpenedFile.External -> playingViewModel.playItem(opened.item)
        }
        onOpenedFileHandled()
        navigate(Route.Playing)
    }

    val context = LocalContext.current
    // Only the audio-read permission is a runtime permission; the foreground-service ones are
    // normal permissions granted at install time.
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    fun onPermissionKnown(granted: Boolean) {
        viewModel.onStoragePermissionResult(granted)
        if (granted) playingViewModel.initializeMediaController(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionKnown(granted) }

    LaunchedEffect(Unit) {
        if (hasAudioPermission()) onPermissionKnown(true)
        else permissionLauncher.launch(audioPermission)
    }

    // The user may grant the permission in system Settings and come back.
    LifecycleResumeEffect(songsState.permissionDenied) {
        if (songsState.permissionDenied && hasAudioPermission()) onPermissionKnown(true)
        onPauseOrDispose { }
    }

    val playState by playingViewModel.state.collectAsStateWithLifecycle()
    val mainQueueState by playingViewModel.queueState.collectAsStateWithLifecycle()
    // Album and artist screens mark the playing song and turn Play into Pause.
    val nowPlayingMediaId = mainQueueState.queue.getOrNull(mainQueueState.currentIndex ?: -1)?.mediaId
    val snackbarHostState = remember { SnackbarHostState() }
    val messageScope = rememberCoroutineScope()
    fun showMessage(message: String) {
        messageScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // The song action sheet, shared by every list in the app.
    var songMenu by remember { mutableStateOf<SongMenuRequest?>(null) }
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        playingViewModel.playbackErrors.collect { title ->
            val message = if (title.isNullOrBlank()) context.getString(R.string.error_cannot_play_generic)
            else context.getString(R.string.error_cannot_play, title)
            snackbarHostState.showSnackbar(message)
        }
    }

    // Every screen, shared by the phone stack and both tablet sections. [right]: shown in the
    // tablet's right section, where back returns to the player.
    fun screens(right: Boolean, libraryWindow: WindowSizeClass) = entryProvider<NavKey> {
        val closeRight: () -> Unit = { if (right) backRight() else back() }
        entry<Route.Home> {
            HomeScreen(
                modifier = modifier,
                windowSizeClass = libraryWindow,
                viewModel = viewModel,
                playingViewModel = playingViewModel,
                toScreen = { navigate(it) },
                toAlbum = { navigate(Route.Album(it.id)) },
                toArtist = { navigate(Route.Artist(it.id)) },
                toPlaylist = { option, playlist ->
                    navigate(Route.Playlist(option, playlist = playlist?.first))
                },
                toAddSongs = { id, name -> navigate(Route.AddSongs(id, name)) },
                settings = settingsState,
                onTabChanged = { settingsViewModel.setLastTab(it) },
                onSongSortChanged = { settingsViewModel.setSongSort(it.name) },
                openSearch = { navigate(Route.Search) },
                openSongMenu = { songMenu = it },
                tabletLibrary = tablet,
                requestedTab = libraryTab.takeIf { tablet && it >= 0 },
                onPageShown = { libraryTab = it }
            )
        }
        entry<Route.Stats> {
            StatsScreen(back = closeRight)
        }
        entry<Route.Search> {
            SearchScreen(
                songsViewModel = viewModel,
                playingViewModel = playingViewModel,
                openSongMenu = { songMenu = it },
                toAlbum = { navigate(Route.Album(it.id)) },
                toArtist = { navigate(Route.Artist(it.id)) },
                toPlaylist = { navigate(Route.Playlist(null, it.id)) },
                // Tablets: the rail is always there, and system back still works.
                back = if (tablet) null else ({ back() })
            )
        }
        entry<Route.Album> { route ->
            AlbumScreen(
                modifier = modifier,
                id = route.id,
                viewModel = viewModel,
                play = { album, index ->
                    playingViewModel.playLibrary(album.toMediaItem(index))
                },
                playAll = { item, shuffle ->
                    playingViewModel.playLibraryAll(item, shuffle)
                },
                openSongMenu = { songMenu = it },
                nowPlayingMediaId = nowPlayingMediaId,
                isPlaying = playState.playing,
                togglePlayPause = { playingViewModel.togglePlayPause() },
                back = { back() }
            )
        }
        entry<Route.Artist> { route ->
            ArtistScreen(
                modifier = modifier,
                id = route.id,
                viewModel = viewModel,
                toAlbum = { album -> navigate(Route.Album(album.id)) },
                play = { artist, index ->
                    playingViewModel.playLibrary(artist.toMediaItem(index))
                },
                playAll = { item, shuffle ->
                    playingViewModel.playLibraryAll(item, shuffle)
                },
                openSongMenu = { songMenu = it },
                nowPlayingMediaId = nowPlayingMediaId,
                isPlaying = playState.playing,
                togglePlayPause = { playingViewModel.togglePlayPause() },
                back = { back() }
            )
        }
        entry<Route.Playlist> { route ->
            PlaylistScreen(
                modifier = modifier,
                playingViewModel = playingViewModel,
                songsViewModel = viewModel,
                playlistsOptions = route.playlistsOptions,
                id = route.playlist,
                add = { playlist, title ->
                    navigate(Route.AddSongs(playlist = playlist, title = title))
                },
                openSongMenu = { songMenu = it },
                back = { back() }
            )
        }
        entry<Route.Settings> {
            SettingsScreen(modifier = modifier, windowSizeClass = windowSizeClass) { closeRight() }
        }
        entry<Route.Playing> {
            val persistent = tablet && right
            PlayingScreen(
                modifier = modifier,
                windowSizeClass = windowSizeClass,
                playingViewModel = playingViewModel,
                equalizerViewModel = equalizerViewModel,
                onSaveQueueAsPlaylist = { name, songs ->
                    viewModel.createPlaylistWithSongs(name, songs)
                    showMessage(context.getString(R.string.saved_as_playlist, name))
                },
                persistent = persistent,
                onOpenStats = if (persistent) ({ navigate(Route.Stats) }) else null,
                onOpenSettings = if (persistent) ({ navigate(Route.Settings) }) else null,
                onShuffleAll = if (persistent && songsState.songs.isNotEmpty()) ({
                    val songs = songsState.songs
                    songs.indices.randomOrNull()?.let { start ->
                        playingViewModel.playLibraryList(songs, start, true, MediaSource.Songs.prefix)
                    }
                }) else null,
                onBrowseSongs = if (persistent) ({ showLibraryTab(HomeDestination.SONGS.ordinal) }) else null,
                back = { if (!persistent) closeRight() }
            )
        }
        entry<Route.TagEditor> {
            TagEditorScreen(
                songId = it.songId,
                onSaved = { showMessage(context.getString(R.string.tag_editor_saved)) },
                back = { back() }
            )
        }
        entry<Route.AddSongs> {
            AddSongsScreen(
                id = it.playlist,
                title = it.title,
                songsViewModel = viewModel,
                back = { back() }
            )
        }
    }

    // On the library's root, back leaves the app like any launcher activity (music keeps playing).
    val leaveOrBack: () -> Unit = { if (rootBackStack.size > 1) back() else (context as? Activity)?.moveTaskToBack(true) }
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tablet) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                // The library section is phone-sized: about a third of a landscape tablet.
                val libraryWidth = (maxWidth * 0.34f).coerceIn(360.dp, 420.dp)
                val libraryWindow = remember(libraryWidth, maxHeight) {
                    WindowSizeClass.calculateFromSize(DpSize(libraryWidth, maxHeight))
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    TabletRail(
                        selectedTab = libraryTab,
                        onSelect = { showLibraryTab(it) },
                        onSearch = { navigate(Route.Search) }
                    )
                    Box(modifier = Modifier.width(libraryWidth).fillMaxHeight()) {
                        NavDisplay(
                            backStack = rootBackStack,
                            onBack = leaveOrBack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = viewModelStoreOwner)
                            ),
                            entryProvider = screens(right = false, libraryWindow = libraryWindow)
                        )
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavDisplay(
                            backStack = rightStack,
                            onBack = { backRight() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = viewModelStoreOwner)
                            ),
                            entryProvider = screens(right = true, libraryWindow = libraryWindow)
                        )
                    }
                }
            }
        } else {
            NavDisplay(
                backStack = rootBackStack,
                onBack = leaveOrBack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = viewModelStoreOwner)
                ),
                entryProvider = screens(right = false, libraryWindow = windowSizeClass)
            )
        }

        when {
            songsState.permissionDenied -> StatusScreen(
                title = stringResource(R.string.permission_title),
                body = stringResource(R.string.permission_body),
                icon = R.drawable.arcticons__folder_music,
                primaryLabel = stringResource(R.string.permission_grant),
                onPrimary = { permissionLauncher.launch(audioPermission) },
                // After "Don't ask again" the dialog no longer appears; Settings always works.
                secondaryLabel = stringResource(R.string.open_settings),
                onSecondary = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            songsState.loaded && songsState.error != null -> StatusScreen(
                title = stringResource(R.string.error_title),
                body = songsState.error ?: stringResource(R.string.error_unexpected),
                icon = R.drawable.marketeq__caution,
                primaryLabel = stringResource(R.string.retry),
                onPrimary = { viewModel.query() }
            )

            settingsState == null || !songsState.loaded -> LoadingScreen(
                message = if (settingsState == null) stringResource(R.string.initializing)
                else stringResource(R.string.fetching_songs)
            )

            playState.connectionFailed -> StatusScreen(
                title = stringResource(R.string.error_title),
                body = stringResource(R.string.player_unavailable),
                icon = R.drawable.marketeq__caution,
                primaryLabel = stringResource(R.string.retry),
                onPrimary = { playingViewModel.initializeMediaController(context) }
            )
        }

        songMenu?.let { request ->
            // Rows rebuilt from playlists lack the artist ID; the live library has it.
            val librarySong = remember(request.song.id) { viewModel.loadSongsByID(request.song.id) ?: request.song }
            SongActionsSheet(
                request = request,
                isFavourite = favourites.any { it.song == request.song.id },
                playlists = playlists,
                handlers = SongActionHandlers(
                    playNext = { playingViewModel.playNext(it); showMessage(context.getString(R.string.playing_next, it.title)) },
                    addToQueue = { playingViewModel.addToQueue(it); showMessage(context.getString(R.string.added_to_queue, it.title)) },
                    toggleFavourite = { viewModel.toggleFavourite(it) },
                    addToPlaylist = { playlistId, song ->
                        viewModel.addToPlaylist(playlistId, listOf(song))
                        val name = playlists.firstOrNull { it.id == playlistId }?.name.orEmpty()
                        showMessage(context.getString(R.string.added_to_playlist, name))
                    },
                    createPlaylistWith = { name, song ->
                        viewModel.createPlaylistWithSongs(name, listOf(song))
                        showMessage(context.getString(R.string.added_to_playlist, name))
                    },
                    goToAlbum = if (librarySong.albumId > 0) ({ navigate(Route.Album(librarySong.albumId)) }) else null,
                    goToArtist = if (librarySong.artistId > 0) ({ navigate(Route.Artist(librarySong.artistId)) }) else null,
                    editTags = if (TagEditorRepository.isSupportedDevice &&
                        fileExtension(librarySong.path.ifEmpty { librarySong.displayName }) in EDITABLE_EXTENSIONS
                    ) ({ navigate(Route.TagEditor(librarySong.id)) }) else null,
                ),
                onDismiss = { songMenu = null }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Keep the message above the mini player (tablets have none).
                .padding(bottom = if (tablet) 24.dp else 96.dp)
        )
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
}

/** The tablet's navigation rail: Search, then the library tabs. */
@Composable
private fun TabletRail(selectedTab: Int, onSelect: (Int) -> Unit, onSearch: () -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            FloatingActionButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = onSearch,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ms_search),
                    contentDescription = stringResource(R.string.search)
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.weight(1f))
        HomeDestination.entries.forEachIndexed { index, destination ->
            val selected = selectedTab == index
            NavigationRailItem(
                modifier = Modifier.testTag("rail_${destination.name.lowercase()}"),
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = ImageVector.vectorResource(if (selected) destination.selectedIcon else destination.icon),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
