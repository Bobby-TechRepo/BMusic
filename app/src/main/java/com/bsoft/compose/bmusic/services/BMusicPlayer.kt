package com.bsoft.compose.bmusic.services

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.bsoft.compose.bmusic.data.PlaybackCoordinator
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.models.MediaId
import com.bsoft.compose.bmusic.data.models.MediaSource
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player the media session exposes. Every queue change and shuffle toggle, from any
 * controller (the app, the notification, Bluetooth, Android Auto, Assistant), lands here and is
 * routed through [PlaybackCoordinator]. This makes the service the single owner of the queue:
 * nothing else mutates [QueueManager].
 *
 * Shuffle is reported to controllers from [QueueManager] rather than ExoPlayer, because BMusic
 * shows "Up next" in play order and therefore reorders the timeline itself; ExoPlayer's own
 * shuffle mode stays off.
 */
@OptIn(UnstableApi::class)
class BMusicPlayer(
    private val exoPlayer: ExoPlayer,
    private val coordinator: PlaybackCoordinator,
    private val queueManager: QueueManager
) : ForwardingSimpleBasePlayer(exoPlayer) {

    override fun getState(): SimpleBasePlayer.State =
        super.getState().buildUpon()
            .setShuffleModeEnabled(queueManager.state.value.shuffle)
            .build()

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        coordinator.setShuffle(exoPlayer, shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
    ): ListenableFuture<*> {
        val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        coordinator.install(exoPlayer, mediaItems.toList(), start, contextOf(mediaItems), startPositionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        coordinator.addAt(exoPlayer, index, mediaItems.toList())
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        coordinator.removeRange(exoPlayer, fromIndex, toIndex)
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        coordinator.moveRange(exoPlayer, fromIndex, toIndex, newIndex)
        return Futures.immediateVoidFuture()
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>
    ): ListenableFuture<*> {
        coordinator.replaceRange(exoPlayer, fromIndex, toIndex, mediaItems.toList())
        return Futures.immediateVoidFuture()
    }

    private companion object {
        /** The source the queue came from, read from the first entry's media ID. */
        fun contextOf(items: List<MediaItem>): String {
            val first = items.firstOrNull()?.let { MediaId.parse(it.mediaId) } as? MediaId.Entry
            return first?.source?.takeUnless { it is MediaSource.Queued }?.prefix
                ?: MediaSource.Custom.prefix
        }
    }
}
