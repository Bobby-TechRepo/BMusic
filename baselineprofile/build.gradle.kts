// Generates the app's Baseline Profile and measures start-up. Runs on a connected device:
//   ./gradlew :app:generateReleaseBaselineProfile           (writes the profile into :app)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   (start-up benchmark)
// Needs Android 13+ (or a rooted phone on Android 9 to 12). See docs/PHASE7_PERFORMANCE.md.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.bsoft.compose.bmusic.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Profile collection needs Android 9 (API 28) or newer.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

baselineProfile {
    // Use the phone or emulator you have plugged in, rather than a Gradle-managed device.
    useConnectedDevices = true
}

// Tell the tests the app's package name (so they never drift from :app's applicationId).
androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}

dependencies {
    implementation(libs.androidx.junit)
    // Brings androidx.test:runner, which provides the AndroidJUnitRunner named above.
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
