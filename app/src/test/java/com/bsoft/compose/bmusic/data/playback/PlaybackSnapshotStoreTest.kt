package com.bsoft.compose.bmusic.data.playback

import androidx.datastore.core.DataStore
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import com.bsoft.compose.bmusic.data.serializables.AppSettingsData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore that counts writes. */
private class FakeDataStore<T>(initial: T) : DataStore<T> {
    val state = MutableStateFlow(initial)
    var writes = 0
    override val data: Flow<T> = state
    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        writes++
        return transform(state.value).also { state.value = it }
    }
}

/**
 * The store's writers run in [kotlinx.coroutines.test.TestScope.backgroundScope]. `advanceUntilIdle()`
 * does not run background-only work, so these tests use `runCurrent()`, which does.
 */
@ExperimentalCoroutinesApi
class PlaybackSnapshotStoreTest {
    private val legacy: AppSettingsPreferences = mockk(relaxed = true)

    @Test
    fun `saved queue and position load back as one snapshot`() = runTest {
        val queue = FakeDataStore(PlaybackQueueData())
        val position = FakeDataStore(PlaybackPositionData())
        val store = PlaybackSnapshotStore(queue, position, legacy, backgroundScope)

        store.saveQueue(listOf("album_5#1|11", "album_5#0|10"), listOf(1, 0), "album_5", shuffle = true)
        store.savePosition(index = 1, positionMs = 42_000, playWhenReady = true, repeatMode = 2)
        runCurrent()

        val snapshot = store.load()!!
        assertEquals(listOf("album_5#1|11", "album_5#0|10"), snapshot.mediaIds)
        assertEquals(listOf(1, 0), snapshot.sourceOrder)
        assertEquals("album_5", snapshot.context)
        assertTrue(snapshot.shuffle)
        assertEquals(1, snapshot.index)
        assertEquals(42_000L, snapshot.positionMs)
        assertEquals(2, snapshot.repeatMode)
    }

    @Test
    fun `identical queue saves are written once`() = runTest {
        val queue = FakeDataStore(PlaybackQueueData())
        val store = PlaybackSnapshotStore(queue, FakeDataStore(PlaybackPositionData()), legacy, backgroundScope)

        repeat(5) {
            store.saveQueue(listOf("1", "2"), listOf(0, 1), "songs", shuffle = false)
            runCurrent()
        }
        assertEquals(1, queue.writes)
    }

    @Test
    fun `position saves do not rewrite the queue`() = runTest {
        val queue = FakeDataStore(PlaybackQueueData(mediaIds = listOf("1")))
        val position = FakeDataStore(PlaybackPositionData())
        val store = PlaybackSnapshotStore(queue, position, legacy, backgroundScope)

        store.savePosition(0, 1_000, true, 0); runCurrent()
        store.savePosition(0, 16_000, true, 0); runCurrent()

        assertEquals(0, queue.writes)
        assertEquals(16_000L, position.state.value.positionMs)
    }

    @Test
    fun `mismatched source order is dropped`() = runTest {
        val queue = FakeDataStore(PlaybackQueueData())
        val store = PlaybackSnapshotStore(queue, FakeDataStore(PlaybackPositionData()), legacy, backgroundScope)
        store.saveQueue(listOf("1", "2", "3"), listOf(0, 1), "songs", shuffle = true)
        runCurrent()
        assertTrue(queue.state.value.sourceOrder.isEmpty())
    }

    @Test
    fun `legacy snapshot is migrated once and cleared`() = runTest {
        every { legacy.data } returns flowOf(
            AppSettingsData(
                lastQueueMediaIds = listOf("7", "8"), lastQueueIndex = 1, lastPositionMs = 5_000,
                lastShuffleMode = true, lastQueueContext = "favourites", lastRepeatMode = 1
            )
        )
        val queue = FakeDataStore(PlaybackQueueData())
        val store = PlaybackSnapshotStore(queue, FakeDataStore(PlaybackPositionData()), legacy, backgroundScope)

        val first = store.load()!!
        assertEquals(listOf("7", "8"), first.mediaIds)
        assertEquals("favourites", first.context)
        assertEquals(1, first.index)
        assertTrue(first.sourceOrder.isEmpty())
        verify { legacy.clearLegacyPlaybackSnapshot() }

        // Never read again, even though the mocked legacy data still has a queue.
        assertNull(store.load())
        assertTrue(queue.state.value.migratedLegacy)
    }

    @Test
    fun `nothing saved and no legacy data means nothing to restore`() = runTest {
        every { legacy.data } returns flowOf(AppSettingsData())
        val store = PlaybackSnapshotStore(
            FakeDataStore(PlaybackQueueData()), FakeDataStore(PlaybackPositionData()), legacy, backgroundScope
        )
        assertNull(store.load())
    }

    @Test
    fun `speed is saved with the position and clamped`() = runTest {
        val store = PlaybackSnapshotStore(
            FakeDataStore(PlaybackQueueData()), FakeDataStore(PlaybackPositionData()), legacy, backgroundScope
        )
        assertEquals(1f, store.loadSpeed())

        store.savePosition(0, 0L, false, 0, speed = 1.5f); runCurrent()
        assertEquals(1.5f, store.loadSpeed())

        store.savePosition(0, 0L, false, 0, speed = 10f); runCurrent()
        assertEquals(MAX_SPEED, store.loadSpeed())
    }

    @Test
    fun `snapshot carries the speed`() = runTest {
        val store = PlaybackSnapshotStore(
            FakeDataStore(PlaybackQueueData()), FakeDataStore(PlaybackPositionData()), legacy, backgroundScope
        )
        store.saveQueue(listOf("10"), emptyList(), "songs", shuffle = false)
        store.savePosition(0, 5_000L, true, 0, speed = 0.75f)
        runCurrent()
        assertEquals(0.75f, store.load()!!.speed)
    }
}
