package com.bsoft.compose.bmusic.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.data.serializables.AppSettingsData
import com.bsoft.compose.bmusic.ui.Main
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import com.bsoft.compose.bmusic.viewmodels.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bsoft.compose.bmusic.viewmodels.EqualizerViewModel
import com.bsoft.compose.bmusic.viewmodels.PlayingViewModel
import com.bsoft.compose.bmusic.viewmodels.SongsViewModel
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import com.bsoft.compose.bmusic.utils.ShortcutAction
import com.bsoft.compose.bmusic.utils.shortcutFor
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
/**
 * AppCompatActivity (not ComponentActivity) so AppCompatDelegate.setApplicationLocales can apply
 * the in-app language on Android 12 and below; Android 13+ uses the system per-app language.
 */
class MainActivity : AppCompatActivity() {
    /** Keyboard shortcuts, handed to the UI (see utils/KeyboardShortcuts.kt). */
    private val shortcuts = MutableSharedFlow<ShortcutAction>(extraBufferCapacity = 8)

    /** A music file opened from another app ("Open with BMusic"), waiting to be played. */
    private val openedFile = MutableStateFlow<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        openedFile.value = intent.data ?: return
    }

    /** Only called for keys nothing on screen used (a text field, a focused button...). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = shortcutFor(keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed)
        if (action != null) {
            // Holding a key down must not toggle play/pause over and over.
            if (event.repeatCount == 0) shortcuts.tryEmit(action)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    //private val settingsViewModel: SettingsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the file is already playing.
        if (savedInstanceState == null) handleViewIntent(intent)

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val windowSizeClass = calculateWindowSizeClass(this)
            
            // Phones stay portrait (the player layouts are built for it). Tablets and foldables
            // follow the device: Android 16+ ignores orientation locks on large screens anyway.
            val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            SideEffect {
                val wanted = if (isTablet) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (requestedOrientation != wanted) requestedOrientation = wanted
            }

            val settingsState by settingsViewModel.settings.collectAsStateWithLifecycle()
            val settings = settingsState ?: AppSettingsData()
            // "Dynamic" uses Material You wallpaper colours on Android 12+; older versions fall back to blue.
            val useDynamic = settings.accentColor == AppSettingsData.ACCENT_DYNAMIC &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val accentColor = when {
                useDynamic -> null
                settings.accentColor == AppSettingsData.ACCENT_DYNAMIC -> Color(AppSettingsData().accentColor)
                else -> Color(settings.accentColor)
            }


            val songsViewModel: SongsViewModel  = hiltViewModel()
            val playingViewModel: PlayingViewModel = hiltViewModel()
            val equalizerViewModel: EqualizerViewModel = hiltViewModel()
            //CompositionLocalProvider(LocalizedContext provides localizedContext) {
                BMusicTheme(dynamicColor = true, accentColor = accentColor) {
                    // Test tags double as resource IDs, so UI Automator (Baseline Profile
                    // journeys, macrobenchmarks) can find screens. No effect on users.
                    Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        Main(windowSizeClass = windowSizeClass,
                            playingViewModel = playingViewModel,
                            viewModel = songsViewModel,
                            equalizerViewModel = equalizerViewModel,
                            settingsViewModel = settingsViewModel,
                            shortcuts = shortcuts,
                            openedFile = openedFile,
                            onOpenedFileHandled = { openedFile.value = null })
                    }
                }
            //}
        }
    }
}
