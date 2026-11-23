package com.bsoft.compose.bmusic.ui.screens

import android.graphics.Bitmap
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.states.QueueState
import com.bsoft.compose.bmusic.ui.components.BitmapImage
import com.bsoft.compose.bmusic.ui.components.EqualizerView
import com.bsoft.compose.bmusic.data.playback.SleepTimerState
import com.bsoft.compose.bmusic.ui.components.LyricsView
import com.bsoft.compose.bmusic.ui.components.PlaybackOptionsSheet
import com.bsoft.compose.bmusic.ui.components.PlayerControl
import com.bsoft.compose.bmusic.ui.components.PlayingPage
import com.bsoft.compose.bmusic.ui.components.PlayingTab
import com.bsoft.compose.bmusic.ui.components.PlayingBars
import com.bsoft.compose.bmusic.ui.components.QueueSongView
import com.bsoft.compose.bmusic.ui.components.TransparentStatusBarHandler
import com.bsoft.compose.bmusic.ui.components.totalDurationText
import com.bsoft.compose.bmusic.utils.Util
import com.bsoft.compose.bmusic.viewmodels.EqualizerViewModel
import com.bsoft.compose.bmusic.viewmodels.LyricsViewModel
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.recordable.ReorderableItem
import com.bsoft.compose.recordable.ReorderableLazyListState
import com.bsoft.compose.recordable.detectReorder
import com.bsoft.compose.recordable.rememberReorderableLazyListState
import com.bsoft.compose.recordable.reorderable
import kotlinx.coroutines.launch
import com.bsoft.compose.bmusic.ui.components.TestTags
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun PlayingScreen(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    playingViewModel: PlayingViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
    /** Saves the current queue as a new playlist with the given name. */
    onSaveQueueAsPlaylist: (name: String, songs: List<Song>) -> Unit = { _, _ -> },
    /**
     * Tablet layout: the player is always on screen (no back arrow), shows a welcome card when
     * nothing is playing, and carries the Stats and Settings buttons.
     */
    persistent: Boolean = false,
    onOpenStats: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
    onBrowseSongs: (() -> Unit)? = null,
    back: () -> Unit
) {
    // Light status-bar icons over the artwork; on tablets the bar is shared with the library.
    if (!persistent) TransparentStatusBarHandler()

    val coroutineScope = rememberCoroutineScope()
    val playState by playingViewModel.state.collectAsStateWithLifecycle()
    val queueState by playingViewModel.queueState.collectAsStateWithLifecycle()
    val isFavorite by playingViewModel.isCurrentTrackFavorite.collectAsStateWithLifecycle(initialValue = false)

    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(queueState.current) {
        bitmap = Util.loadArtwork(context, queueState.current?.mediaUri, Size(400, 400))
    }

    val isEqualizerEnabled by equalizerViewModel.isEqualizerEnabled.collectAsStateWithLifecycle()
    val equalizerState by equalizerViewModel.state.collectAsStateWithLifecycle()
    val selectedBand by equalizerViewModel.selected.collectAsStateWithLifecycle()
    val customValues by equalizerViewModel.bandValues.collectAsStateWithLifecycle()
    val audioEffects by equalizerViewModel.effects.collectAsStateWithLifecycle()

    val recordableState = rememberReorderableLazyListState(onMove = { from, to ->
        // LazyColumn has a header before the queue list; translate item positions to queue indices.
        val fromIndex = from.index - 0
        val toIndex = to.index - 0
        playingViewModel.moveQueueItem(fromIndex, toIndex)
    })

    val isMobile = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lyricsViewModel.import(it) }
    }
    LaunchedEffect(lyricsState.message) {
        lyricsState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            lyricsViewModel.dismissMessage()
        }
    }
    val lyricsPage: @Composable (Modifier) -> Unit = { pageModifier ->
        LyricsView(
            modifier = pageModifier,
            state = lyricsState,
            positionMs = playState.position,
            onSeek = { playingViewModel.seek(it) },
            // .lrc files have no registered MIME type, so allow any file.
            onImport = { lyricsPicker.launch(arrayOf("*/*")) },
            onRemoveImported = { lyricsViewModel.removeImported() }
        )
    }

    val sleepState by playingViewModel.sleepTimerState.collectAsStateWithLifecycle()
    var showPlaybackOptions by remember { mutableStateOf(false) }
    if (showPlaybackOptions) {
        PlaybackOptionsSheet(
            sleepState = sleepState,
            remainingMs = { playingViewModel.sleepTimerRemainingMs() },
            speed = playState.speed,
            onStartSleep = { playingViewModel.startSleepTimer(it) },
            onSleepAfterTrack = { playingViewModel.sleepAfterCurrentTrack() },
            onExtendSleep = { playingViewModel.extendSleepTimer(it) },
            onCancelSleep = { playingViewModel.cancelSleepTimer() },
            onSpeed = { playingViewModel.setPlaybackSpeed(it) },
            onDismiss = { showPlaybackOptions = false }
        )
    }

    Surface(modifier = modifier) {
        if (bitmap == null) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 16.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                painter = painterResource(id = R.drawable.lady),
                contentScale = ContentScale.Crop,
                contentDescription = null
            )
        } else {
            BitmapImage(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 16.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                bitmap = bitmap as Bitmap,
                contentScale = ContentScale.Crop
            )
        }
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black.copy(alpha = 0.3f)
        ) { screenPadding ->
            if (isMobile) {
                // Mobile Layout: Tabbed Pager
                val pagerState = rememberPagerState(initialPage = 1, pageCount = { PlayingPage.entries.size })
                Column(modifier = Modifier.padding(screenPadding).fillMaxSize()) {
                    Box(modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                        PlayingTab(
                            // Leave room for the back button.
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp),
                            active = pagerState.currentPage
                        ) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(it)
                            }
                        }
                        IconButton(
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = { back() }
                        ) {
                            Icon(
                                tint = Color.White,
                                imageVector = ImageVector.vectorResource(R.drawable.material_symbols__arrow_back_ios_new_rounded),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                    HorizontalPager(modifier = Modifier.weight(1f), state = pagerState) { currentPage ->
                        when (PlayingPage.entries[currentPage]) {
                            PlayingPage.Queue -> {
                                QueueView(queueState, recordableState, playingViewModel, onSaveQueueAsPlaylist, bitmap, playState.playing)
                            }
                            PlayingPage.Playing -> {
                                PlayerControl(
                                    playingState = playState,
                                    queueState = queueState,
                                    bitmap = bitmap,
                                    isFavorite = isFavorite,
                                    next = { playingViewModel.next() },
                                    previous = { playingViewModel.previous() },
                                    playToggled = { playingViewModel.togglePlayPause() },
                                    forward = { playingViewModel.forward() },
                                    rewind = { playingViewModel.rewind() },
                                    seek = { playingViewModel.seek(it) },
                                    repeatToggled = { playingViewModel.toggleRepeat() },
                                    shuffleToggled = { playingViewModel.toggleShuffle() },
                                    favouriteToggled = { playingViewModel.toggleFavorite() },
                                    sleepTimerActive = sleepState != SleepTimerState.Off,
                                    openPlaybackOptions = { showPlaybackOptions = true }
                                )
                            }
                            PlayingPage.Lyrics -> lyricsPage(Modifier)
                            PlayingPage.Equalizer -> {
                                EqualizerView(
                                    enabled = isEqualizerEnabled,
                                    state = equalizerState,
                                    selectedBand = selectedBand,
                                    customValues = customValues,
                                    selectedPreset = { equalizerViewModel.choosePreset(it.preset) },
                                    bandModified = { band, level -> equalizerViewModel.modifyBand(band, level) },
                                    effects = audioEffects,
                                    onEffectsChange = { settings, persist -> equalizerViewModel.setEffects(settings, persist) },
                                    toggleEnable = { equalizerViewModel.toggleEnable() }
                                )
                            }
                        }
                    }
                }
            } else {
                // Large screens: the player fills its section. Queue, lyrics and the equalizer
                // live in one panel that slides over its right side, opened and closed with a
                // single button.
                var panelOpen by rememberSaveable { mutableStateOf(false) }
                var panelTab by rememberSaveable { mutableIntStateOf(0) }
                androidx.activity.compose.BackHandler(enabled = panelOpen) { panelOpen = false }

                BoxWithConstraints(modifier = Modifier.padding(screenPadding).fillMaxSize()) {
                    // The panel takes a bit over half the player: wide enough for full queue rows
                    // and the equalizer, while the artwork still shows beside it.
                    val panelWidth = (maxWidth * 0.58f).coerceIn(380.dp, 560.dp).coerceAtMost(maxWidth - 24.dp)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!persistent) {
                                IconButton(onClick = { back() }) {
                                    Icon(
                                        tint = Color.White,
                                        imageVector = ImageVector.vectorResource(R.drawable.material_symbols__arrow_back_ios_new_rounded),
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            }
                            Text(
                                modifier = Modifier.weight(1f).padding(start = if (persistent) 8.dp else 0.dp).semantics { heading() },
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            PlayerHeaderButton(
                                icon = R.drawable.ms_queue_music,
                                label = stringResource(R.string.cd_queue_panel),
                                active = panelOpen,
                                onClick = { panelOpen = !panelOpen }
                            )
                            onOpenStats?.let { PlayerHeaderButton(R.drawable.ms_bar_chart, stringResource(R.string.stats_title), onClick = it) }
                            onOpenSettings?.let { PlayerHeaderButton(R.drawable.ms_settings, stringResource(R.string.settings), onClick = it) }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (persistent && queueState.current == null) {
                                PlayerWelcome(onShuffleAll = onShuffleAll, onBrowse = onBrowseSongs)
                            } else {
                                Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxHeight()) {
                                    PlayerControl(
                                        playingState = playState,
                                        queueState = queueState,
                                        bitmap = bitmap,
                                        isFavorite = isFavorite,
                                        next = { playingViewModel.next() },
                                        previous = { playingViewModel.previous() },
                                        playToggled = { playingViewModel.togglePlayPause() },
                                        forward = { playingViewModel.forward() },
                                        rewind = { playingViewModel.rewind() },
                                        seek = { playingViewModel.seek(it) },
                                        repeatToggled = { playingViewModel.toggleRepeat() },
                                        shuffleToggled = { playingViewModel.toggleShuffle() },
                                        favouriteToggled = { playingViewModel.toggleFavorite() },
                                        sleepTimerActive = sleepState != SleepTimerState.Off,
                                        openPlaybackOptions = { showPlaybackOptions = true }
                                    )
                                }
                            }
                        }
                    }

                    // The overlay panel: Queue | Lyrics | Equalizer.
                    androidx.compose.animation.AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        visible = panelOpen,
                        enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, top = 76.dp, bottom = 12.dp)
                                .width(panelWidth)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xF0141519),
                            shadowElevation = 16.dp
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                SecondaryTabRow(
                                    selectedTabIndex = panelTab,
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                    divider = {}
                                ) {
                                    listOf(R.string.queue, R.string.lyrics, R.string.equalizer).forEachIndexed { index, label ->
                                        Tab(
                                            selected = panelTab == index,
                                            onClick = { panelTab = index },
                                            text = { Text(stringResource(label), color = Color.White) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (panelTab) {
                                        0 -> QueueView(queueState, recordableState, playingViewModel, onSaveQueueAsPlaylist, bitmap, playState.playing)
                                        1 -> lyricsPage(Modifier.fillMaxSize())
                                        else -> Column(
                                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        ) {
                                            EqualizerView(
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = isEqualizerEnabled,
                                                state = equalizerState,
                                                selectedBand = selectedBand,
                                                customValues = customValues,
                                                selectedPreset = { equalizerViewModel.choosePreset(it.preset) },
                                                bandModified = { band, level -> equalizerViewModel.modifyBand(band, level) },
                                                effects = audioEffects,
                                                onEffectsChange = { settings, persist -> equalizerViewModel.setEffects(settings, persist) },
                                                toggleEnable = { equalizerViewModel.toggleEnable() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A round, translucent button in the large-screen player's header; [active] fills it. */
@Composable
private fun PlayerHeaderButton(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    FilledTonalIconButton(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) Color.White else Color.White.copy(alpha = 0.14f),
            contentColor = if (active) Color.Black else Color.White
        )
    ) {
        Icon(imageVector = ImageVector.vectorResource(icon), contentDescription = label)
    }
}

/** The large-screen player before anything has played. */
@Composable
private fun PlayerWelcome(onShuffleAll: (() -> Unit)?, onBrowse: (() -> Unit)?) {
    Column(
        modifier = Modifier.padding(32.dp).widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(88.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_music_note),
                    tint = Color.White.copy(alpha = 0.8f),
                    contentDescription = null
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.player_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            onShuffleAll?.let {
                Button(
                    onClick = it,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_shuffle), contentDescription = null)
                    Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.shuffle_all))
                }
            }
            onBrowse?.let {
                OutlinedButton(onClick = it, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text(stringResource(R.string.player_welcome_browse))
                }
            }
        }
    }
}

/** A round, translucent icon button in the queue header. */
@Composable
private fun QueueAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        enabled = enabled,
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        )
    ) {
        Icon(imageVector = ImageVector.vectorResource(icon), contentDescription = label)
    }
}

