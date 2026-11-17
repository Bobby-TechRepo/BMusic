package com.bsoft.compose.bmusic.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One counted listen (see PlayCountTracker), kept for listening stats. Title and artist are
 * copied so stats survive the file being deleted. Added in database version 4.
 */
@Entity(tableName = "listen_events", indices = [Index(value = ["listened_at"])])
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "song") val song: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String,
    /** Epoch milliseconds when the listen was counted. */
    @ColumnInfo(name = "listened_at") val listenedAt: Long,
    /** The song's length, used to estimate listening time. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

/** A song and how often it was played in a period. */
data class SongPlays(
    @ColumnInfo(name = "song") val song: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "plays") val plays: Int,
)

/** Plays on one local day; [day] is the epoch day after adding the time-zone offset. */
data class DayPlays(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "plays") val plays: Int,
)

/** An artist and how often their songs were played in a period. */
data class ArtistPlays(
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "plays") val plays: Int,
)
