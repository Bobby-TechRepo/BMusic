package com.bsoft.compose.bmusic.data.preferences

import android.content.Context
import com.bsoft.compose.bmusic.data.datastores.appSettingsDataStore
import com.bsoft.compose.bmusic.data.serializables.AppSettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

const val MAX_RECENT_SEARCHES = 8

class AppSettingsPreferences(private val context: Context) {
    val data: Flow<AppSettingsData>
        get() = context.appSettingsDataStore.data

    fun setAccentColor(color: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(accentColor = color) }
        }
    }

    fun setLanguage(language: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(language = language) }
        }
    }

    fun setRememberLastTab(remember: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(rememberLastTab = remember) }
        }
    }

    fun setLastTab(tab: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(lastTab = tab) }
        }
    }

    fun setSongSort(sort: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(songSort = sort) }
        }
    }

    fun setHideShortSongs(hide: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(hideShortSongs = hide) }
        }
    }

    fun setShakeToChange(shake: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(shakeToChange = shake) }
        }
    }

    /** Puts [query] first in the search history (no duplicates, at most [MAX_RECENT_SEARCHES]). */
    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { settings ->
                val rest = settings.recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }
                settings.copy(recentSearches = (listOf(trimmed) + rest).take(MAX_RECENT_SEARCHES))
            }
        }
    }

    fun removeRecentSearch(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(recentSearches = it.recentSearches - query) }
        }
    }

    fun clearRecentSearches() {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(recentSearches = emptyList()) }
        }
    }

    fun setReplayGainMode(mode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(replayGainMode = mode) }
        }
    }

    fun setSoundFade(fade: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(soundFade = fade) }
        }
    }

    /** [mode] is a TransitionMode name. The old switch is kept in step for older backups. */
    fun setTransitionMode(mode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(transitionMode = mode, soundFade = mode == "FADE") }
        }
    }

    fun setTransitionSeconds(seconds: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(transitionSeconds = seconds) }
        }
    }

    fun setGaplessAlbums(gapless: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(gaplessAlbums = gapless) }
        }
    }

    fun setLastPlayedMediaId(mediaId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(lastPlayedMediaId = mediaId) }
        }
    }

    fun savePlaybackSnapshot(
        mediaIds: List<String>,
        currentIndex: Int,
        positionMs: Long,
        playWhenReady: Boolean,
        repeatMode: Int,
        shuffleMode: Boolean,
        queueContext: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { settings ->
                settings.copy(
                    lastQueueMediaIds = mediaIds,
                    lastQueueIndex = currentIndex.coerceAtLeast(0),
                    lastPositionMs = positionMs.coerceAtLeast(0L),
                    lastPlayWhenReady = playWhenReady,
                    lastRepeatMode = repeatMode,
                    lastShuffleMode = shuffleMode,
                    lastQueueContext = queueContext
                )
            }
        }
    }

    /**
     * Empties the queue snapshot that earlier builds stored here. Playback state now lives in
     * PlaybackSnapshotStore; this file keeps only user settings.
     */
    fun clearLegacyPlaybackSnapshot() {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(lastQueueMediaIds = emptyList()) }
        }
    }

    fun setLastRepeatMode(mode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(lastRepeatMode = mode) }
        }
    }

    fun setLastShuffleMode(shuffle: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.appSettingsDataStore.updateData { it.copy(lastShuffleMode = shuffle) }
        }
    }
}
