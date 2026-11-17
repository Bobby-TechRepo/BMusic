package com.bsoft.compose.bmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.SmartPlaylists
import com.bsoft.compose.bmusic.data.models.Song
import kotlinx.coroutines.flow.combine
import com.bsoft.compose.bmusic.ui.components.DetailBackdrop
import com.bsoft.compose.bmusic.ui.components.DetailBarAction
import com.bsoft.compose.bmusic.ui.components.DetailHeader
import com.bsoft.compose.bmusic.ui.components.DetailTopBar
import com.bsoft.compose.bmusic.ui.components.MessageToast
import com.bsoft.compose.bmusic.ui.components.PlaylistArtwork
import com.bsoft.compose.bmusic.ui.components.PlaylistMenu
import com.bsoft.compose.bmusic.ui.components.QueueSongView
import com.bsoft.compose.bmusic.ui.components.SongMenuRequest
import com.bsoft.compose.bmusic.ui.components.TransparentStatusBarHandler
import com.bsoft.compose.bmusic.ui.components.alerts.PlaylistDeleteAlertDialog
import com.bsoft.compose.bmusic.ui.components.detailTopInset
import com.bsoft.compose.bmusic.ui.components.headerScrolledAway
import com.bsoft.compose.bmusic.ui.components.headerThresholdPx
import com.bsoft.compose.bmusic.ui.components.totalDurationText
import com.bsoft.compose.bmusic.ui.pages.PlaylistsOptions
import com.bsoft.compose.bmusic.utils.ScreenOverlay
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.PlaylistTransferViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import com.bsoft.compose.recordable.ReorderableItem
import com.bsoft.compose.recordable.detectReorder
import com.bsoft.compose.recordable.rememberReorderableLazyListState
import com.bsoft.compose.recordable.reorderable
import com.bsoft.compose.bmusic.ui.components.TestTags

