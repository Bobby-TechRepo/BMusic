package com.bsoft.compose.bmusic.baselineprofile

import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/*
 * The paths a listener takes most: open the app, scroll songs, open an album and an artist,
 * look at playlists, start a song and open the player. Shared by the profile generator and the
 * benchmarks. Every step is optional, so an empty library or a tablet layout (no tabs) still
 * produces a profile instead of failing.
 *
 * The tags below mirror com.bsoft.compose.bmusic.ui.components.TestTags in :app.
 */

private const val WAIT_MS = 5_000L

/** The app under test, passed in by Gradle (see build.gradle.kts). */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: "com.bsoft.compose.bmusic"

/** Grants library access up front, so the permission screen never blocks the journey. */
fun MacrobenchmarkScope.grantLibraryPermissions() {
    val permissions = if (Build.VERSION.SDK_INT >= 33) {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
    } else {
        listOf("android.permission.READ_EXTERNAL_STORAGE")
    }
    permissions.forEach { device.executeShellCommand("pm grant $packageName $it") }
}

/** Waits until Home has drawn its pages. */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.res("home_pager")), WAIT_MS)
}

fun MacrobenchmarkScope.openTab(route: String): Boolean {
    val tab = device.findObject(By.res("tab_$route")) ?: return false
    tab.click()
    device.waitForIdle()
    return true
}

/** Flings a list down and back up, which draws many rows (and loads their artwork). */
fun MacrobenchmarkScope.flingList(selector: BySelector) {
    val list = device.wait(Until.findObject(selector), WAIT_MS) ?: return
    // Keep the gesture away from the screen edges (system back / notification gestures).
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** Opens the first item matching [selector], scrolls its screen, and goes back. */
fun MacrobenchmarkScope.openFirstAndBack(selector: BySelector) {
    val item = device.wait(Until.findObject(selector), WAIT_MS) ?: return
    item.click()
    if (device.wait(Until.hasObject(By.res("detail_list")), WAIT_MS) == true) {
        flingList(By.res("detail_list"))
    }
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.browseSongs() {
    if (!openTab("songs")) return
    flingList(By.res("songs_list"))
}

fun MacrobenchmarkScope.browseAlbums() {
    if (!openTab("album")) return
    flingList(By.res("albums_grid"))
    openFirstAndBack(By.res("album_tile"))
}

fun MacrobenchmarkScope.browseArtists() {
    if (!openTab("artists")) return
    flingList(By.res("artists_list"))
    openFirstAndBack(By.res("artist_row"))
}

fun MacrobenchmarkScope.browsePlaylists() {
    if (!openTab("playlists")) return
    flingList(By.res("playlists_grid"))
}

fun MacrobenchmarkScope.browseFolders() {
    if (!openTab("folders")) return
    flingList(By.res("folders_list"))
}

/** Plays the first song, opens the full player, then returns to Home. */
fun MacrobenchmarkScope.playAndOpenPlayer() {
    if (!openTab("songs")) return
    val song = device.wait(Until.findObject(By.res("song_row")), WAIT_MS) ?: return
    song.click()
    val miniPlayer = device.wait(Until.findObject(By.res("mini_player")), WAIT_MS) ?: return
    miniPlayer.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
