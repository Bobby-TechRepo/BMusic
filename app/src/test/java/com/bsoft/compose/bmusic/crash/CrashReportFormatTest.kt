package com.bsoft.compose.bmusic.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatTest {
    private val context = CrashContext(appVersion = "1.2.0", versionCode = 12, device = "Google Pixel 9", androidVersion = "16", sdk = 36)

    @Test
    fun `report has the app, phone and stack trace`() {
        val error = IllegalStateException("Queue is empty", RuntimeException("root cause"))
        val report = CrashReportFormat.format(error, "main", context, timeMs = 0L)
        val lines = report.lines()
        assertEquals("BMusic crash report", lines[0])
        assertEquals("Time: 1970-01-01 00:00:00 UTC", lines[1])
        assertEquals("App: 1.2.0 (12)", lines[2])
        assertEquals("Device: Google Pixel 9", lines[3])
        assertEquals("Android: 16 (API 36)", lines[4])
        assertEquals("Thread: main", lines[5])
        assertTrue(report.contains("java.lang.IllegalStateException: Queue is empty"))
        assertTrue(report.contains("Caused by: java.lang.RuntimeException: root cause"))
    }

    @Test
    fun `huge reports are cut short`() {
        val error = RuntimeException("x".repeat(CrashReportFormat.MAX_CHARS * 2))
        val report = CrashReportFormat.format(error, "main", context, timeMs = 0L)
        assertTrue(report.length <= CrashReportFormat.MAX_CHARS + 20)
        assertTrue(report.endsWith("(truncated)\n"))
    }
}