private data class PlaylistScreenState(
    val songs: List<Song> = emptyList(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val title: String = "",
    val titleRes: Int? = null,
    val bg: Int
)

@Composable
private fun SelectionMenuItem(@DrawableRes icon: Int, label: String, enabled: Boolean = true, clicked: () -> Unit) {
    val tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button) { clicked() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(imageVector = ImageVector.vectorResource(icon), tint = tint, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/**
 * A built-in list (Favourites, Most Played, Recently Played, Recently Added) or a custom
 * playlist ([id]). Long-press a song to select several; custom playlists can also be reordered
 * by long-press dragging while editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    modifier: Modifier = Modifier, playlistsOptions: PlaylistsOptions? = null, id: Long? = null,
    playingViewModel: PlayingViewModel = hiltViewModel(), songsViewModel: SongsViewModel = hiltViewModel(),
    openSongMenu: (SongMenuRequest) -> Unit = {},
    transferViewModel: PlaylistTransferViewModel = hiltViewModel(),
    add: (Long, String) -> Unit, back: () -> Unit
) {
    TransparentStatusBarHandler()

    val transferMessage by transferViewModel.message.collectAsStateWithLifecycle()
    MessageToast(transferMessage) { transferViewModel.dismissMessage() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null && id != null) transferViewModel.export(id, uri)
    }

    var state by remember { mutableStateOf(PlaylistScreenState(bg = R.drawable.playlist_bg)) }
    var editMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val queueState by playingViewModel.queueState.collectAsStateWithLifecycle()
    val playingState by playingViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(playlistsOptions, id) {
        when (playlistsOptions) {
            PlaylistsOptions.MostPlayed -> {
                playingViewModel.getMostPlayed.collect { list ->
                    state = state.copy(
                        songs = list.mapNotNull { songsViewModel.loadSongsByID(it.song) },
                        playCounts = list.associate { it.song to it.played },
                        titleRes = playlistsOptions.titleRes,
                        bg = playlistsOptions.bg
                    )
                }
            }
            PlaylistsOptions.RecentlyAdded -> {
                songsViewModel.state.collect { songsState ->
                    state = state.copy(songs = songsState.last, titleRes = playlistsOptions.titleRes, bg = playlistsOptions.bg)
                }
            }
            PlaylistsOptions.RecentlyPlayed -> {
                playingViewModel.getLastPlayed.collect { list ->
                    state = state.copy(
                        songs = list.mapNotNull { songsViewModel.loadSongsByID(it.song) },
                        titleRes = playlistsOptions.titleRes,
                        bg = playlistsOptions.bg
                    )
                }
            }
            PlaylistsOptions.Rediscover -> {
                playingViewModel.allPlayCounts.collect { counts ->
                    state = state.copy(
                        songs = SmartPlaylists.rediscover(counts, System.currentTimeMillis())
                            .mapNotNull { songsViewModel.loadSongsByID(it) },
                        playCounts = counts.associate { it.song to it.played },
                        titleRes = playlistsOptions.titleRes,
                        bg = playlistsOptions.bg
                    )
                }
            }
            PlaylistsOptions.NeverPlayed -> {
                combine(songsViewModel.state, playingViewModel.allPlayCounts) { songsState, counts ->
                    SmartPlaylists.neverPlayed(songsState.songs, counts)
                }.collect { songs ->
                    state = state.copy(songs = songs, titleRes = playlistsOptions.titleRes, bg = playlistsOptions.bg)
                }
            }
            PlaylistsOptions.Favourites -> {
                songsViewModel.favourites.collect { list ->
                    state = state.copy(
                        songs = list.mapNotNull { songsViewModel.loadSongsByID(it.song) },
                        titleRes = playlistsOptions.titleRes,
                        bg = playlistsOptions.bg
                    )
                }
            }
            else -> {
                id?.let {
                    val playlist = songsViewModel.playlistRepository.getPlayList(it)
                    state = state.copy(
                        title = playlist.name,
                        // Prefer the live library entry (fresh tags, artist ID); fall back to the saved row.
                        songs = songsViewModel.playlistRepository.getSongsFromPlayList(it)
                            .map { entity -> songsViewModel.loadSongsByID(entity.id) ?: entity.song }
                    )
                }
            }
        }
    }

    val displayTitle = state.titleRes?.let { stringResource(it) } ?: state.title

    var showOverlay by remember { mutableStateOf(ScreenOverlay()) }
    val playlistMenuSheetState = rememberBottomSheetState(
        enabledValues = setOf(SheetValue.Expanded, SheetValue.Hidden),
        initialValue = SheetValue.Expanded
    )

    fun exitEditMode() {
        editMode = false
        selected.clear()
    }
    BackHandler(enabled = editMode) { exitEditMode() }

    val contextPrefix = remember(playlistsOptions, id) {
        when (playlistsOptions) {
            PlaylistsOptions.MostPlayed -> MediaSource.MostPlayed.prefix
            PlaylistsOptions.RecentlyAdded -> MediaSource.RecentlyAdded.prefix
            PlaylistsOptions.RecentlyPlayed -> MediaSource.RecentlyPlayed.prefix
            PlaylistsOptions.Favourites -> MediaSource.Favourites.prefix
            PlaylistsOptions.Rediscover -> MediaSource.Rediscover.prefix
            PlaylistsOptions.NeverPlayed -> MediaSource.NeverPlayed.prefix
            else -> id?.let { MediaSource.Playlist(it).prefix } ?: MediaSource.Custom.prefix
        }
    }
    // Songs can be removed from custom playlists and from Favourites.
    val canRemove = id != null || playlistsOptions == PlaylistsOptions.Favourites
    val canReorder = id != null

    fun removeSongs(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val removed = ids.toSet()
        if (id != null) {
            songsViewModel.removeFromPlaylist(id, removed)
            state = state.copy(songs = state.songs.filterNot { it.id in removed })
        } else if (playlistsOptions == PlaylistsOptions.Favourites) {
            songsViewModel.removeFromFavourites(removed)
        }
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (canReorder && editMode) {
                state = state.copy(songs = state.songs.toMutableList().apply { add(to.index, removeAt(from.index)) })
            }
        },
        onDragEnd = { _, _ ->
            if (canReorder && editMode) songsViewModel.reorderPlaylist(id, state.songs.map { it.id })
        }
    )

    val listState = rememberLazyListState()
    val threshold = headerThresholdPx()
    val collapsed by remember { derivedStateOf { headerScrolledAway(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, threshold) } }
    val playingHere = queueState.queue.getOrNull(queueState.currentIndex ?: -1)?.mediaId?.startsWith("$contextPrefix#") == true
    val addSongs: (() -> Unit)? = when {
        id != null -> ({ add(id, displayTitle) })
        playlistsOptions == PlaylistsOptions.Favourites -> ({ add(-1L, displayTitle) })
        else -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(bitmap = null, fallback = state.bg)

        if (editMode && canReorder) {
            // Editing a custom playlist: tap to select, drag the handle to reorder. No header, so
            // the reorder indices are the song positions.
            LazyColumn(
                state = reorderState.listState,
                modifier = Modifier.fillMaxSize().reorderable(reorderState),
                contentPadding = PaddingValues(top = detailTopInset() + 8.dp, bottom = 120.dp)
            ) {
                itemsIndexed(state.songs, key = { _, song -> song.id }) { _, song ->
                    ReorderableItem(reorderState, key = song.id) { dragging ->
                        QueueSongView(
                            song = song, selectionMode = true, movable = true, moving = dragging,
                            dragHandleModifier = Modifier.detectReorder(reorderState),
                            checked = song.id in selected
                        ) {
                            if (song.id in selected) selected.remove(song.id) else selected.add(song.id)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(TestTags.DETAIL_LIST),
                state = listState,
                contentPadding = PaddingValues(bottom = if (editMode) 120.dp else 24.dp)
            ) {
                item(key = "header") {
                    DetailHeader(
                        artwork = { PlaylistArtwork(songs = state.songs, bg = state.bg) },
                        title = displayTitle,
                        subtitle = null,
                        meta = stringResource(
                            R.string.song_row_subtitle,
                            pluralStringResource(R.plurals.search_songs_count, state.songs.size, state.songs.size),
                            totalDurationText(state.songs.sumOf { it.duration })
                        ),
                        playing = playingHere && playingState.playing,
                        enabled = state.songs.isNotEmpty(),
                        onPlay = {
                            if (playingHere) playingViewModel.togglePlayPause()
                            else if (state.songs.isNotEmpty()) playingViewModel.playLibraryList(state.songs, 0, false, contextPrefix)
                        },
                        onShuffle = {
                            state.songs.indices.randomOrNull()?.let { start ->
                                playingViewModel.playLibraryList(state.songs, start, true, contextPrefix)
                            }
                        }
                    )
                }
                if (state.songs.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                            text = stringResource(R.string.playlist_empty_hint),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    QueueSongView(
                        song = song,
                        showMenu = !editMode,
                        selectionMode = editMode,
                        checked = song.id in selected,
                        currentPlaying = queueState.current?.id == song.id,
                        playing = playingState.playing,
                        playCount = if (playlistsOptions == PlaylistsOptions.MostPlayed || playlistsOptions == PlaylistsOptions.Rediscover) state.playCounts[song.id] else null,
                        menuClicked = {
                            openSongMenu(
                                SongMenuRequest(song, onRemove = if (id != null) ({ removeSongs(listOf(song.id)) }) else null)
                            )
                        },
                        onLongClick = {
                            if (!editMode) {
                                editMode = true
                                selected.clear()
                            }
                            if (song.id !in selected) selected.add(song.id)
                        }
                    ) {
                        if (editMode) {
                            if (song.id in selected) selected.remove(song.id) else selected.add(song.id)
                        } else {
                            // A plain tap keeps the current shuffle mode.
                            playingViewModel.playLibraryList(state.songs, index, null, contextPrefix)
                        }
                    }
                }
            }
        }

        DetailTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = if (editMode) stringResource(R.string.selected_count, selected.size) else displayTitle,
            collapsed = collapsed || editMode,
            onBack = { if (editMode) exitEditMode() else back() }
        ) {
            if (editMode) {
                TextButton(onClick = { exitEditMode() }) { Text(stringResource(R.string.done), color = Color.White) }
            } else {
                addSongs?.let { DetailBarAction(R.drawable.ms_playlist_add, stringResource(R.string.add_songs), it) }
                if (id != null) {
                    DetailBarAction(R.drawable.ms_more_vert, stringResource(R.string.cd_playlist_options)) {
                        showOverlay = ScreenOverlay(item = ScreenOverlay.Item.PlaylistMenu)
                    }
                }
            }
        }

        if (editMode) {
            val chosen = state.songs.filter { it.id in selected }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xF2202227),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectionMenuItem(label = stringResource(R.string.select_all), icon = R.drawable.ms_check_circle_fill1) {
                        selected.clear(); selected.addAll(state.songs.map { it.id })
                    }
                    SelectionMenuItem(label = stringResource(R.string.play), icon = R.drawable.ms_play_arrow_fill1, enabled = chosen.isNotEmpty()) {
                        playingViewModel.playLibraryList(chosen, 0, false, MediaSource.Custom.prefix)
                        exitEditMode()
                    }
                    SelectionMenuItem(label = stringResource(R.string.add_to_queue), icon = R.drawable.ms_playlist_play, enabled = chosen.isNotEmpty()) {
                        chosen.forEach { playingViewModel.addToQueue(it) }
                        exitEditMode()
                    }
                    if (canRemove) {
                        SelectionMenuItem(label = stringResource(R.string.remove), icon = R.drawable.ms_delete, enabled = chosen.isNotEmpty()) {
                            removeSongs(chosen.map { it.id })
                            exitEditMode()
                        }
                    }
                }
            }
        }
    }

    // Dialogs and sheets.
    run {
        if (showRenameDialog && id != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(stringResource(R.string.rename_playlist)) },
                text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) }) },
                confirmButton = {
                    TextButton(onClick = {
                        songsViewModel.renamePlaylist(id, renameText)
                        state = state.copy(title = renameText.trim().ifEmpty { state.title })
                        showRenameDialog = false
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (showOverlay.item == ScreenOverlay.Item.PlaylistMenu) {
            id?.let { id ->
                ModalBottomSheet(sheetState = playlistMenuSheetState, onDismissRequest = { showOverlay = ScreenOverlay() }) {
                    PlaylistMenu(
                        playlist = id,
                        playlistName = displayTitle,
                        subtitle = pluralStringResource(R.plurals.search_songs_count, state.songs.size, state.songs.size),
                        addSongs = { add(it, displayTitle) },
                        play = { if (state.songs.isNotEmpty()) playingViewModel.playLibraryList(state.songs, 0, false, contextPrefix); showOverlay = ScreenOverlay() },
                        shuffle = {
                            state.songs.indices.randomOrNull()?.let { start -> playingViewModel.playLibraryList(state.songs, start, true, contextPrefix) }
                            showOverlay = ScreenOverlay()
                        },
                        rename = { renameText = displayTitle; showRenameDialog = true; showOverlay = ScreenOverlay() },
                        export = { exportLauncher.launch(transferViewModel.suggestedFileName(displayTitle)); showOverlay = ScreenOverlay() },
                        // "Edit" = select and reorder.
                        edit = { editMode = true; selected.clear(); showOverlay = ScreenOverlay() },
                        delete = { showOverlay = showOverlay.copy(item = ScreenOverlay.Item.PlaylistDeleteDialog) }
                    )
                }
            }
        }

        if (showOverlay.item == ScreenOverlay.Item.PlaylistDeleteDialog) {
            id?.let {
                PlaylistDeleteAlertDialog(
                    playlist = it,
                    playlistName = displayTitle,
                    onConfirmation = { songsViewModel.deletePlaylist(it); showOverlay = ScreenOverlay(); back() },
                    onDismissRequest = { showOverlay = ScreenOverlay() }
                )
            }
        }
    }
}
