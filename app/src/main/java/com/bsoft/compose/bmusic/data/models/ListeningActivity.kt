package com.bsoft.compose.bmusic.data.models

import java.time.LocalDate
import java.time.YearMonth

/** Plays in one bar of the listening-activity chart: a day, or a month for "all time". */
data class ActivityBucket(val start: LocalDate, val plays: Int, val monthly: Boolean = false)

/** Turns per-day play counts into the bars shown on the stats screen. */
object ListeningActivity {
    /** How many months "all time" shows. */
    const val MONTHS = 12

    /**
     * One bar per day for a [windowDays]-day window ending [today] (days without plays are 0),
     * or, when [windowDays] is null, one bar per month for the last [MONTHS] months.
     *
     * @param playsByEpochDay plays keyed by local epoch day ([LocalDate.toEpochDay])
     */
    fun buckets(windowDays: Int?, playsByEpochDay: Map<Long, Int>, today: LocalDate): List<ActivityBucket> {
        if (windowDays != null) {
            val first = today.minusDays((windowDays - 1).toLong())
            return (0 until windowDays).map { offset ->
                val day = first.plusDays(offset.toLong())
                ActivityBucket(day, playsByEpochDay[day.toEpochDay()] ?: 0)
            }
        }
        val thisMonth = YearMonth.from(today)
        val byMonth = playsByEpochDay.entries.groupBy({ YearMonth.from(LocalDate.ofEpochDay(it.key)) }, { it.value })
            .mapValues { (_, plays) -> plays.sum() }
        return (MONTHS - 1 downTo 0).map { back ->
            val month = thisMonth.minusMonths(back.toLong())
            ActivityBucket(month.atDay(1), byMonth[month] ?: 0, monthly = true)
        }
    }

    /** Index of the busiest bar (the first one on a tie), or -1 when nothing was played. */
    fun peakIndex(buckets: List<ActivityBucket>): Int {
        val max = buckets.maxOfOrNull { it.plays } ?: 0
        return if (max == 0) -1 else buckets.indexOfFirst { it.plays == max }
    }
}
