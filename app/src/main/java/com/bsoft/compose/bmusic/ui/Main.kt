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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.navigation3.runtime.NavKey
import com.bsoft.compose.bmusic.ui.components.EmptyState
import com.bsoft.compose.bmusic.utils.moveDetailsIntoPane
import com.bsoft.compose.bmusic.utils.movePaneIntoRoot
import com.bsoft.compose.bmusic.utils.openFromHome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.bsoft.compose.bmusic.utils.ShortcutAction
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.bsoft.compose.bmusic.viewmodels.OpenedFile
import com.bsoft.compose.bmusic.viewmodels.OpenedFileViewModel
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(shortcuts) {
        shortcuts.collect { action ->
            when (action) {
                ShortcutAction.PLAY_PAUSE -> playingViewModel.togglePlayPause()
                ShortcutAction.NEXT -> playingViewModel.next()
                ShortcutAction.PREVIOUS -> playingViewModel.previous()
                ShortcutAction.SEEK_FORWARD -> playingViewModel.forward()
                ShortcutAction.SEEK_BACK -> playingViewModel.rewind()
                ShortcutAction.SEARCH -> if (rootBackStack.lastOrNull() != Route.Search) rootBackStack.add(Route.Search)
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
        if (rootBackStack.lastOrNull() != Route.Playing) rootBackStack.add(Route.Playing)
    }

    // Two-pane library on large screens (>= 840 dp wide): see utils/Panes.kt.
    val twoPane = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val pane = rememberNavBackStack()
    val paneStates = rememberSaveableStateHolder()
    LaunchedEffect(twoPane) {
        val isDetail: (NavKey) -> Boolean = { it is Route.Album || it is Route.Artist || it is Route.Playlist }
        val moved = if (twoPane) moveDetailsIntoPane(rootBackStack.toList(), pane.toList(), Route.Home, isDetail)
        else movePaneIntoRoot(rootBackStack.toList(), pane.toList(), Route.Home)
        if (moved.root != rootBackStack.toList()) { rootBackStack.clear(); rootBackStack.addAll(moved.root) }
        if (moved.pane != pane.toList()) { pane.clear(); pane.addAll(moved.pane) }
    }
    /** Opens an album/artist/playlist: in the pane when Home is showing on a large screen. */
    fun showDetail(route: Route) {
        if (twoPane && rootBackStack.lastOrNull() == Route.Home) {
            val next = openFromHome(pane.toList(), route)
            pane.clear(); pane.addAll(next)
        } else {
            rootBackStack.add(route)
        }
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

    // Album, artist and playlist screens: full screen, or in the right pane on large screens.
    val detailContent: @Composable (Route, Boolean) -> Unit = { route, inPane ->
        val push: (Route) -> Unit = { next -> if (inPane) pane.add(next) else rootBackStack.add(next) }
        val goBack: () -> Unit = { if (inPane) { if (pane.isNotEmpty()) pane.removeAt(pane.lastIndex) } else back() }
        when (route) {
                    is Route.Album -> AlbumScreen(
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
                        back = goBack
                    )
                    is Route.Artist -> ArtistScreen(
                        modifier = modifier,
                        id = route.id,
                        viewModel = viewModel,
                        toAlbum = { album -> push(Route.Album(album.id)) },
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
                        back = goBack
                    )
                    is Route.Playlist -> PlaylistScreen(
                        modifier = modifier,
                        playingViewModel = playingViewModel,
                        songsViewModel = viewModel,
                        playlistsOptions = route.playlistsOptions,
                        id = route.playlist,
                        add = { playlist, title ->
                            rootBackStack.add(Route.AddSongs(playlist = playlist, title = title))
                        },
                        openSongMenu = { songMenu = it },
                        back = goBack
                    )
                    else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = rootBackStack,
            // On Home, back leaves the app like any launcher activity (music keeps playing).
            onBack = { if (rootBackStack.size > 1) back() else (context as? Activity)?.moveTaskToBack(true) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(
                    viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
                        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
                    }
                )
            ),
            entryProvider = entryProvider {
                entry<Route.Home> {
                    val homeScreen: @Composable (Modifier) -> Unit = { homeModifier ->
                    HomeScreen(
                        modifier = homeModifier,
                        windowSizeClass = windowSizeClass,
                        viewModel = viewModel,
                        playingViewModel = playingViewModel,
                        toScreen = { rootBackStack.add(it) },
                        toAlbum = { showDetail(Route.Album(it.id)) },
                        toArtist = { showDetail(Route.Artist(it.id)) },
                        toPlaylist = { option, playlist ->
                            showDetail(Route.Playlist(option, playlist = playlist?.first))
                        },
                        toAddSongs = { id, name -> rootBackStack.add(Route.AddSongs(id, name)) },
                        settings = settingsState,
                        onTabChanged = { settingsViewModel.setLastTab(it) },
                        onSongSortChanged = { settingsViewModel.setSongSort(it.name) },
                        openSearch = { rootBackStack.add(Route.Search) },
                        openSongMenu = { songMenu = it }
                    )
                    }
                    if (twoPane) {
                        // Large screens: Home on the left, the open album/artist/playlist on the right.
                        BackHandler(enabled = pane.isNotEmpty()) { pane.removeAt(pane.lastIndex) }
                        Row(modifier = modifier.fillMaxSize()) {
                            homeScreen(Modifier.weight(1f))
                            VerticalDivider()
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val top = pane.lastOrNull() as? Route
                                if (top == null) {
                                    PanePlaceholder()
                                } else {
                                    paneStates.SaveableStateProvider("${pane.size}:$top") { detailContent(top, true) }
                                }
                            }
                        }
                    } else {
                        homeScreen(modifier)
                    }
                }
                entry<Route.Stats> {
                    StatsScreen(back = { back() })
                }
                entry<Route.Search> {
                    SearchScreen(
                        songsViewModel = viewModel,
                        playingViewModel = playingViewModel,
                        openSongMenu = { songMenu = it },
                        toAlbum = { rootBackStack.add(Route.Album(it.id)) },
                        toArtist = { rootBackStack.add(Route.Artist(it.id)) },
                        toPlaylist = { rootBackStack.add(Route.Playlist(null, it.id)) },
                        back = { back() }
                    )
                }
                entry<Route.Album> { detailContent(it, false) }
                entry<Route.Artist> { detailContent(it, false) }
                entry<Route.Playlist> { detailContent(it, false) }
                entry<Route.Settings> {
                    SettingsScreen(modifier = modifier, windowSizeClass = windowSizeClass) { back() }
                }
                entry<Route.Playing> {
                    PlayingScreen(
                        modifier = modifier,
                        windowSizeClass = windowSizeClass,
                        playingViewModel = playingViewModel,
                        equalizerViewModel = equalizerViewModel,
                        onSaveQueueAsPlaylist = { name, songs ->
                            viewModel.createPlaylistWithSongs(name, songs)
                            showMessage(context.getString(R.string.saved_as_playlist, name))
                        },
                        back = { back() }
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
        )

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
                    goToAlbum = if (librarySong.albumId > 0) ({ showDetail(Route.Album(librarySong.albumId)) }) else null,
                    goToArtist = if (librarySong.artistId > 0) ({ showDetail(Route.Artist(librarySong.artistId)) }) else null,
                    editTags = if (TagEditorRepository.isSupportedDevice &&
                        fileExtension(librarySong.path.ifEmpty { librarySong.displayName }) in EDITABLE_EXTENSIONS
                    ) ({ rootBackStack.add(Route.TagEditor(librarySong.id)) }) else null,
                ),
                onDismiss = { songMenu = null }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Keep the message above the mini player.
                .padding(bottom = 96.dp)
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

/** The right pane before anything is opened. */
@Composable
private fun PanePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = R.drawable.ms_library_music,
            title = stringResource(R.string.pane_empty_title),
            message = stringResource(R.string.pane_empty_message)
        )
    }
}

