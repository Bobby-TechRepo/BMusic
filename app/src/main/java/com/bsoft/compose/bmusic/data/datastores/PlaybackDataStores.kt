package com.bsoft.compose.bmusic.data.datastores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.bsoft.compose.bmusic.data.playback.JsonDataSerializer
import com.bsoft.compose.bmusic.data.playback.PlaybackPositionData
import com.bsoft.compose.bmusic.data.playback.PlaybackQueueData

// A corrupt snapshot is not worth a crash: start from an empty queue instead.

val Context.playbackQueueDataStore: DataStore<PlaybackQueueData> by dataStore(
    fileName = "playback_queue.json",
    serializer = JsonDataSerializer(PlaybackQueueData(), PlaybackQueueData.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { PlaybackQueueData() },
)

val Context.playbackPositionDataStore: DataStore<PlaybackPositionData> by dataStore(
    fileName = "playback_position.json",
    serializer = JsonDataSerializer(PlaybackPositionData(), PlaybackPositionData.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { PlaybackPositionData() },
)
