package com.bsoft.compose.bmusic.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only code that changes the play queue. It applies every change to the [Player] and to
 * [QueueManager] together, so the app's queue model can never drift from what actually plays.
 *
 * Called from [com.bsoft.compose.bmusic.services.BMusicPlayer], through which every controller
 * (the app's UI, the notification, Bluetooth, Android Auto) reaches the player. Main thread only.
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val queueManager: QueueManager
) {
    /** Install the exact list supplied by the user and prepare the player. */
    fun replaceQueue(
        player: Player,
        items: List<MediaItem>,
        startIndex: Int,
        context: String,
        startPositionMs: Long = 0L
    ) {
        install(player, items, startIndex, context, startPositionMs)
        player.prepare()
    }

    /**
     * Install a new queue without preparing; the Media3 session prepares on the caller's request.
     *
     * Handing back the queue that is already loaded (entry for entry, as playback resumption
     * does) only moves to [startIndex]: re-installing it would reshuffle a shuffled queue.
     */
    fun install(
        player: Player,
        items: List<MediaItem>,
        startIndex: Int,
        context: String,
        startPositionMs: Long
    ) {
        if (isLoaded(player, items)) {
            val index = startIndex.coerceIn(0, items.lastIndex)
            player.seekTo(index, startPositionMs)
            queueManager.updateCurrentIndex(index)
            return
        }
        val playerIndex = queueManager.setQueue(items, startIndex, context)
        player.setMediaItems(queueManager.currentQueue, playerIndex, startPositionMs)
    }

    private fun isLoaded(player: Player, items: List<MediaItem>): Boolean {
        val queue = queueManager.currentQueue
        return items.isNotEmpty() && items.size == queue.size && items.size == player.mediaItemCount &&
            items.indices.all { items[it].mediaId == queue[it].mediaId }
    }

    fun synchronizeFromPlayer(player: Player) {
        if (player.mediaItemCount == 0) {
            queueManager.updateCurrentIndex(-1)
            return
        }
        if (player.currentMediaItemIndex in queueManager.currentQueue.indices) {
            queueManager.updateCurrentIndex(player.currentMediaItemIndex)
        }
    }

    fun removeAt(player: Player, index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        queueManager.removeAt(index)
        queueManager.updateCurrentIndex(player.currentMediaItemIndex)
    }

    /** Removes entries [fromIndex, toIndex). Removing everything clears the queue. */
    fun removeRange(player: Player, fromIndex: Int, toIndex: Int) {
        val count = player.mediaItemCount
        val end = toIndex.coerceAtMost(count)
        if (fromIndex < 0 || fromIndex >= end) return
        if (fromIndex == 0 && end == count) {
            clearQueue(player)
            return
        }
        for (index in end - 1 downTo fromIndex) {
            player.removeMediaItem(index)
            queueManager.removeAt(index)
        }
        queueManager.updateCurrentIndex(player.currentMediaItemIndex)
    }

    fun move(player: Player, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in 0 until player.mediaItemCount || toIndex !in 0 until player.mediaItemCount || fromIndex == toIndex) return
        player.moveMediaItem(fromIndex, toIndex)
        queueManager.move(fromIndex, toIndex)
        queueManager.updateCurrentIndex(player.currentMediaItemIndex)
    }

    /** Moves entries [fromIndex, toIndex) so the first one lands at [newIndex]. */
    fun moveRange(player: Player, fromIndex: Int, toIndex: Int, newIndex: Int) {
        if (toIndex - fromIndex == 1) {
            move(player, fromIndex, newIndex)
            return
        }
        val count = player.mediaItemCount
        if (fromIndex < 0 || toIndex > count || fromIndex >= toIndex) return
        player.moveMediaItems(fromIndex, toIndex, newIndex)
        adoptFromPlayer(player)
    }

    fun addNext(player: Player, item: MediaItem) {
        val insertIndex = if (player.currentMediaItemIndex >= 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex.coerceAtMost(player.mediaItemCount), item)
        queueManager.insertAt(insertIndex, item)
    }

    /** Inserts [items] starting at [index] (clamped to the queue). */
    fun addAt(player: Player, index: Int, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, player.mediaItemCount)
        player.addMediaItems(safeIndex, items)
        items.forEachIndexed { offset, item -> queueManager.insertAt(safeIndex + offset, item) }
        queueManager.updateCurrentIndex(player.currentMediaItemIndex)
    }

    fun replaceRange(player: Player, fromIndex: Int, toIndex: Int, items: List<MediaItem>) {
        player.replaceMediaItems(fromIndex, toIndex, items)
        adoptFromPlayer(player)
    }

    fun clearQueue(player: Player) {
        player.clearMediaItems()
        queueManager.setQueue(emptyList(), 0, queueManager.currentContext)
    }

    /**
     * Turns shuffle on or off without interrupting the song that is playing.
     *
     * The queue is shown in play order, so shuffling physically reorders the timeline. Instead
     * of replacing the whole timeline (which reloads the current track and stutters), every
     * entry except the playing one is removed and the new order is added back around it.
     */
    fun setShuffle(player: Player, enabled: Boolean) {
        if (enabled == queueManager.state.value.shuffle) return
        val playing = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (playing in 0 until count) queueManager.updateCurrentIndex(playing)

        val target = if (enabled) queueManager.enableShuffle() else queueManager.disableShuffle()
        val queue = queueManager.currentQueue
        if (queue.isEmpty() || count == 0) return

        if (playing !in 0 until count || target !in queue.indices || count != queue.size) {
            // Out of sync: fall back to a full replace at the current position.
            player.setMediaItems(queue, target.coerceIn(queue.indices), player.currentPosition)
            return
        }
        if (playing + 1 < count) player.removeMediaItems(playing + 1, count)
        if (playing > 0) player.removeMediaItems(0, playing)
        if (target > 0) player.addMediaItems(0, queue.subList(0, target).toList())
        if (target + 1 < queue.size) player.addMediaItems(target + 1, queue.subList(target + 1, queue.size).toList())
    }

    private fun adoptFromPlayer(player: Player) {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        queueManager.adopt(items, player.currentMediaItemIndex)
    }
}
