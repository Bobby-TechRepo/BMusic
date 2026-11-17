package com.bsoft.compose.bmusic.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures how much the Baseline Profile helps: cold start-up and scrolling, each compiled
 * without a profile and with it. Run with
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`; Android Studio shows the
 * timings (median and percentiles) in the test results.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollSongsWithoutProfile() = scrollSongs(CompilationMode.None())

    @Test
    fun scrollSongsWithProfile() = scrollSongs(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            grantLibraryPermissions()
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForHome()
    }

    private fun scrollSongs(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            grantLibraryPermissions()
            pressHome()
            startActivityAndWait()
            waitForHome()
            openTab("songs")
        }
    ) {
        flingList(androidx.test.uiautomator.By.res("songs_list"))
    }
}
