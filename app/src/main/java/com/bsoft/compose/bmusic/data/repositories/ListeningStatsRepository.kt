package com.bsoft.compose.bmusic.data.repositories

import com.bsoft.compose.bmusic.data.doas.ListenEventDao
import com.bsoft.compose.bmusic.data.entities.ArtistPlays
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.SongPlays
import com.bsoft.compose.bmusic.data.models.ActivityBucket
import com.bsoft.compose.bmusic.data.models.ListeningActivity
import com.bsoft.compose.bmusic.data.models.Song
import java.time.LocalDate
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Time window for listening stats. */
enum class StatsPeriod(val days: Int?) {
    WEEK(7), MONTH(30), ALL_TIME(null);

    /** Start of the window, in epoch ms, for a given "now". */
    fun since(nowMs: Long): Long = days?.let { nowMs - it * 24L * 60 * 60 * 1000 } ?: 0L
}

data class ListeningStats(
    val plays: Int = 0,
    val distinctSongs: Int = 0,
    val listenedMs: Long = 0L,
    val topSongs: List<SongPlays> = emptyList(),
    val topArtists: List<ArtistPlays> = emptyList(),
    /** Bars for the activity chart: days for a week or month, months for all time. */
    val activity: List<ActivityBucket> = emptyList(),
    /** Days the period covers (since the first listen for all time), for daily averages. */
    val days: Int = 0,
) {
    val isEmpty: Boolean get() = plays == 0

    /** Average listening time per day of the period. */
    val dailyAverageMs: Long get() = if (days > 0) listenedMs / days else 0L
}

@Singleton
class ListeningStatsRepository @Inject constructor(private val dao: ListenEventDao) {
    /** Records one counted listen. Called by the playback service. */
    suspend fun record(song: Song, atMs: Long = System.currentTimeMillis()) {
        dao.insert(
            ListenEventEntity(
                song = song.id, title = song.title, artist = song.artist, album = song.album,
                listenedAt = atMs, durationMs = song.duration.coerceAtLeast(0L)
            )
        )
    }

    suspend fun stats(
        period: StatsPeriod,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 10,
        zoneOffsetMs: Long = TimeZone.getDefault().getOffset(nowMs).toLong(),
    ): ListeningStats {
        val since = period.since(nowMs)
        val today = LocalDate.ofEpochDay(Math.floorDiv(nowMs + zoneOffsetMs, DAY_MS))
        // "All time" bars cover a year, so count that far back even for the totals' window.
        val activitySince = if (period.days == null) today.minusMonths(ListeningActivity.MONTHS.toLong()).withDayOfMonth(1)
            .toEpochDay() * DAY_MS - zoneOffsetMs else since
        val perDay = dao.playsPerDay(activitySince, zoneOffsetMs).associate { it.day to it.plays }
        val days = period.days ?: dao.firstListenAt()?.let { first ->
            (today.toEpochDay() - Math.floorDiv(first + zoneOffsetMs, DAY_MS) + 1).toInt().coerceAtLeast(1)
        } ?: 0
        return ListeningStats(
            plays = dao.playsSince(since),
            distinctSongs = dao.distinctSongsSince(since),
            listenedMs = dao.listenedMsSince(since),
            topSongs = dao.topSongsSince(since, limit),
            topArtists = dao.topArtistsSince(since, limit / 2),
            activity = ListeningActivity.buckets(period.days, perDay, today),
            days = days,
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
