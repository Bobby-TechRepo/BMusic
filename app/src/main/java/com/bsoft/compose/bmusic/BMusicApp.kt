package com.bsoft.compose.bmusic

import android.app.Application
import com.bsoft.compose.bmusic.crash.CrashReporter
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import com.bsoft.compose.bmusic.data.repositories.SongRepository
import com.bsoft.compose.bmusic.modules.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BMusicApp : Application() {
    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var appSettingsPreferences: AppSettingsPreferences

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        // First, so even a crash during start-up is saved for the user to share.
        CrashReporter.install(this)
        super.onCreate() // Hilt injects fields here.
        // One process-wide MediaStore observer keeps the library cache fresh for both the UI
        // and the playback service (which may run without any screen open).
        songRepository.startObserving()
        // "Hide short songs" changes what the library contains, for the UI and the service alike.
        applicationScope.launch {
            appSettingsPreferences.data
                .map { it.hideShortSongs }
                .distinctUntilChanged()
                .collect { songRepository.setHideShortSongs(it) }
        }
    }
}
