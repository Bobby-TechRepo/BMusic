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
    back: () -> Unit
) {
    TransparentStatusBarHandler()

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
                // Tablet Layout: Split Pane
                Column(modifier = Modifier.padding(screenPadding).fillMaxSize()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { back() }) {
                            Icon(
                                tint = Color.White,
                                imageVector = ImageVector.vectorResource(R.drawable.material_symbols__arrow_back_ios_new_rounded),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left: Controls
                        Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
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
                        
                        // Right: Toggleable Queue & Equalizer
                        var rightPaneIndex by remember { mutableIntStateOf(0) }
                        
                        Column(modifier = Modifier.weight(0.8f).fillMaxHeight().padding(end = 16.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Black.copy(alpha = 0.4f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SecondaryTabRow(
                                        selectedTabIndex = rightPaneIndex,
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White,
                                        divider = {}
                                    ) {
                                        Tab(
                                            selected = rightPaneIndex == 0,
                                            onClick = { rightPaneIndex = 0 },
                                            text = { Text(stringResource(R.string.queue), color = Color.White) }
                                        )
                                        Tab(
                                            selected = rightPaneIndex == 1,
                                            onClick = { rightPaneIndex = 1 },
                                            text = { Text(stringResource(R.string.lyrics), color = Color.White) }
                                        )
                                        Tab(
                                            selected = rightPaneIndex == 2,
                                            onClick = { rightPaneIndex = 2 },
                                            text = { Text(stringResource(R.string.equalizer), color = Color.White) }
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (rightPaneIndex == 0) {
                                            QueueView(queueState, recordableState, playingViewModel, onSaveQueueAsPlaylist, bitmap, playState.playing)
                                        } else if (rightPaneIndex == 1) {
                                            lyricsPage(Modifier.fillMaxSize())
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
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
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
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
