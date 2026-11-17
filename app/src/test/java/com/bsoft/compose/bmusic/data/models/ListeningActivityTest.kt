package com.bsoft.compose.bmusic.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListeningActivityTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `a week is seven days ending today, gaps filled with zero`() {
        val plays = mapOf(today.toEpochDay() to 5, today.minusDays(6).toEpochDay() to 2, today.minusDays(9).toEpochDay() to 9)
        val bars = ListeningActivity.buckets(7, plays, today)
        assertEquals(7, bars.size)
        assertEquals(today.minusDays(6), bars.first().start)
        assertEquals(today, bars.last().start)
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 5), bars.map { it.plays })
    }

    @Test
    fun `all time is twelve months ending this month`() {
        val plays = mapOf(
            LocalDate.of(2026, 11, 1).toEpochDay() to 3,
            LocalDate.of(2026, 11, 10).toEpochDay() to 4,
            LocalDate.of(2026, 2, 14).toEpochDay() to 6,
            LocalDate.of(2025, 1, 1).toEpochDay() to 100, // older than a year: not shown
        )
        val bars = ListeningActivity.buckets(null, plays, today)
        assertEquals(12, bars.size)
        assertTrue(bars.all { it.monthly })
        assertEquals(LocalDate.of(2025, 12, 1), bars.first().start)
        assertEquals(LocalDate.of(2026, 11, 1), bars.last().start)
        assertEquals(7, bars.last().plays)
        assertEquals(6, bars.first { it.start.monthValue == 2 }.plays)
        assertEquals(13, bars.sumOf { it.plays })
    }

    @Test
    fun `peak is the busiest bar, or none`() {
        val bars = ListeningActivity.buckets(3, mapOf(today.minusDays(1).toEpochDay() to 4, today.toEpochDay() to 4), today)
        assertEquals(1, ListeningActivity.peakIndex(bars))
        assertEquals(-1, ListeningActivity.peakIndex(ListeningActivity.buckets(3, emptyMap(), today)))
    }
}