@Composable
private fun QueueView(
    queueState: QueueState,
    recordableState: ReorderableLazyListState,
    playingViewModel: PlayingViewModel,
    onSaveQueueAsPlaylist: (name: String, songs: List<Song>) -> Unit,
    bitmap: Bitmap? = null,
    playing: Boolean = false
) {
    var savingAsPlaylist by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    val count = queueState.queue.size
    val currentIndex = queueState.currentIndex ?: -1
    val current = queueState.current
    // "Up next": the songs after the current one, and how long they last.
    val upcoming = remember(queueState.queue, currentIndex) {
        queueState.queue.drop(currentIndex + 1).map { it.mediaMetadata.durationMs ?: 0L }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        if (current != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.10f)) {
                        if (bitmap != null) {
                            BitmapImage(bitmap = bitmap, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ms_music_note),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.now_playing).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inversePrimary
                        )
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = current.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    PlayingBars(
                        modifier = Modifier.padding(end = 6.dp),
                        color = MaterialTheme.colorScheme.inversePrimary,
                        animate = playing
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(R.string.queue_up_next),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = if (count == 0) pluralStringResource(R.plurals.queue_count, 0, 0) else stringResource(
                        R.string.song_row_subtitle,
                        pluralStringResource(R.plurals.search_songs_count, upcoming.size, upcoming.size),
                        totalDurationText(upcoming.sum())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
            QueueAction(R.drawable.ms_playlist_add, stringResource(R.string.save_as_playlist), enabled = count > 0) {
                savingAsPlaylist = true
            }
            QueueAction(R.drawable.ms_delete, stringResource(R.string.clear_queue), enabled = count > 0) {
                confirmingClear = true
            }
        }
        LazyColumn(
            state = recordableState.listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(TestTags.QUEUE_LIST)
                .reorderable(recordableState),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(queueState.queue, key = { _, item -> item.mediaId }) { index, item ->
                // Converted once per item, not on every recomposition of the queue.
                val song = remember(item) { Song.fromMediaItem(item) }
                ReorderableItem(recordableState, key = item.mediaId) { isDragging ->
                    QueueSongView(
                        modifier = Modifier.fillMaxWidth(),
                        movable = true,
                        moving = isDragging,
                        dragHandleModifier = Modifier.detectReorder(recordableState),
                        currentPlaying = index == queueState.currentIndex,
                        playing = playing,
                        dimmed = index < currentIndex,
                        song = song,
                        onRemove = { playingViewModel.removeQueueItem(index) }
                    ) {
                        playingViewModel.playPlaylistIndex(index)
                    }
                }
            }
        }
    }

    if (savingAsPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingAsPlaylist = false },
            title = { Text(stringResource(R.string.save_as_playlist)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    onSaveQueueAsPlaylist(name.trim(), queueState.queue.map(Song::fromMediaItem))
                    savingAsPlaylist = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { savingAsPlaylist = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_queue)) },
            text = { Text(stringResource(R.string.clear_queue_message)) },
            confirmButton = {
                TextButton(onClick = { playingViewModel.clearQueue(); confirmingClear = false }) {
                    Text(stringResource(R.string.clear_queue))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
