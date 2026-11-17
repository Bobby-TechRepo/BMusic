package com.bsoft.compose.bmusic.services

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.activities.MainActivity
import com.bsoft.compose.bmusic.data.AudioEffectsManager
import com.bsoft.compose.bmusic.data.EqualizerManager
import com.bsoft.compose.bmusic.data.PlayCountTracker
import com.bsoft.compose.bmusic.data.PlaybackCoordinator
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.models.MediaId
import com.bsoft.compose.bmusic.data.models.FolderTree
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.bsoft.compose.bmusic.data.models.SmartPlaylists
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.playback.PlaybackFileGuard
import com.bsoft.compose.bmusic.data.playback.PlaybackSnapshotStore
import com.bsoft.compose.bmusic.data.playback.SleepTimer
import com.bsoft.compose.bmusic.data.playback.SleepTimerState
import com.bsoft.compose.bmusic.data.playback.CrossfadeContext
import com.bsoft.compose.bmusic.data.playback.TRANSITION_SECONDS_DEFAULT
import com.bsoft.compose.bmusic.data.playback.TransitionMode
import com.bsoft.compose.bmusic.data.playback.crossfadeLengthMs
import com.bsoft.compose.bmusic.data.playback.fadeInMsFor
import com.bsoft.compose.bmusic.data.playback.fadeVolume
import com.bsoft.compose.bmusic.data.playback.isNextTrackOfAlbum
import com.bsoft.compose.bmusic.data.playback.shouldCrossfade
import com.bsoft.compose.bmusic.data.playback.transitionMs
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import com.bsoft.compose.bmusic.data.preferences.EqualizerPreferences
import com.bsoft.compose.bmusic.data.repositories.FavouriteRepository
import com.bsoft.compose.bmusic.data.repositories.ListeningStatsRepository
import com.bsoft.compose.bmusic.data.repositories.PlayerCounterRepository
import com.bsoft.compose.bmusic.data.repositories.PlaylistRepository
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.data.tags.AudioTagRepository
import com.bsoft.compose.bmusic.data.tags.ReplayGainMode
import com.bsoft.compose.bmusic.modules.IoDispatcher
import com.bsoft.compose.bmusic.utils.Util
import com.bsoft.compose.bmusic.widget.NowPlayingWidget
import com.bsoft.compose.bmusic.widget.WidgetContent
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "PlaybackService"

/** How often the service samples playback for play counting and snapshots. */
private const val TICK_MS = 1_000L

/** Position jumps larger than this between ticks are seeks, not listening. */
private const val MAX_CONTINUOUS_STEP_MS = 3 * TICK_MS

/** How often the playback position is saved while playing. */
private const val SNAPSHOT_INTERVAL_MS = 15_000L

/** Widget artwork size; RemoteViews bitmaps must stay small. */
private const val WIDGET_ART_PX = 192

/** Stop skipping after this many unplayable tracks in a row, so a broken queue can't spin. */
private const val MAX_CONSECUTIVE_ERRORS = 5

/** Custom session commands behind the notification's favourite and shuffle buttons. */
private const val CMD_TOGGLE_FAVOURITE = "com.bsoft.compose.bmusic.TOGGLE_FAVOURITE"
private const val CMD_TOGGLE_SHUFFLE = "com.bsoft.compose.bmusic.TOGGLE_SHUFFLE"

