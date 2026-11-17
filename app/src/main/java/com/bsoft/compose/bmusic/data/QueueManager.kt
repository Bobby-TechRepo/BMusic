package com.bsoft.compose.bmusic.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.states.QueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** App-level queue projection. ExoPlayer remains the playback timeline; this class owns
 * the source order and the mapping between shuffled positions and source positions. */
class QueueManager {
    private var currentContextPrefix: String = "songs"
    private var originalQueue: List<MediaItem> = emptyList()
    private var sourcePositions: List<Int> = emptyList()
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()
    val currentQueue: List<MediaItem> get() = _state.value.queue
    val currentContext: String get() = currentContextPrefix

    val currentOriginalIndex: Int
        get() {
            val index = _state.value.currentIndex ?: return 0
            return sourcePositions.getOrNull(index) ?: 0
        }

    private fun shuffledPositions(size: Int, targetIndex: Int): List<Int> {
        if (size == 0) return emptyList()
        val target = targetIndex.coerceIn(0, size - 1)
        return (listOf(target) + (0 until size).filter { it != target }.shuffled())
    }

    /** Replaces the logical queue and returns the player index to start at. */
    fun setQueue(items: List<MediaItem>, startIndex: Int, contextPrefix: String = "songs"): Int {
        currentContextPrefix = contextPrefix
        val valid = items.filter { Song.fromMediaItem(it).id != 0L }
        originalQueue = valid
        if (valid.isEmpty()) {
            sourcePositions = emptyList()
            _state.update { it.copy(queue = emptyList(), currentIndex = null) }
            return 0
        }
        val start = startIndex.coerceIn(valid.indices)
        val shuffle = _state.value.shuffle
        sourcePositions = if (shuffle) shuffledPositions(valid.size, start) else valid.indices.toList()
        val currentIndex = if (shuffle) 0 else start
        _state.update { it.copy(queue = sourcePositions.map(valid::get), currentIndex = currentIndex) }
        return currentIndex
    }

    fun getCurrentContextMediaId(): String {
        val currentSongId = _state.value.current?.id ?: -1L
        return "$currentContextPrefix#${currentOriginalIndex}|$currentSongId"
    }

    /** Synchronize from the actual player index, not from an independently assumed index. */
    fun updateCurrentIndex(index: Int) {
        val size = _state.value.queue.size
        _state.update { current ->
            current.copy(currentIndex = index.takeIf { it in 0..<size })
        }
    }

    fun toggleShuffle(): Int = if (_state.value.shuffle) disableShuffle() else enableShuffle()

    fun enableShuffle(): Int {
        if (_state.value.shuffle) return _state.value.currentIndex ?: 0
        if (originalQueue.isEmpty()) {
            _state.update { it.copy(shuffle = true, queue = emptyList(), currentIndex = null) }
            sourcePositions = emptyList()
            return 0
        }
        val current = _state.value.currentIndex ?: 0
        val sourceIndex = sourcePositions.getOrNull(current) ?: current
        sourcePositions = shuffledPositions(originalQueue.size, sourceIndex)
        _state.update { it.copy(shuffle = true, queue = sourcePositions.map(originalQueue::get), currentIndex = 0) }
        return 0
    }

    fun disableShuffle(): Int {
        val current = _state.value.currentIndex ?: 0
        val sourceIndex = sourcePositions.getOrNull(current) ?: current
        sourcePositions = originalQueue.indices.toList()
        val safeIndex = sourceIndex.takeIf { it in originalQueue.indices }
        _state.update { it.copy(shuffle = false, queue = originalQueue, currentIndex = safeIndex) }
        return safeIndex ?: 0
    }


    /** Removes one playback entry while preserving the mapping to the original source list. */
    fun removeAt(index: Int): MediaItem? {
        val current = _state.value
        if (index !in current.queue.indices) return null
        val removed = current.queue[index]
        val sourceIndex = sourcePositions.getOrNull(index) ?: index
        if (sourceIndex in originalQueue.indices) {
            originalQueue = originalQueue.toMutableList().also { it.removeAt(sourceIndex) }
        }
        sourcePositions = sourcePositions.filterIndexed { i, _ -> i != index }
            .map { if (it > sourceIndex) it - 1 else it }
        val nextIndex = when {
            current.queue.size <= 1 -> null
            current.currentIndex == null -> null
            current.currentIndex > index -> current.currentIndex - 1
            current.currentIndex == index -> index.coerceAtMost(current.queue.size - 2)
            else -> current.currentIndex
        }
        val updatedQueue = current.queue.toMutableList().also { it.removeAt(index) }
        _state.update { it.copy(queue = updatedQueue, currentIndex = nextIndex) }
        return removed
    }

