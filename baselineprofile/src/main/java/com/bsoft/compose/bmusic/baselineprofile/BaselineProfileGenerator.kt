package com.bsoft.compose.bmusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code BMusic runs at start-up and while browsing, so it can be compiled ahead of
 * time on install. Run with `./gradlew :app:generateReleaseBaselineProfile` on a phone that has
 * some music; the result is written to app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage,
        // Also marks start-up code for the dex layout optimisation (Startup Profile).
        includeInStartupProfile = true
    ) {
        grantLibraryPermissions()
        pressHome()
        startActivityAndWait()
        waitForHome()

        browseSongs()
        browseAlbums()
        browseArtists()
        browsePlaylists()
        browseFolders()
        playAndOpenPlayer()
    }
}
