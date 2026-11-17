package com.bsoft.compose.bmusic.data.playback

import android.util.Log
import androidx.datastore.core.DataStore
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PlaybackSnapshotStore"

/** Speeds the player accepts from a saved snapshot. */
const val MIN_SPEED = 0.25f
const val MAX_SPEED = 3f

/**
 * Saves and restores the playback queue and position. The playback service is its only writer.
 *
 * Writes are conflated: rapid changes (dragging a queue entry, a burst of timeline events) end
 * in one write of the latest value, and an unchanged queue is never written twice.
 */
class PlaybackSnapshotStore(
    private val queueStore: DataStore<PlaybackQueueData>,
    private val positionStore: DataStore<PlaybackPositionData>,
    private val legacySettings: AppSettingsPreferences,
    scope: CoroutineScope,
) {
    private val pendingQueue = MutableStateFlow<PlaybackQueueData?>(null)
    private val pendingPosition = MutableStateFlow<PlaybackPositionData?>(null)

    init {
        scope.launch {
            // StateFlow drops values equal to the previous one, so identical saves are skipped.
            pendingQueue.filterNotNull().collect { data ->
                safely("queue") {
                    queueStore.updateData { current -> data.copy(migratedLegacy = current.migratedLegacy || data.migratedLegacy) }
                }
            }
        }
        scope.launch {
            pendingPosition.filterNotNull().collect { data ->
                safely("position") { positionStore.updateData { data } }
            }
        }
    }

    fun saveQueue(mediaIds: List<String>, sourceOrder: List<Int>, context: String, shuffle: Boolean) {
        val order = if (sourceOrder.size == mediaIds.size) sourceOrder else emptyList()
        pendingQueue.value = PlaybackQueueData(mediaIds, order, context, shuffle)
    }

    fun savePosition(index: Int, positionMs: Long, playWhenReady: Boolean, repeatMode: Int, speed: Float = 1f) {
        pendingPosition.value = PlaybackPositionData(
            index = index.coerceAtLeast(0),
            positionMs = positionMs.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
            repeatMode = repeatMode,
            speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        )
    }

    /** The saved playback speed (1 when none). Separate from [load] so it applies to an empty queue too. */
    suspend fun loadSpeed(): Float = try {
        positionStore.data.first().speed.coerceIn(MIN_SPEED, MAX_SPEED)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        1f
    }

    /** The last saved snapshot, or null when there is nothing to restore. */
    suspend fun load(): PlaybackSnapshot? = try {
        val queue = queueStore.data.first()
        if (queue.mediaIds.isNotEmpty()) {
            val position = positionStore.data.first()
            PlaybackSnapshot(
                mediaIds = queue.mediaIds,
                sourceOrder = queue.sourceOrder,
                context = queue.context,
                shuffle = queue.shuffle,
                index = position.index,
                positionMs = position.positionMs,
                playWhenReady = position.playWhenReady,
                repeatMode = position.repeatMode,
                speed = position.speed,
            )
        } else if (!queue.migratedLegacy) {
            loadLegacy()
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the playback snapshot", e)
        null
    }

    /** One-time read of the snapshot that earlier builds kept inside app_settings.json. */
    private suspend fun loadLegacy(): PlaybackSnapshot? {
        val old = legacySettings.data.first()
        queueStore.updateData { it.copy(migratedLegacy = true) }
        if (old.lastQueueMediaIds.isEmpty()) return null
        legacySettings.clearLegacyPlaybackSnapshot()
        return PlaybackSnapshot(
            mediaIds = old.lastQueueMediaIds,
            sourceOrder = emptyList(), // Earlier builds did not save the unshuffled order.
            context = old.lastQueueContext,
            shuffle = old.lastShuffleMode,
            index = old.lastQueueIndex,
            positionMs = old.lastPositionMs,
            playWhenReady = old.lastPlayWhenReady,
            repeatMode = old.lastRepeatMode,
        )
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the playback $what", e)
        }
    }
}