    /** Moves an entry in the visible playback order. Duplicate songs remain distinct entries. */
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        val current = _state.value
        if (fromIndex !in current.queue.indices || toIndex !in current.queue.indices || fromIndex == toIndex) return false
        val queue = current.queue.toMutableList().also { list -> list.add(toIndex, list.removeAt(fromIndex)) }
        sourcePositions = sourcePositions.toMutableList().also { list -> list.add(toIndex, list.removeAt(fromIndex)) }
        if (!current.shuffle) {
            originalQueue = queue
            sourcePositions = queue.indices.toList()
        }
        val oldCurrent = current.currentIndex
        val newCurrent = when {
            oldCurrent == null -> null
            oldCurrent == fromIndex -> toIndex
            fromIndex < oldCurrent && toIndex >= oldCurrent -> oldCurrent - 1
            fromIndex > oldCurrent && toIndex <= oldCurrent -> oldCurrent + 1
            else -> oldCurrent
        }
        _state.update { it.copy(queue = queue, currentIndex = newCurrent) }
        return true
    }

    /** Inserts an entry into the active queue at a playback-order position. */
    fun insertAt(index: Int, item: MediaItem): Int {
        val current = _state.value
        val safeIndex = index.coerceIn(0, current.queue.size)
        originalQueue = originalQueue + item
        val sourceIndex = originalQueue.lastIndex
        val queue = current.queue.toMutableList().also { it.add(safeIndex, item) }
        sourcePositions = sourcePositions.toMutableList().also { it.add(safeIndex, sourceIndex) }
        _state.update { it.copy(queue = queue, currentIndex = when {
            it.currentIndex == null -> safeIndex
            it.currentIndex >= safeIndex -> it.currentIndex + 1
            else -> it.currentIndex
        }) }
        return safeIndex
    }

    fun setRepeatMode(mode: @Player.RepeatMode Int) {
        _state.update { it.copy(repeatMode = mode) }
    }

    /**
     * For each entry in play order, its position in the original (unshuffled) order. The
     * identity when shuffle is off. Saved with the queue so a restored shuffled queue can still
     * be un-shuffled back to its original order.
     */
    val sourceOrder: List<Int>
        get() = sourcePositions

    /**
     * Restores a saved queue exactly as it was: [items] in play order, [sourceOrder] mapping
     * each one to its original position. No reshuffle happens. If [sourceOrder] does not match
     * [items] (some songs were deleted, or an old snapshot), play order becomes the original order.
     *
     * @return the player index to start at.
     */
    fun restore(
        items: List<MediaItem>,
        sourceOrder: List<Int>,
        currentIndex: Int,
        shuffle: Boolean,
        repeatMode: @Player.RepeatMode Int,
        contextPrefix: String
    ): Int {
        currentContextPrefix = contextPrefix
        if (items.isEmpty()) {
            originalQueue = emptyList()
            sourcePositions = emptyList()
            _state.update { it.copy(queue = emptyList(), currentIndex = null, shuffle = shuffle, repeatMode = repeatMode) }
            return 0
        }
        // Normalise to a permutation of 0..n-1 by rank, so gaps left by dropped songs are fine.
        val positions = if (sourceOrder.size == items.size) {
            val ranked = IntArray(items.size)
            sourceOrder.withIndex().sortedBy { it.value }.forEachIndexed { rank, entry -> ranked[entry.index] = rank }
            ranked.toList()
        } else {
            items.indices.toList()
        }
        val original = arrayOfNulls<MediaItem>(items.size)
        positions.forEachIndexed { playIndex, sourceIndex -> original[sourceIndex] = items[playIndex] }
        originalQueue = original.map { requireNotNull(it) }
        sourcePositions = positions
        val index = currentIndex.coerceIn(items.indices)
        _state.update { it.copy(queue = items, currentIndex = index, shuffle = shuffle, repeatMode = repeatMode) }
        return index
    }

    /**
     * Takes the player's timeline as the new queue after a change that cannot be mapped entry by
     * entry (e.g. a range move or replace from an external controller). The current play order
     * becomes the original order; the shuffle flag is kept.
     */
    fun adopt(items: List<MediaItem>, currentIndex: Int) {
        originalQueue = items
        sourcePositions = items.indices.toList()
        _state.update { it.copy(queue = items, currentIndex = currentIndex.takeIf { i -> i in items.indices }) }
    }
}
