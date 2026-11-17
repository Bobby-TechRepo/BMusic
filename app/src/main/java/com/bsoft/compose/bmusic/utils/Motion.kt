package com.bsoft.compose.bmusic.utils

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * True when the user turned animations off (Settings > Accessibility > Remove animations, or
 * the developer "Animator duration scale" set to off). Decorative motion, such as the
 * now-playing bars and the wavy seek line, then stays still. Re-read when the app resumes, so
 * changing the setting takes effect without a restart.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    var reduce by remember { mutableStateOf(read()) }
    LifecycleResumeEffect(Unit) {
        reduce = read()
        onPauseOrDispose { }
    }
    return reduce
}
