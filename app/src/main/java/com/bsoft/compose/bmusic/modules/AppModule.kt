package com.bsoft.compose.bmusic.modules

import android.content.Context
import com.bsoft.compose.bmusic.data.AudioEffectsManager
import com.bsoft.compose.bmusic.data.EqualizerManager
import com.bsoft.compose.bmusic.data.QueueManager
import com.bsoft.compose.bmusic.data.datastores.playbackPositionDataStore
import com.bsoft.compose.bmusic.data.datastores.playbackQueueDataStore
import android.os.SystemClock
import com.bsoft.compose.bmusic.data.playback.PlaybackSnapshotStore
import com.bsoft.compose.bmusic.data.playback.SleepTimer
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import com.bsoft.compose.bmusic.data.preferences.EqualizerPreferences
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSongRepository(@ApplicationContext context: Context): SongRepository {
        return SongRepository(context)
    }

    @Provides
    @Singleton
    fun provideQueueManager()= QueueManager()

    @Provides
    @Singleton
    fun provideEqualizerManager()= EqualizerManager()

    @Provides
    @Singleton
    fun provideAudioEffectsManager() = AudioEffectsManager()

    @Provides
    @Singleton
    fun provideEqualizerPreferences(@ApplicationContext context: Context): EqualizerPreferences{
        return EqualizerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideAppSettingsPreferences(@ApplicationContext context: Context): AppSettingsPreferences {
        return AppSettingsPreferences(context)
    }

    @Provides
    @Singleton
    fun provideSleepTimer(): SleepTimer = SleepTimer(clock = { SystemClock.elapsedRealtime() })

    @Provides
    @Singleton
    fun providePlaybackSnapshotStore(
        @ApplicationContext context: Context,
        appSettingsPreferences: AppSettingsPreferences,
        @ApplicationScope scope: CoroutineScope
    ): PlaybackSnapshotStore = PlaybackSnapshotStore(
        queueStore = context.playbackQueueDataStore,
        positionStore = context.playbackPositionDataStore,
        legacySettings = appSettingsPreferences,
        scope = scope
    )
}
