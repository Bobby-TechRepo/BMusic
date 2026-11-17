package com.bsoft.compose.bmusic.data.doas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bsoft.compose.bmusic.data.entities.ArtistPlays
import com.bsoft.compose.bmusic.data.entities.DayPlays
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.SongPlays

@Dao
interface ListenEventDao {
    @Insert
    suspend fun insert(vararg events: ListenEventEntity)

    @Query("SELECT COUNT(*) FROM listen_events WHERE listened_at >= :since")
    suspend fun playsSince(since: Long): Int

    @Query("SELECT COUNT(DISTINCT song) FROM listen_events WHERE listened_at >= :since")
    suspend fun distinctSongsSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM listen_events WHERE listened_at >= :since")
    suspend fun listenedMsSince(since: Long): Long

    @Query(
        """
        SELECT song, title, artist, COUNT(*) AS plays FROM listen_events
        WHERE listened_at >= :since
        GROUP BY song ORDER BY plays DESC, MAX(listened_at) DESC LIMIT :limit
        """
    )
    suspend fun topSongsSince(since: Long, limit: Int): List<SongPlays>

    @Query(
        """
        SELECT artist, COUNT(*) AS plays FROM listen_events
        WHERE listened_at >= :since
        GROUP BY artist ORDER BY plays DESC, MAX(listened_at) DESC LIMIT :limit
        """
    )
    suspend fun topArtistsSince(since: Long, limit: Int): List<ArtistPlays>

    /** Plays per local day; [offsetMs] is the time zone's offset from UTC. */
    @Query(
        """
        SELECT (listened_at + :offsetMs) / 86400000 AS day, COUNT(*) AS plays FROM listen_events
        WHERE listened_at >= :since
        GROUP BY day ORDER BY day
        """
    )
    suspend fun playsPerDay(since: Long, offsetMs: Long): List<DayPlays>

    @Query("SELECT MIN(listened_at) FROM listen_events")
    suspend fun firstListenAt(): Long?

    @Query("SELECT * FROM listen_events ORDER BY listened_at ASC")
    suspend fun all(): List<ListenEventEntity>

    @Query("SELECT COUNT(*) FROM listen_events WHERE song = :song AND listened_at = :listenedAt")
    suspend fun countAt(song: Long, listenedAt: Long): Int
}
