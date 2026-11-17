package com.bsoft.compose.bmusic.data.playback

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * The saved play queue. Written only when the queue itself changes (new list, add, remove, move,
 * shuffle), so position updates never rewrite thousands of IDs.
 */
@Serializable
data class PlaybackQueueData(
    /** Media IDs in play order. */
    val mediaIds: List<String> = emptyList(),
    /** For each entry, its position in the original unshuffled order (see QueueManager.sourceOrder). */
    val sourceOrder: List<Int> = emptyList(),
    val context: String = "songs",
    val shuffle: Boolean = false,
    /** True once the old snapshot inside app_settings.json has been read and cleared. */
    val migratedLegacy: Boolean = false,
)

/** Where playback was. Small and cheap to rewrite every few seconds. */
@Serializable
data class PlaybackPositionData(
    val index: Int = 0,
    val positionMs: Long = 0L,
    val playWhenReady: Boolean = false,
    val repeatMode: Int = 0,
    /** Playback speed; kept even when the queue is empty. Added in Phase 4b. */
    val speed: Float = 1f,
)

/** A complete snapshot ready to restore. */
data class PlaybackSnapshot(
    val mediaIds: List<String>,
    val sourceOrder: List<Int>,
    val context: String,
    val shuffle: Boolean,
    val index: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val speed: Float = 1f,
)

/** kotlinx.serialization JSON serializer for a typed DataStore. Unknown keys are ignored. */
class JsonDataSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unable to read playback snapshot", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}
