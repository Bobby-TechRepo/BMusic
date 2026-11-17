package com.bsoft.compose.bmusic.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bsoft.compose.bmusic.data.entities.ListenEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades real on-disk databases from older versions to the current one and lets Room check
 * the result. Room compares every table, column, default, key and index with the entities when
 * it opens the database, so a migration that leaves any difference fails here instead of on a
 * user's phone.
 *
 * The old schemas are written out by hand, as Room generated them for the entities of that
 * version (schemas were not exported before version 4).
 *
 * Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class UserDataDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun upgradeFromVersion1KeepsEverything() = runBlocking {
        createDatabase(version = 1, schema = V1_SCHEMA, data = SAMPLE_DATA_V1)
        checkUpgradedData(expectedAlbumId = -1L)
    }

    @Test
    fun upgradeFromVersion2KeepsEverything() = runBlocking {
        createDatabase(version = 2, schema = V2_SCHEMA, data = SAMPLE_DATA_V2)
        checkUpgradedData(expectedAlbumId = 77L)
    }

    @Test
    fun upgradeFromVersion3AddsListeningHistory() = runBlocking {
        createDatabase(version = 3, schema = V3_SCHEMA, data = SAMPLE_DATA_V2)
        openLatest().useAndClose { db ->
            assertEquals(0, db.listenEventDao().playsSince(0L))
            db.listenEventDao().insert(
                ListenEventEntity(song = 11, title = "One", artist = "A", album = "X", listenedAt = 1_000L, durationMs = 200_000L)
            )
            assertEquals(1, db.listenEventDao().playsSince(0L))
        }
    }

    private suspend fun checkUpgradedData(expectedAlbumId: Long) {
        openLatest().useAndClose { db ->
            assertEquals(listOf(11L), db.favouriteDao().getAll().first().map { it.song })
            assertEquals(5, db.playerCounterDao().getAll().first().single().played)

            val playlist = db.playlistDao().getPlaylists().first().single()
            assertEquals("Road trip", playlist.name)

            // Version 3 numbers existing songs in the order they were added.
            val songs = db.playlistDao().getPlaylistSongs(playlist.id)
            assertEquals(listOf(13L, 11L, 12L), songs.map { it.id })
            assertEquals(listOf(0, 1, 2), songs.map { it.position })
            assertEquals(expectedAlbumId, songs.first().albumId)

            assertEquals(0, db.listenEventDao().playsSince(0L))
        }
    }

    /** Writes a database file as an older app version would have left it. */
    private fun createDatabase(version: Int, schema: List<String>, data: List<String>) {
        // Plain framework SQLite, so the file is exactly what Room will find on an old install.
        val helper = object : SQLiteOpenHelper(context, name, null, version) {
            override fun onCreate(db: SQLiteDatabase) {
                (schema + data).forEach { db.execSQL(it) }
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase
        helper.close()
    }

    /** Opens the file with the app's real migrations; Room validates the schema on open. */
    private fun openLatest(): UserDataDatabase =
        Room.databaseBuilder(context, UserDataDatabase::class.java, name)
            .addMigrations(UserDataDatabase.MIGRATION_1_2, UserDataDatabase.MIGRATION_2_3, UserDataDatabase.MIGRATION_3_4)
            .build()

    private inline fun <T> UserDataDatabase.useAndClose(block: (UserDataDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private companion object {
        val COMMON_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `favourites` (`song` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`favourite` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`song`))",
            "CREATE TABLE IF NOT EXISTS `player_counter` (`song` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`played` INTEGER NOT NULL, `last_played` INTEGER NOT NULL, PRIMARY KEY(`song`))",
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `duration` INTEGER, `count` INTEGER NOT NULL, `checked` INTEGER NOT NULL)",
        )

        private const val PLAYLIST_SONGS_V1_COLUMNS =
            "`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `display_name` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                "`album` TEXT NOT NULL, `duration` INTEGER NOT NULL, `playlist` INTEGER NOT NULL"

        val V1_SCHEMA = COMMON_TABLES + listOf(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` ($PLAYLIST_SONGS_V1_COLUMNS, PRIMARY KEY(`id`, `playlist`))"
        )

        val V2_SCHEMA = COMMON_TABLES + listOf(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` ($PLAYLIST_SONGS_V1_COLUMNS, " +
                "`album_id` INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(`id`, `playlist`))"
        )

        val V3_SCHEMA = COMMON_TABLES + listOf(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` ($PLAYLIST_SONGS_V1_COLUMNS, " +
                "`album_id` INTEGER NOT NULL DEFAULT -1, `position` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`, `playlist`))"
        )

        private val SHARED_ROWS = listOf(
            "INSERT INTO favourites (song, title, favourite) VALUES (11, 'One', 1)",
            "INSERT INTO player_counter (song, title, played, last_played) VALUES (11, 'One', 5, 1000)",
            "INSERT INTO playlists (id, title, duration, count, checked) VALUES (1, 'Road trip', 600000, 3, 0)",
        )

        /** Songs added out of ID order, so the numbering has to follow insertion order. */
        val SAMPLE_DATA_V1 = SHARED_ROWS + listOf(13L, 11L, 12L).map { id ->
            "INSERT INTO playlist_songs (id, title, display_name, artist, album, duration, playlist) " +
                "VALUES ($id, 'Song $id', 'song$id.mp3', 'A', 'X', 200000, 1)"
        }

        val SAMPLE_DATA_V2 = SHARED_ROWS + listOf(13L, 11L, 12L).map { id ->
            "INSERT INTO playlist_songs (id, title, display_name, artist, album, duration, playlist, album_id) " +
                "VALUES ($id, 'Song $id', 'song$id.mp3', 'A', 'X', 200000, 1, 77)"
        }
    }
}
