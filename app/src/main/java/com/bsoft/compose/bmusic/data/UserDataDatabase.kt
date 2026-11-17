package com.bsoft.compose.bmusic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bsoft.compose.bmusic.data.doas.FavouritesDao
import com.bsoft.compose.bmusic.data.doas.ListenEventDao
import com.bsoft.compose.bmusic.data.doas.PlayerCounterDao
import com.bsoft.compose.bmusic.data.doas.PlaylistDao
import com.bsoft.compose.bmusic.data.entities.FavouriteEntity
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import com.bsoft.compose.bmusic.data.entities.PlayCounterEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import com.bsoft.compose.bmusic.data.entities.PlaylistSongEntity

@Database(
    entities = [FavouriteEntity::class, PlayCounterEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, ListenEventEntity::class],
    version = 4,
    exportSchema = true
)
abstract class UserDataDatabase: RoomDatabase() {
    companion object {
        /** Preserve existing user playlists while adding the album-art lookup key. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN album_id INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** Adds a user-editable order to playlists, numbering existing rows in insertion order. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE playlist_songs SET position = (
                        SELECT COUNT(*) FROM playlist_songs AS earlier
                        WHERE earlier.playlist = playlist_songs.playlist AND earlier.rowid < playlist_songs.rowid
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the listening history used by listening stats. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `listen_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `song` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, " +
                        "`listened_at` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_events_listened_at` ON `listen_events` (`listened_at`)")
            }
        }
    }

    abstract fun favouriteDao(): FavouritesDao
    abstract fun playerCounterDao(): PlayerCounterDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listenEventDao(): ListenEventDao
}