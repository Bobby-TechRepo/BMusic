package com.bsoft.compose.bmusic.crash

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.bsoft.compose.bmusic.R
import java.io.File

private const val TAG = "CrashReporter"

/**
 * Keeps the last crash on the phone so the user can choose to share it from Settings. Nothing is
 * sent anywhere automatically: BMusic has no internet permission and no analytics.
 */
object CrashReporter {
    private const val DIRECTORY = "crash"
    private const val FILE_NAME = "last_crash.txt"

    /** Call once from Application.onCreate, before anything else can crash. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = CrashReportFormat.format(error, thread.name, crashContext(app), System.currentTimeMillis())
                file(app).apply { parentFile?.mkdirs() }.writeText(report)
            } catch (t: Throwable) {
                Log.e(TAG, "Could not save the crash report", t)
            }
            // Let Android show its dialog / end the process as usual.
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null when BMusic has not crashed since it was last cleared. */
    fun lastReport(context: Context): String? = file(context).takeIf { it.isFile }?.let {
        try {
            it.readText()
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** A share sheet with the report as text, e.g. for email or a messaging app. */
    fun shareIntent(context: Context, report: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        return Intent.createChooser(send, context.getString(R.string.crash_report_share))
    }

    private fun file(context: Context) = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    @Suppress("DEPRECATION") // The older calls are only used on the versions that need them.
    private fun crashContext(context: Context): CrashContext {
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
        val versionCode = when {
            info == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
            else -> info.versionCode.toLong()
        }
        return CrashContext(
            appVersion = info?.versionName ?: "?",
            versionCode = versionCode,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
        )
    }
}
