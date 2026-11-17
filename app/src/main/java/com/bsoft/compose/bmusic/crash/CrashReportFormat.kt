package com.bsoft.compose.bmusic.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What a crash report says about the app and phone (no personal data). */
data class CrashContext(
    val appVersion: String,
    val versionCode: Long,
    val device: String,
    val androidVersion: String,
    val sdk: Int,
)

/** Plain-text crash report, readable in an email or a GitHub issue. Pure, so it is unit-tested. */
object CrashReportFormat {
    /** Longest report kept; deep recursion can produce megabytes of identical frames. */
    const val MAX_CHARS = 32_000

    fun format(error: Throwable, threadName: String, context: CrashContext, timeMs: Long): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timeMs))
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("BMusic crash report")
            appendLine("Time: $time")
            appendLine("App: ${context.appVersion} (${context.versionCode})")
            appendLine("Device: ${context.device}")
            appendLine("Android: ${context.androidVersion} (API ${context.sdk})")
            appendLine("Thread: $threadName")
            appendLine()
            append(trace.trimEnd())
            appendLine()
        }
        return if (report.length <= MAX_CHARS) report else report.take(MAX_CHARS) + "\n… (truncated)\n"
    }
}
