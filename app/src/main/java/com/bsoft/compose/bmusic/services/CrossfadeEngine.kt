package com.bsoft.compose.bmusic.services

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.bsoft.compose.bmusic.data.playback.crossfadeInVolume
import com.bsoft.compose.bmusic.data.playback.crossfadeLengthMs
import com.bsoft.compose.bmusic.data.playback.crossfadeOutVolume

/**
 * True crossfade on top of a single Media3 player.
 *
 * The media session owns one [main] player, so the overlap is played by a second, private
 * "tail" player:
 * 1. A few seconds before the crossfade point, the tail player loads the *current* song, paused
 *    at the point where the overlap starts.
 * 2. At that point it starts playing; as soon as it produces sound, the main player is muted and
 *    moves on to the next song. Controllers (notification, lock screen, Android Auto) simply see
 *    the next song start.
 * 3. During the overlap the tail fades the old song out while the main player fades the new one
 *    in (equal-power curves), then the tail stops.
 *
 * The tail shares the main player's audio session, so the equalizer and sound effects apply to
 * both, and it never takes audio focus: if the main player pauses, loses focus, seeks or skips,
 * the overlap is cut short and the tail goes quiet at once.
 *
 * Everything runs on the main thread, driven by [tick] from the service's volume loop.
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val main: ExoPlayer,
    private val audioAttributes: AudioAttributes,
) {
    private enum class Phase { IDLE, PREPARED, STARTING, FADING }

    private var tail: ExoPlayer? = null
    private var phase = Phase.IDLE

    /** The main player's item index the tail was prepared for (the outgoing song). */
    private var preparedIndex = C.INDEX_UNSET
    private var preparedMediaId: String? = null
    /** The main player's item index the overlap fades into (the incoming song). */
    private var incomingIndex = C.INDEX_UNSET
    private var overlapMs = 0L
    private var startRequestedAt = 0L
    private var fadeStartedAt = 0L
    /** The outgoing song's volume (ReplayGain and sleep fade) when the overlap began. */
    private var outgoingVolume = 1f

    /** True while [tick] needs to run often (start timing and smooth ramps). */
    val needsFastTicks: Boolean
        get() = phase != Phase.IDLE

    /** True while the new song is fading in; the service then leaves the volume to [incomingFactor]. */
    val crossfading: Boolean
        get() = phase == Phase.FADING

    private val tailListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && phase == Phase.STARTING) handOver()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && phase == Phase.FADING) finish()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Crossfade tail failed; playing without it", error)
            cancel()
        }
    }

    /**
     * Advances the crossfade. [allowed] says whether the current song may crossfade into the
     * next one at all; [settingMs] is the chosen length; [volume] is the volume the main player
     * would have without a crossfade (ReplayGain times the sleep timer's fade).
     */
    fun tick(allowed: Boolean, settingMs: Long, volume: Float) {
        when (phase) {
            Phase.IDLE -> if (allowed && main.isPlaying) maybePrepare(settingMs)
            Phase.PREPARED -> {
                val duration = main.duration
                val remaining = duration - main.currentPosition
                when {
                    !allowed || main.currentMediaItemIndex != preparedIndex ||
                        main.currentMediaItem?.mediaId != preparedMediaId -> cancel()
                    // Seeked back, far before the crossfade point: prepare again later.
                    remaining > overlapMs + PREPARE_LEAD_MS + 1_000L -> cancel()
                    // Seeked past most of the overlap: just let the song end normally.
                    remaining < MIN_REMAINING_TO_START_MS -> cancel()
                    main.isPlaying && remaining <= overlapMs + START_LATENCY_MS -> start(volume)
                }
            }
            Phase.STARTING -> {
                if (!main.isPlaying || SystemClock.elapsedRealtime() - startRequestedAt > START_TIMEOUT_MS) cancel()
            }
            Phase.FADING -> {
                // The listener skipped or picked another song: drop the old one at once.
                if (main.currentMediaItemIndex != incomingIndex) {
                    cancel()
                    return
                }
                val progress = progress()
                tail?.volume = outgoingVolume * crossfadeOutVolume(progress)
                if (progress >= 1f) finish()
            }
        }
    }

    /** Multiplier for the main player's volume: ramps the incoming song in during the overlap. */
    fun incomingFactor(): Float = if (phase == Phase.FADING) crossfadeInVolume(progress()) else 1f

    /** The main player paused or stopped: cut the overlap (the next song carries on alone). */
    fun onMainStopped() {
        if (phase == Phase.STARTING || phase == Phase.FADING) cancel()
    }

    /** Stops any overlap and unloads the tail player (it stays created for next time). */
    fun cancel() {
        tail?.run {
            pause()
            volume = 0f
            clearMediaItems()
        }
        phase = Phase.IDLE
        preparedIndex = C.INDEX_UNSET
        preparedMediaId = null
        incomingIndex = C.INDEX_UNSET
    }

    fun release() {
        cancel()
        tail?.run {
            removeListener(tailListener)
            release()
        }
        tail = null
    }

    private fun maybePrepare(settingMs: Long) {
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val length = crossfadeLengthMs(settingMs, duration)
        if (length <= 0L) return
        val remaining = duration - main.currentPosition
        if (remaining > length + PREPARE_LEAD_MS || remaining < length + MIN_REMAINING_TO_START_MS) return
        val item = main.currentMediaItem ?: return

        val player = tail ?: ExoPlayer.Builder(context).build().also { created ->
            // Never takes audio focus: the main player owns it and the tail follows it.
            created.setAudioAttributes(audioAttributes, false)
            created.addListener(tailListener)
            tail = created
        }
        // Same session as the main player, so the equalizer and effects shape the tail too.
        if (main.audioSessionId != C.AUDIO_SESSION_ID_UNSET) player.setAudioSessionId(main.audioSessionId)
        player.playbackParameters = main.playbackParameters
        player.volume = 0f
        player.setMediaItem(item, duration - length)
        player.playWhenReady = false
        player.prepare()

        overlapMs = length
        preparedIndex = main.currentMediaItemIndex
        preparedMediaId = item.mediaId
        phase = Phase.PREPARED
    }

    private fun start(volume: Float) {
        val player = tail ?: return cancel()
        if (player.playbackState != Player.STATE_READY) {
            // Not buffered in time (slow storage): skip this crossfade rather than stutter.
            cancel()
            return
        }
        outgoingVolume = volume
        player.volume = volume
        player.play()
        startRequestedAt = SystemClock.elapsedRealtime()
        phase = Phase.STARTING
    }

    /** The tail is audible: mute the main player and move it to the next song. */
    private fun handOver() {
        val next = main.nextMediaItemIndex
        if (next == C.INDEX_UNSET || !main.isPlaying) {
            cancel()
            return
        }
        main.volume = 0f
        main.seekToNextMediaItem()
        incomingIndex = main.currentMediaItemIndex
        fadeStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.FADING
    }

    private fun finish() = cancel()

    private fun progress(): Float =
        if (overlapMs <= 0L) 1f
        else ((SystemClock.elapsedRealtime() - fadeStartedAt).toFloat() / overlapMs).coerceIn(0f, 1f)

    private companion object {
        const val TAG = "CrossfadeEngine"
        /** Load the tail this long before the overlap, so it is buffered when needed. */
        const val PREPARE_LEAD_MS = 4_000L
        /** Starting playback takes a moment; start this much early so the two line up. */
        const val START_LATENCY_MS = 60L
        /** Give up if the tail has not started within this time. */
        const val START_TIMEOUT_MS = 1_500L
        /** Less than this left when the overlap would start: not worth it. */
        const val MIN_REMAINING_TO_START_MS = 500L
    }
}
