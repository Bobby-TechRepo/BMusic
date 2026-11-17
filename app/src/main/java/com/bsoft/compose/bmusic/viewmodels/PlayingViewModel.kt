package com.bsoft.compose.bmusic.viewmodels

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.models.MediaId
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.playback.SleepTimer
import com.bsoft.compose.bmusic.data.playback.SleepTimerState
import com.bsoft.compose.bmusic.data.repositories.FavouriteRepository
import com.bsoft.compose.bmusic.data.repositories.PlayerCounterRepository
import com.bsoft.compose.bmusic.data.states.PlayingState
import com.bsoft.compose.bmusic.data.states.QueueState
import com.bsoft.compose.bmusic.services.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayingViewModel @Inject constructor(
    /** Read-only here: only the playback service changes the queue. */
    private val queueManager: QueueManager,
    private val playerCounterRepository: PlayerCounterRepository,
    private val favouriteRepository: FavouriteRepository,
    /** Shared with the playback service, which stops playback when it runs out. */
    private val sleepTimer: SleepTimer
) : ViewModel() {

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    /** Milliseconds left on a sleep countdown, or null. */
    fun sleepTimerRemainingMs(): Long? = sleepTimer.remainingMs()

    fun startSleepTimer(minutes: Int) = sleepTimer.start(minutes)
    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)
    fun sleepAfterCurrentTrack() = sleepTimer.stopAfterCurrentTrack()
    fun cancelSleepTimer() = sleepTimer.cancel()

    fun setPlaybackSpeed(speed: Float) {
        mediaBrowser?.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    private val mutableState = MutableStateFlow(PlayingState())
    val state = mutableState.asStateFlow()

    val isCurrentTrackFavorite: Flow<Boolean> = combine(
        queueManager.state,
        favouriteRepository.getAllFavorites()
    ) { queueState, favorites ->
        val currentId = queueState.current?.id
        favorites.any { it.song == currentId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val getLastPlayed = playerCounterRepository.getLastPlayed().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    /** Every play count, for the Rediscover and Never played lists. */
    val allPlayCounts = playerCounterRepository.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val getMostPlayed = playerCounterRepository.getMostPlayed().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val queueState = queueManager.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = QueueState()
    )

    private var mediaBrowser: MediaBrowser? = null

    /**
     * Emits the title of a track that could not be played (or null when unknown). The service
     * skips the track; the UI shows a short message.
     */
    private val _playbackErrors = MutableSharedFlow<String?>(extraBufferCapacity = 4)
    val playbackErrors: SharedFlow<String?> = _playbackErrors.asSharedFlow()

    private var connecting = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaBrowser?.let { player ->
                mutableState.update { it.copy(position = player.currentPosition, playing = player.isPlaying) }
                if (player.isPlaying) handler.postDelayed(this, 200)
            }
        }
    }

    val playerListener = object : Player.Listener {
        // Triggered when the player starts buffering, becomes ready, or ends
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> mutableState.update { it.copy(buffering = true) }
                else -> mutableState.update { it.copy(buffering = false) }
            }
        }

        // Triggered when play/pause changes
        override fun onIsPlayingChanged(playing: Boolean) {
            mutableState.update { it.copy(playing = playing) }
            if (playing) {
                handler.post(updateProgressRunnable)
            } else {
                handler.removeCallbacks(updateProgressRunnable)
            }
        }

        // Triggered when a critical playback error occurs
        override fun onPlayerError(error: PlaybackException) {
            // PlaybackService skips unplayable tracks; tell the user why the song changed.
            Log.w("PlayingViewModel", "Playback error ${error.errorCode}", error)
            _playbackErrors.tryEmit(queueManager.state.value.current?.title)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            mutableState.update { it.copy(speed = playbackParameters.speed) }
        }

        // A seek while paused must move the progress bar too.
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) {
            mutableState.update { it.copy(position = newPosition.positionMs) }
        }

        // Triggered when moving to a new song/video in the playlist
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Logic moved to PlaybackService to ensure it runs even when UI is not active
            // and to handle reactive queue refreshes for dynamic playlists.
        }


    }

    fun toggleFavorite() {
        queueManager.state.value.current?.let { song ->
            viewModelScope.launch {
                favouriteRepository.toggleFavourite(song)
            }
        }
    }

    fun initializeMediaController(context: Context) {
        if (mediaBrowser != null || connecting) return
        connecting = true
        mutableState.update { it.copy(connectionFailed = false) }
        connect(context.applicationContext, attempt = 1)
    }

    private fun connect(appContext: Context, attempt: Int) {
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaBrowser.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val browser = controllerFuture.get()
                mediaBrowser = browser
                browser.addListener(playerListener)
                connecting = false
                // The service may already be playing (app reopened mid-song): sync the UI now,
                // because no isPlaying change will arrive for a state that already holds.
                // PlaybackService restores the persisted queue itself; do not set items here.
                mutableState.update {
                    it.copy(
                        connected = true,
                        connectionFailed = false,
                        playing = browser.isPlaying,
                        position = browser.currentPosition,
                        buffering = browser.playbackState == Player.STATE_BUFFERING,
                        speed = browser.playbackParameters.speed
                    )
                }
                if (browser.isPlaying) handler.post(updateProgressRunnable)
            } catch (e: Exception) {
                Log.w("PlayingViewModel", "MediaBrowser connection failed (attempt $attempt)", e)
                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    viewModelScope.launch {
                        delay(CONNECT_RETRY_DELAY_MS * attempt)
                        connect(appContext, attempt + 1)
                    }
                } else {
                    connecting = false
                    mutableState.update { it.copy(connected = false, connectionFailed = true) }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    /** Plays the whole library starting at [song]. Located by ID, so list order can't misfire. */
    fun playSong(song: Song, index: Int) {
        val item = MediaItem.Builder()
            .setMediaId(MediaId.Entry(MediaSource.Songs, index, song.id).encode())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setIsBrowsable(false).setIsPlayable(true).build())
            .build()
        playLibrary(item)
    }

    /** Plays a whole source (album, artist, ...) from the given entry, keeping the shuffle mode. */
    fun playLibrary(mediaItem: MediaItem) {
        mediaBrowser?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    /** "Play all" / "Shuffle" for a source. The user's repeat mode is left alone. */
    fun playLibraryAll(mediaItem: MediaItem, shuffle: Boolean) {
        mediaBrowser?.apply {
            setMediaItem(mediaItem)
            // Standard Media3 shuffle: the service keeps the chosen song first and shuffles the rest.
            shuffleModeEnabled = shuffle
            prepare()
            play()
        }
    }

    /**
     * Play a supplied source list from the tapped song. [shuffle] sets the shuffle mode;
     * null keeps the current one (a plain tap on a song).
     */
    fun playLibraryList(songs: List<Song>, startIndex: Int, shuffle: Boolean?, contextPrefix: String = MediaSource.Custom.prefix) {
        if (songs.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(songs.indices)
        val source = MediaSource.fromPrefix(contextPrefix) ?: MediaSource.Custom
        val mediaItems = songs.mapIndexed { index, song ->
            song.toMediaItem().buildUpon()
                .setMediaId(MediaId.Entry(source, index, song.id).encode())
                .build()
        }
        mediaBrowser?.let { browser ->
            browser.setMediaItems(mediaItems, safeStartIndex, 0L)
            shuffle?.let { browser.shuffleModeEnabled = it }
            browser.prepare()
            browser.play()
        }
    }

    /** Plays one file from another app ("Open with BMusic") on its own. */
    fun playItem(item: MediaItem) {
        mediaBrowser?.apply {
            setMediaItems(listOf(item), 0, 0L)
            prepare()
            play()
        }
    }

    fun playPlaylistIndex(index: Int) {
        mediaBrowser?.apply {
            seekToDefaultPosition(index)
            play()
        }
    }

    // Queue edits are plain player commands. The service's BMusicPlayer applies them to the
    // player and the queue model together, so the UI never edits QueueManager itself.

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaBrowser?.let { browser ->
            if (fromIndex !in 0 until browser.mediaItemCount || toIndex !in 0 until browser.mediaItemCount || fromIndex == toIndex) return
            browser.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun removeQueueItem(index: Int) {
        mediaBrowser?.let { browser ->
            if (index !in 0 until browser.mediaItemCount) return
            browser.removeMediaItem(index)
        }
    }

    /** Add a song immediately after the current track. */
    fun playNext(song: Song) {
        mediaBrowser?.let { browser ->
            val item = song.toMediaItem().buildUpon()
                .setMediaId(MediaId.Entry(MediaSource.Queued(System.nanoTime().toString()), 0, song.id).encode())
                .build()
            val index = (browser.currentMediaItemIndex + 1).coerceIn(0, browser.mediaItemCount)
            browser.addMediaItem(index, item)
        }
    }

    /** Adds a song to the end of the queue. */
    fun addToQueue(song: Song) {
        mediaBrowser?.let { browser ->
            val item = song.toMediaItem().buildUpon()
                .setMediaId(MediaId.Entry(MediaSource.Queued(System.nanoTime().toString()), 0, song.id).encode())
                .build()
            browser.addMediaItem(item)
        }
    }

    fun clearQueue() {
        mediaBrowser?.clearMediaItems()
    }

    fun togglePlayPause() {
        mediaBrowser?.apply {
            if (isPlaying) pause() else play()
        }
    }

    fun next() {
        mediaBrowser?.apply {
            if(hasNextMediaItem()){
                seekToNextMediaItem()
            }
        }
    }

    fun forward(){
        mediaBrowser?.let { player -> player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))) }
    }

    /** Restarts the current track after its first seconds, otherwise goes to the previous one. */
    fun previous() {
        mediaBrowser?.seekToPrevious()
    }

    fun rewind(){
        mediaBrowser?.let { player -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }
    }

    fun seek(position: Long){
        mediaBrowser?.seekTo(position)
    }

    fun toggleRepeat(){
        mediaBrowser?.let { player ->
            if(player.availableCommands.contains(Player.COMMAND_SET_REPEAT_MODE)){
                when(player.repeatMode){
                    Player.REPEAT_MODE_ALL -> {
                        player.repeatMode = Player.REPEAT_MODE_ONE
                    }
                    Player.REPEAT_MODE_ONE -> {
                        player.repeatMode = Player.REPEAT_MODE_OFF
                    }
                    Player.REPEAT_MODE_OFF -> {
                        player.repeatMode = Player.REPEAT_MODE_ALL
                    }
                }
            }
        }
    }

    fun toggleShuffle() {
        mediaBrowser?.let { browser ->
            browser.shuffleModeEnabled = !queueManager.state.value.shuffle
        }
    }

    override fun onCleared() {
        handler.removeCallbacks(updateProgressRunnable)
        try {
            mediaBrowser?.removeListener(playerListener)
            mediaBrowser?.release()
        } catch (e: Exception) {
            // Ignore release errors during cleanup
        }
        mediaBrowser = null
    }

    private companion object {
        const val MAX_CONNECT_ATTEMPTS = 3
        const val CONNECT_RETRY_DELAY_MS = 1_000L
    }
}