/** A resolved play request: the queue to install, where to start, and its source. */
private data class ResolvedQueue(val items: List<MediaItem>, val index: Int, val context: String)

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    private lateinit var mediaLibrarySession: MediaLibrarySession

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var playbackCoordinator: PlaybackCoordinator
    @Inject lateinit var equalizerManager: EqualizerManager
    @Inject lateinit var audioEffectsManager: AudioEffectsManager
    @Inject lateinit var equalizerPreferences: EqualizerPreferences
    @Inject lateinit var playerCounterRepository: PlayerCounterRepository
    @Inject lateinit var listeningStatsRepository: ListeningStatsRepository
    @Inject lateinit var favouriteRepository: FavouriteRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var snapshotStore: PlaybackSnapshotStore
    @Inject lateinit var appSettingsPreferences: AppSettingsPreferences
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var audioTagRepository: AudioTagRepository
    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher
    @Inject lateinit var audioAttributes: AudioAttributes
    @Inject lateinit var fileGuard: PlaybackFileGuard

    private val serviceJob = SupervisorJob()

    /** Main-thread scope: the player and the session must only be touched on this thread. */
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    /** The player the session exposes; routes every queue change through the coordinator. */
    private lateinit var player: BMusicPlayer

    /** Restoring the saved queue at start-up; playback resumption waits for it. */
    private var restoreJob: Job? = null

    // Home-screen widget: the art is kept so the last frame (after the service stops) still has it.
    private var widgetJob: Job? = null
    private var widgetArt: Bitmap? = null
    private var widgetArtSongId: Long? = null

    private val playCountTracker = PlayCountTracker()
    private var consecutiveErrors = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var queueSavePending = false

    // Song transitions (Off / Fade / Crossfade) and "Shake to change song" settings.
    private var transitionMode = TransitionMode.OFF
    private var transitionLengthMs = transitionMs(TRANSITION_SECONDS_DEFAULT)
    private var gaplessAlbums = true
    private lateinit var crossfade: CrossfadeEngine

    // ReplayGain: the song's level-matched volume. Fades and the sleep timer multiply it.
    private var replayGainMode = ReplayGainMode.OFF
    private var baseVolume = 1f
    private var replayGainJob: Job? = null
    private var shakeEnabled = false
    private var fadeJob: Job? = null
    private val shakeDetector = ShakeDetector()
    private var shakeRegistered = false
    private val sensorManager: SensorManager? by lazy { getSystemService(SensorManager::class.java) }
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size >= 3 && shakeDetector.onSample(v[0], v[1], v[2], SystemClock.elapsedRealtime())) {
                if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Sleep timer "end of track": stop as soon as the current song finishes.
            if (sleepTimer.state.value == SleepTimerState.EndOfTrack &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                exoPlayer.pause()
                exoPlayer.seekTo(0L)
                sleepTimer.cancel()
            }
            val song = mediaItem?.let(Song::fromMediaItem)
            if (song == null || song.id == 0L) {
                playCountTracker.reset()
            } else if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || song.id != playCountTracker.currentSongId) {
                // A queue edit that keeps the same song playing must not restart its listen time.
                val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song.duration
                // Counting happens later, once the track has really been listened to.
                playCountTracker.start(song.id, duration, exoPlayer.currentPosition)
            }
            // Keep the app's queue projection aligned with ExoPlayer.
            playbackCoordinator.synchronizeFromPlayer(exoPlayer)
            savePosition()
            updateWidget()
            updateReplayGain()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) scheduleQueueSave()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            queueManager.setRepeatMode(repeatMode)
            savePosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0 else crossfade.onMainStopped()
            savePosition()
            updateSoundFade()
            updateShakeListener()
            updateWidget()
        }

        override fun onPlayerError(error: PlaybackException) = handlePlaybackError(error)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = savePosition()

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The last song of the queue ended: an "end of track" timer has done its job.
            if (playbackState == Player.STATE_ENDED && sleepTimer.state.value == SleepTimerState.EndOfTrack) {
                sleepTimer.cancel()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        exoPlayer.repeatMode = queueManager.state.value.repeatMode
        // Keeps the CPU awake for long background playback with the screen off.
        exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)
        crossfade = CrossfadeEngine(this, exoPlayer, audioAttributes)
        exoPlayer.addListener(playerListener)
        // onAudioSessionIdChanged is not guaranteed to fire for the initial session.
        attachEqualizer(exoPlayer.audioSessionId)

        // Tapping the system media notification / lock-screen card returns to BMusic.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        player = BMusicPlayer(exoPlayer, playbackCoordinator, queueManager)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(mediaButtons(favourite = false, shuffle = false))
            .build()
        observeMediaButtons()

        restorePlaybackSnapshot()
        startTicker()
        observeSettings()
        fileGuard.handler = fileGuardHandler
    }

    override fun onDestroy() {
        if (fileGuard.handler === fileGuardHandler) fileGuard.handler = null
        mainHandler.removeCallbacksAndMessages(null)
        if (shakeRegistered) sensorManager?.unregisterListener(shakeListener)
        saveQueue()
        savePosition()
        widgetJob?.cancel()
        currentSong().let { song ->
            NowPlayingWidget.update(this, WidgetContent(song?.title, song?.artist, playing = false, art = widgetArt.takeIf { song?.id == widgetArtSongId }))
        }
        crossfade.release()
        equalizerManager.release()
        audioEffectsManager.release()
        serviceScope.cancel()
        exoPlayer.removeListener(playerListener)
        mediaLibrarySession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    // ---- Playback housekeeping ----------------------------------------------------------

    /** Samples playback once a second: counts plays and saves the position periodically. */
    private fun startTicker() {
        serviceScope.launch {
            var sinceSnapshot = 0L
            while (isActive) {
                delay(TICK_MS)
                if (!exoPlayer.isPlaying) {
                    // A countdown that ran out while paused has nothing left to stop.
                    if (sleepTimer.isExpired()) sleepTimer.cancel()
                    continue
                }

                playCountTracker.onProgress(exoPlayer.currentPosition, MAX_CONTINUOUS_STEP_MS)?.let { songId ->
                    val song = exoPlayer.currentMediaItem?.let(Song::fromMediaItem)
                    if (song != null && song.id == songId) {
                        launch {
                            playerCounterRepository.incrementCount(song)
                            listeningStatsRepository.record(song)
                        }
                    }
                }

                sinceSnapshot += TICK_MS
                if (sinceSnapshot >= SNAPSHOT_INTERVAL_MS) {
                    sinceSnapshot = 0L
                    savePosition()
                }
            }
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            sleepTimer.state.collect { updateSoundFade() }
        }
        serviceScope.launch {
            appSettingsPreferences.data.collect { settings ->
                transitionMode = TransitionMode.resolve(settings.transitionMode, settings.soundFade)
                transitionLengthMs = transitionMs(settings.transitionSeconds)
                gaplessAlbums = settings.gaplessAlbums
                if (transitionMode != TransitionMode.CROSSFADE) crossfade.cancel()
                val gainMode = ReplayGainMode.fromName(settings.replayGainMode)
                if (gainMode != replayGainMode) {
                    replayGainMode = gainMode
                    updateReplayGain()
                }
                shakeEnabled = settings.shakeToChange
                updateSoundFade()
                updateShakeListener()
            }
        }
    }

    /**
     * One volume loop for song transitions (fade or crossfade) and the sleep timer's final fade,
     * so they never fight over the volume. Runs only while music plays and one of them is on:
     * 10 times a second, or 40 around a crossfade so it starts on time and ramps smoothly.
     */
    private fun updateSoundFade() {
        val sleepCounting = sleepTimer.state.value is SleepTimerState.Counting
        val transitions = transitionMode != TransitionMode.OFF
        val shouldRun = (transitions || sleepCounting) && exoPlayer.isPlaying
        if (shouldRun && fadeJob?.isActive != true) {
            fadeJob = serviceScope.launch {
                while (isActive) {
                    if (sleepTimer.isExpired()) {
                        crossfade.cancel()
                        stopForSleepTimer()
                        break
                    }
                    val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    val sleepFactor = sleepTimer.volumeFactor()
                    val trackFactor = when (transitionMode) {
                        TransitionMode.OFF -> 1f
                        TransitionMode.FADE -> fadeVolume(
                            exoPlayer.currentPosition, duration,
                            fadeInMs = fadeInMsFor(transitionLengthMs), fadeOutMs = transitionLengthMs
                        )
                        TransitionMode.CROSSFADE -> {
                            crossfade.tick(crossfadeAllowed(duration), transitionLengthMs, sleepFactor * baseVolume)
                            crossfade.incomingFactor()
                        }
                    }
                    val target = trackFactor * sleepFactor * baseVolume
                    if (kotlin.math.abs(exoPlayer.volume - target) > 0.01f) exoPlayer.volume = target
                    delay(if (crossfade.needsFastTicks) 25 else 100)
                }
            }
        } else if (!shouldRun) {
            fadeJob?.cancel()
            fadeJob = null
            // Never leave the player quiet once neither fade is active. While merely paused, the
            // loop resumes (and corrects the volume) as soon as playback restarts.
            if (!transitions && !sleepCounting) exoPlayer.volume = baseVolume
        }
    }

    /** Whether the current song may crossfade into the next one (see [shouldCrossfade]). */
    private fun crossfadeAllowed(durationMs: Long): Boolean {
        val nextIndex = exoPlayer.nextMediaItemIndex
        val current = exoPlayer.currentMediaItem?.let(Song::fromMediaItem)
        val next = if (nextIndex == C.INDEX_UNSET) null else Song.fromMediaItem(exoPlayer.getMediaItemAt(nextIndex))
        return shouldCrossfade(
            CrossfadeContext(
                hasNext = current != null && next != null,
                repeatOne = exoPlayer.repeatMode == Player.REPEAT_MODE_ONE,
                sleepAtEndOfTrack = sleepTimer.state.value == SleepTimerState.EndOfTrack,
                durationMs = durationMs,
                lengthMs = crossfadeLengthMs(transitionLengthMs, durationMs),
                gaplessAlbums = gaplessAlbums,
                nextIsNextAlbumTrack = current != null && next != null &&
                    isNextTrackOfAlbum(current.albumId, current.track, next.albumId, next.track),
            )
        )
    }

    /** While the tag editor rewrites the current song: where it was, and whether it played. */
    private var heldPosition: Long? = null
    private var heldPlayWhenReady = false

    private val fileGuardHandler = object : PlaybackFileGuard.Handler {
        override suspend fun release(songId: Long): Boolean = withContext(Dispatchers.Main) {
            if (currentSong()?.id != songId) return@withContext false
            crossfade.cancel()
            heldPosition = exoPlayer.currentPosition
            heldPlayWhenReady = exoPlayer.playWhenReady
            // stop() closes the file; the queue and the current item stay in place.
            exoPlayer.stop()
            true
        }

        override suspend fun restore(songId: Long) {
            val fresh = withContext(io) { songRepository.findSongById(songId) }
            withContext(Dispatchers.Main) {
                val position = heldPosition ?: return@withContext
                heldPosition = null
                val index = exoPlayer.currentMediaItemIndex
                val item = exoPlayer.currentMediaItem
                if (fresh != null && item != null && Song.fromMediaItem(item).id == songId) {
                    // New title, artist and artwork for the notification and the app.
                    exoPlayer.replaceMediaItem(index, fresh.toMediaItem().buildUpon().setMediaId(item.mediaId).build())
                }
                exoPlayer.prepare()
                exoPlayer.seekTo(index, position)
                exoPlayer.playWhenReady = heldPlayWhenReady
                playbackCoordinator.synchronizeFromPlayer(exoPlayer)
                updateWidget()
            }
        }
    }

    private fun stopForSleepTimer() {
        sleepTimer.cancel()
        exoPlayer.pause()
        exoPlayer.volume = baseVolume
    }

    /**
     * Sets [baseVolume] from the current song's ReplayGain tags, and reads the next song's
     * ahead of time so its level is ready the moment it starts.
     */
    private fun updateReplayGain() {
        replayGainJob?.cancel()
        val song = currentSong()
        if (replayGainMode == ReplayGainMode.OFF || song == null) {
            applyBaseVolume(1f)
            return
        }
        val mode = replayGainMode
        replayGainJob = serviceScope.launch {
            applyBaseVolume(audioTagRepository.replayGain(song).volumeFactor(mode))
            val next = exoPlayer.nextMediaItemIndex
            if (next != C.INDEX_UNSET) {
                Song.fromMediaItem(exoPlayer.getMediaItemAt(next)).takeIf { it.id != 0L }
                    ?.let { audioTagRepository.replayGain(it) }
            }
        }
    }

    private fun applyBaseVolume(volume: Float) {
        baseVolume = volume
        // While a fade loop runs it applies baseVolume itself on its next tick.
        if (fadeJob?.isActive != true) exoPlayer.volume = volume
    }

    /** Listens to the accelerometer only while the setting is on and music plays (saves battery). */
    private fun updateShakeListener() {
        val manager = sensorManager ?: return
        val shouldListen = shakeEnabled && exoPlayer.isPlaying
        if (shouldListen && !shakeRegistered) {
            val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            shakeRegistered = manager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        } else if (!shouldListen && shakeRegistered) {
            manager.unregisterListener(shakeListener)
            shakeRegistered = false
        }
    }

    /**
     * A missing, corrupt or unsupported file must not silently stop playback. Skip to the next
     * track, up to [MAX_CONSECUTIVE_ERRORS] times in a row. The UI shows the error through its
     * own player listener.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        Log.w(TAG, "Playback error ${error.errorCode}", error)
        // 2xxx = I/O (file missing, permission), 3xxx = parsing, 4xxx = decoding.
        val skippable = error.errorCode in 2000..4999
        consecutiveErrors++
        if (skippable && consecutiveErrors < MAX_CONSECUTIVE_ERRORS && exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    private fun attachEqualizer(audioSessionId: Int) {
        // Bass boost, virtualizer and loudness ride on the same audio session as the EQ.
        audioEffectsManager.attach(audioSessionId)
        serviceScope.launch {
            val effects = equalizerPreferences.effects.first()
            audioEffectsManager.apply(effects.bassBoost, effects.virtualizer, effects.loudnessGainMb)
        }
        equalizerManager.attach(audioSessionId)
        if (!equalizerManager.isAvailable) return
        // Apply the saved EQ here, not in a screen, so it is active without the UI.
        serviceScope.launch {
            val enabled = equalizerPreferences.isEqualizerEnabled.first()
            val selected = equalizerPreferences.selected.first()
            val values = equalizerPreferences.values.first()
            equalizerManager.applySettings(enabled, selected, values)
        }
    }

    /**
     * Queue changes fire timeline events from inside the coordinator, before it has updated
     * QueueManager. Save on the next main-thread turn, once both agree, and coalesce bursts.
     */
    private fun scheduleQueueSave() {
        if (queueSavePending) return
        queueSavePending = true
        mainHandler.post {
            queueSavePending = false
            saveQueue()
        }
    }

    private fun saveQueue() {
        val ids = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId }
        val state = queueManager.state.value
        snapshotStore.saveQueue(
            mediaIds = ids,
            sourceOrder = queueManager.sourceOrder,
            context = queueManager.currentContext,
            shuffle = state.shuffle
        )
    }

    private fun savePosition() {
        if (exoPlayer.mediaItemCount == 0) return
        snapshotStore.savePosition(
            index = exoPlayer.currentMediaItemIndex,
            positionMs = exoPlayer.currentPosition,
            playWhenReady = exoPlayer.playWhenReady,
            repeatMode = exoPlayer.repeatMode,
            speed = exoPlayer.playbackParameters.speed
        )
    }

    /** Restores the last queue exactly, including the original order behind a shuffled queue. */
    private fun restorePlaybackSnapshot() {
        restoreJob = serviceScope.launch {
            // The speed survives restarts even with nothing queued.
            val speed = snapshotStore.loadSpeed()
            if (speed != 1f && exoPlayer.playbackParameters.speed == 1f) exoPlayer.setPlaybackSpeed(speed)
            val snapshot = snapshotStore.load() ?: return@launch
            if (exoPlayer.mediaItemCount > 0 || snapshot.mediaIds.isEmpty()) return@launch
            // Keep each restored item's saved position so the shuffle order and current entry
            // survive songs that were deleted since.
            val restored = withContext(io) {
                snapshot.mediaIds.mapIndexedNotNull { savedIndex, mediaId ->
                    val songId = MediaId.parse(mediaId).songIdOrNull ?: return@mapIndexedNotNull null
                    songRepository.findSongById(songId)?.toMediaItem()?.buildUpon()?.setMediaId(mediaId)?.build()
                        ?.let { savedIndex to it }
                }
            }
            // The user may have started something else while the library loaded.
            if (restored.isEmpty() || exoPlayer.mediaItemCount > 0) return@launch

            val items = restored.map { it.second }
            val sourceOrder = if (snapshot.sourceOrder.size == snapshot.mediaIds.size) {
                restored.map { snapshot.sourceOrder[it.first] }
            } else {
                emptyList()
            }
            val current = restored.indexOfFirst { it.first >= snapshot.index }.takeIf { it >= 0 } ?: items.lastIndex
            val sameEntry = restored[current].first == snapshot.index
            val playerIndex = queueManager.restore(
                items = items,
                sourceOrder = sourceOrder,
                currentIndex = current,
                shuffle = snapshot.shuffle,
                repeatMode = snapshot.repeatMode,
                contextPrefix = snapshot.context
            )
            // Restoring is the service's own work, so it talks to ExoPlayer directly instead of
            // through BMusicPlayer (which would treat it as a new queue and reshuffle it).
            exoPlayer.setMediaItems(items, playerIndex, if (sameEntry) snapshot.positionMs else 0L)
            exoPlayer.repeatMode = snapshot.repeatMode
            exoPlayer.prepare()
            if (snapshot.playWhenReady) exoPlayer.play()
        }
    }

    // ---- Home-screen widget ------------------------------------------------------------

    private fun currentSong(): Song? = exoPlayer.currentMediaItem?.let(Song::fromMediaItem)?.takeIf { it.id != 0L }

    /** Pushes the current song and play state to the widget; art loads in the background. */
    private fun updateWidget() {
        val song = currentSong()
        // playWhenReady, not isPlaying, so buffering doesn't flash the play icon.
        val playing = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
        widgetJob?.cancel()
        widgetJob = serviceScope.launch {
            if (song?.id != widgetArtSongId) {
                widgetArt = song?.let { Util.loadArtwork(this@PlaybackService, it.mediaUri, Size(WIDGET_ART_PX, WIDGET_ART_PX)) }
                widgetArtSongId = song?.id
            }
            NowPlayingWidget.update(this@PlaybackService, WidgetContent(song?.title, song?.artist, playing, widgetArt))
        }
    }

    // ---- Notification, lock screen and Android Auto buttons -------------------------------

    /** Favourite and shuffle buttons, shown next to previous / play / next. */
    @OptIn(UnstableApi::class)
    private fun mediaButtons(favourite: Boolean, shuffle: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(if (favourite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (favourite) R.string.remove_from_favourites else R.string.add_to_favourites))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVOURITE, Bundle.EMPTY))
            .build(),
        CommandButton.Builder(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(getString(if (shuffle) R.string.cd_shuffle_on else R.string.cd_shuffle_off))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build(),
    )

    /** Keeps the heart and shuffle icons in step with the current song and the queue. */
    private fun observeMediaButtons() {
        serviceScope.launch {
            combine(
                queueManager.state.map { it.current?.id to it.shuffle }.distinctUntilChanged(),
                favouriteRepository.getAllFavorites().map { list -> list.mapTo(HashSet()) { it.song } }
            ) { (songId, shuffle), favourites -> (songId != null && songId in favourites) to shuffle }
                .distinctUntilChanged()
                .collect { (favourite, shuffle) ->
                    mediaLibrarySession.setMediaButtonPreferences(mediaButtons(favourite, shuffle))
                }
        }
    }

    private fun handleCustomCommand(action: String): Boolean {
        when (action) {
            CMD_TOGGLE_FAVOURITE -> {
                val song = queueManager.state.value.current ?: return false
                serviceScope.launch { favouriteRepository.toggleFavourite(song) }
            }
            // Through BMusicPlayer, so the queue is reordered exactly as from the app.
            CMD_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !queueManager.state.value.shuffle
            else -> return false
        }
        return true
    }

    // ---- Resolving media IDs (run on the I/O dispatcher) --------------------------------

    /** All songs of a source in display order, or null when the source cannot be expanded. */
    private suspend fun songsOf(source: MediaSource): List<Song>? = when (source) {
        MediaSource.Songs -> songRepository.songs
        is MediaSource.Album -> songRepository.findSongsByAlbumId(source.albumId)
        is MediaSource.Artist -> songRepository.findArtistDetailsByArtistId(source.artistId).songs
        MediaSource.RecentlyAdded -> songRepository.last
        MediaSource.RecentlyPlayed -> playerCounterRepository.getLastPlayed().first()
            .mapNotNull { songRepository.findSongById(it.song) }
        MediaSource.MostPlayed -> playerCounterRepository.getMostPlayed().first()
            .mapNotNull { songRepository.findSongById(it.song) }
        MediaSource.Favourites -> favouriteRepository.getAllFavorites().first()
            .mapNotNull { songRepository.findSongById(it.song) }
        MediaSource.Rediscover -> SmartPlaylists.rediscover(playerCounterRepository.getAll().first(), System.currentTimeMillis())
            .mapNotNull { songRepository.findSongById(it) }
        MediaSource.NeverPlayed -> SmartPlaylists.neverPlayed(songRepository.songs, playerCounterRepository.getAll().first())
        // Use the live library entry so deleted files are skipped instead of failing to play.
        is MediaSource.Playlist -> playlistRepository.getSongsFromPlayList(source.playlistId)
            .mapNotNull { songRepository.findSongById(it.id) }
        is MediaSource.Folder -> FolderTree.build(songRepository.songs).findByKey(source.key)?.allSongs()
        MediaSource.Custom, is MediaSource.Queued -> null
    }

    /** Builds the queue for a whole source; each entry gets a unique, contextual media ID. */
    private fun queueFor(source: MediaSource, songs: List<Song>, index: Int, songId: Long?): ResolvedQueue {
        val items = songs.mapIndexed { i, song ->
            song.toMediaItem().buildUpon()
                .setMediaId(MediaId.Entry(source, i, song.id).encode())
                .build()
        }
        val start = songId?.let { id -> songs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return ResolvedQueue(items, start, source.prefix)
    }

    /**
     * Turns one incoming item into a playable one. Other controllers (Android Auto, Assistant,
     * Bluetooth) never send URIs, so songs are always rebuilt from the library by ID.
     */
    private fun resolveEntry(item: MediaItem): MediaItem? {
        val songId = MediaId.parse(item.mediaId).songIdOrNull
        val song = songId?.let { songRepository.findSongById(it) }
        if (song != null) return song.toMediaItem().buildUpon().setMediaId(item.mediaId).build()
        // Same-process callers may already supply a playable item (e.g. a song filtered out of
        // the main library because it is short); accept it as is.
        return item.takeIf { it.localConfiguration != null }
    }

    private suspend fun resolveRequest(mediaItems: List<MediaItem>, startIndex: Int): ResolvedQueue {
        if (mediaItems.size == 1) {
            when (val id = MediaId.parse(mediaItems.first().mediaId)) {
                // A single song (e.g. tapped in Auto's "Songs" folder): play the library from it.
                is MediaId.Track -> {
                    val all = songRepository.songs
                    if (all.any { it.id == id.songId }) return queueFor(MediaSource.Songs, all, 0, id.songId)
                }
                // "Play this album/playlist/... from index or song".
                is MediaId.Entry -> songsOf(id.source)?.takeIf { it.isNotEmpty() }?.let { songs ->
                    return queueFor(id.source, songs, id.index, id.songId)
                }
                else -> Unit
            }
        }

        // An explicit queue: keep the caller's exact order, duplicates included.
        val resolved = mediaItems.mapIndexedNotNull { i, item -> resolveEntry(item)?.let { i to it } }
        if (resolved.isEmpty()) return ResolvedQueue(emptyList(), 0, MediaSource.Custom.prefix)
        val start = resolved.indexOfFirst { it.first >= startIndex }.takeIf { it >= 0 } ?: resolved.lastIndex
        val context = (MediaId.parse(mediaItems.first().mediaId) as? MediaId.Entry)
            ?.source?.takeUnless { it is MediaSource.Queued }?.prefix
            ?: MediaSource.Custom.prefix
        return ResolvedQueue(resolved.map { it.second }, start, context)
    }

    private fun folder(id: String, title: String, type: Int): MediaItem =
        MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build()
            ).build()

    /** A playlist row in Android Auto: open it to browse, or play it straight away. */
    private fun playlistItem(source: MediaSource, title: String): MediaItem =
        MediaItem.Builder().setMediaId(MediaId.Entry(source, 0).encode())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            ).build()

    private suspend fun childrenOf(parent: MediaId): List<MediaItem> = when (parent) {
        MediaId.Root -> listOf(
            folder(MediaId.SONGS, getString(R.string.songs_browsable), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(MediaId.ALBUMS, getString(R.string.albums_browsable), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(MediaId.ARTISTS, getString(R.string.artists_browsable), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            folder(MediaId.PLAYLISTS, getString(R.string.playlists_browsable), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        )
        // The built-in lists first, then the user's own playlists.
        MediaId.PlaylistsFolder -> listOf(
            playlistItem(MediaSource.Favourites, getString(R.string.playlist_favourites)),
            playlistItem(MediaSource.RecentlyAdded, getString(R.string.playlist_recently_added)),
            playlistItem(MediaSource.MostPlayed, getString(R.string.playlist_most_played)),
            playlistItem(MediaSource.RecentlyPlayed, getString(R.string.playlist_recently_played)),
            playlistItem(MediaSource.Rediscover, getString(R.string.playlist_rediscover)),
            playlistItem(MediaSource.NeverPlayed, getString(R.string.playlist_never_played)),
        ) + playlistRepository.getPlayLists().first().map { playlistItem(MediaSource.Playlist(it.id), it.name) }
        MediaId.SongsFolder -> songRepository.songs.map { it.toMediaItem() }
        MediaId.AlbumsFolder -> songRepository.albums.map { it.toMediaItem() }
        MediaId.ArtistsFolder -> songRepository.artists.map { it.toMediaItem() }
        is MediaId.Entry -> when (val source = parent.source) {
            // Children carry album/artist context, so tapping one plays the rest of it too.
            is MediaSource.Album -> songRepository.findSongsByAlbumId(source.albumId)
                .mapIndexed { i, s -> s.toMediaItem().buildUpon().setMediaId(MediaId.Entry(source, i, s.id).encode()).build() }
            is MediaSource.Artist -> songRepository.findArtistDetailsByArtistId(source.artistId).songs
                .mapIndexed { i, s -> s.toMediaItem().buildUpon().setMediaId(MediaId.Entry(source, i, s.id).encode()).build() }
            // Playlists, built-in or custom: tapping a song plays the list from there.
            else -> songsOf(source).orEmpty()
                .mapIndexed { i, s -> s.toMediaItem().buildUpon().setMediaId(MediaId.Entry(source, i, s.id).encode()).build() }
        }
        else -> emptyList()
    }

    private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return this
        val from = page.coerceAtLeast(0).toLong() * pageSize
        if (from >= size) return emptyList()
        return subList(from.toInt(), minOf(size, from.toInt() + pageSize))
    }

    // ---- Session callback ---------------------------------------------------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        /**
         * Keeps Media3's defaults (full control for trusted controllers such as the system
         * notification, Android Auto and this app; read-only for others) and lets trusted
         * controllers use the favourite and shuffle buttons.
         */
        @OptIn(UnstableApi::class)
        override fun onConnectAsync(
            session: MediaSession, controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (controller.isTrusted) {
                result.setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_TOGGLE_FAVOURITE, Bundle.EMPTY))
                        .add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        .build()
                )
            }
            return Futures.immediateFuture(result.build())
        }

        /**
         * Play pressed with nothing queued (widget or headset after the app was closed): resume
         * the saved queue. The service restores it at start-up; wait for that and hand it over.
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            restoreJob?.join()
            val count = exoPlayer.mediaItemCount
            if (count == 0) throw UnsupportedOperationException("Nothing saved to resume")
            MediaSession.MediaItemsWithStartPosition(
                (0 until count).map { exoPlayer.getMediaItemAt(it) },
                exoPlayer.currentMediaItemIndex,
                exoPlayer.currentPosition
            )
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            val handled = handleCustomCommand(customCommand.customAction)
            return Futures.immediateFuture(
                SessionResult(if (handled) SessionResult.RESULT_SUCCESS else SessionError.ERROR_NOT_SUPPORTED)
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = folder(MediaId.ROOT, getString(R.string.library_root), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val items = withContext(io) { childrenOf(MediaId.parse(parentId)) }
            LibraryResult.ofItemList(items.paged(page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val item = withContext(io) {
                when (val id = MediaId.parse(mediaId)) {
                    is MediaId.Track -> songRepository.findSongById(id.songId)?.toMediaItem()
                    is MediaId.Entry -> when {
                        id.songId != null -> songRepository.findSongById(id.songId)?.toMediaItem()
                            ?.buildUpon()?.setMediaId(mediaId)?.build()
                        id.source is MediaSource.Album -> songRepository.findAlbumById(id.source.albumId)?.toMediaItem(id.index)
                        id.source is MediaSource.Artist -> songRepository.findArtistById(id.source.artistId)?.toMediaItem(id.index)
                        else -> null
                    }
                    else -> null
                }
            }
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val requested = mediaItems.toList()
            // Only resolve here. Media3 then calls BMusicPlayer.setMediaItems, which installs the
            // queue (and applies shuffle) through the coordinator.
            val resolved = withContext(io) { resolveRequest(requested, startIndex) }
            MediaSession.MediaItemsWithStartPosition(resolved.items, resolved.index, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            val requested = mediaItems.toList()
            withContext(io) { requested.mapNotNull { resolveEntry(it) } }.toMutableList()
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val count = withContext(io) { songRepository.search(query).size }
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val results = withContext(io) { songRepository.search(query).map { it.toMediaItem() } }
            LibraryResult.ofItemList(results.paged(page, pageSize), params)
        }
    }
}
