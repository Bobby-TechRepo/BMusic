package com.bsoft.compose.bmusic.data.repositories

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.bsoft.compose.bmusic.data.models.Album
import com.bsoft.compose.bmusic.data.models.Artist
import com.bsoft.compose.bmusic.data.models.ArtistDetails
import com.bsoft.compose.bmusic.data.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "SongRepository"

/** With "Hide short songs" on, songs shorter than this (ringtones, voice notes) are hidden. */
private const val SHORT_SONG_MS = 60_000L

/** Number of tracks shown in "Recently added". */
private const val RECENTLY_ADDED_LIMIT = 10

/** MediaStore often fires several change events for one scan; wait for them to settle. */
private const val CHANGE_DEBOUNCE_MS = 750L

// No explicit `: Uri` type on purpose. Kotlin would add a null-check here, and the JVM
// unit-test stubs return null for these framework constants, which made class
// initialisation fail. On a device both values are always non-null.
private val songCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
}

private const val songSortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

/** Albums play in disc/track order; TRACK is disc * 1000 + track. */
private const val albumTrackOrder = "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC"

private val songProjection = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.ARTIST,
    MediaStore.Audio.Media.ALBUM,
    MediaStore.Audio.Media.DURATION,
    MediaStore.Audio.Media.ALBUM_ID,
    MediaStore.Audio.Media.ARTIST_ID,
    MediaStore.Audio.Media.TRACK,
    MediaStore.Audio.Media.DATE_ADDED,
    // Deprecated for opening files, but still filled in and readable; M3U files list paths.
    MediaStore.Audio.Media.DATA
)

private val albumProjection = arrayOf(
    MediaStore.Audio.Albums._ID,
    MediaStore.Audio.Albums.ALBUM,
    MediaStore.Audio.Albums.ARTIST,
    MediaStore.Audio.Albums.NUMBER_OF_SONGS
)

private val artistProjection = arrayOf(
    MediaStore.Audio.Artists._ID,
    MediaStore.Audio.Artists.ARTIST,
    MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
    MediaStore.Audio.Artists.NUMBER_OF_TRACKS
)

/** Maps every row of a song cursor to [Song]. The single place that knows the column layout. */
private fun Cursor.readSongs(): List<Song> {
    val idColumn = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
    val displayNameColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
    val titleColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
    val artistColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
    val albumColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
    val durationColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    val albumIdColumn = getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
    val artistIdColumn = getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
    val trackColumn = getColumnIndex(MediaStore.Audio.Media.TRACK)
    val dateAddedColumn = getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
    @Suppress("DEPRECATION") val pathColumn = getColumnIndex(MediaStore.Audio.Media.DATA)

    return buildList {
        while (moveToNext()) {
            add(
                Song(
                    id = getLong(idColumn),
                    displayName = getString(displayNameColumn) ?: "Unknown Name",
                    title = getString(titleColumn)?.takeIf { it.isNotBlank() }
                        ?: getString(displayNameColumn) ?: "Unknown Title",
                    artist = getString(artistColumn) ?: "Unknown Artist",
                    album = getString(albumColumn) ?: "Unknown Album",
                    duration = getLong(durationColumn),
                    albumId = if (albumIdColumn >= 0) getLong(albumIdColumn) else -1L,
                    artistId = if (artistIdColumn >= 0) getLong(artistIdColumn) else -1L,
                    track = if (trackColumn >= 0) getInt(trackColumn) else 0,
                    dateAdded = if (dateAddedColumn >= 0) getLong(dateAddedColumn) else 0L,
                    path = if (pathColumn >= 0) getString(pathColumn).orEmpty() else ""
                )
            )
        }
    }
}

private fun Cursor.readAlbums(): List<Album> {
    val idCol = getColumnIndex(MediaStore.Audio.Albums._ID)
    val nameCol = getColumnIndex(MediaStore.Audio.Albums.ALBUM)
    val artistCol = getColumnIndex(MediaStore.Audio.Albums.ARTIST)
    val countCol = getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
    return buildList {
        while (moveToNext()) {
            add(
                Album(
                    id = getLong(idCol),
                    name = getString(nameCol) ?: "",
                    artist = getString(artistCol) ?: "",
                    songCount = getInt(countCol)
                )
            )
        }
    }
}

private fun Cursor.readArtists(): List<Artist> {
    val idCol = getColumnIndex(MediaStore.Audio.Artists._ID)
    val nameCol = getColumnIndex(MediaStore.Audio.Artists.ARTIST)
    val albumCountCol = getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
    val songCountCol = getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
    return buildList {
        while (moveToNext()) {
            add(
                Artist(
                    id = getLong(idCol),
                    name = getString(nameCol) ?: "",
                    albumCount = getInt(albumCountCol),
                    songCount = getInt(songCountCol)
                )
            )
        }
    }
}

/**
 * Reads the device music library from MediaStore and caches it in memory.
 *
 * All accessors are blocking: call them from a background dispatcher. The cache is thread-safe
 * and is dropped by [invalidate], which [startObserving] triggers automatically whenever
 * MediaStore reports a change (new download, deleted file, rescan). Collect [changes] to reload.
 */
class SongRepository(private val context: Context) {
    private val lock = Any()

    private var songsCache: Map<Long, Song>? = null
    private var albumsCache: Map<Long, Album>? = null
    private var artistsCache: Map<Long, Artist>? = null
    private var lastAddedCache: List<Song>? = null
    private val albumSongsCache = mutableMapOf<Long, List<Song>>()
    private val artistDetailsCache = mutableMapOf<Long, ArtistDetails>()

    private val _changes = MutableStateFlow(0L)

    /** Increments every time the cached library is invalidated. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private var observer: ContentObserver? = null

    @Volatile
    private var hideShortSongs = true

    /** Applies the "Hide short songs" setting; reloads the library when it changes. */
    fun setHideShortSongs(hide: Boolean) {
        if (hideShortSongs == hide) return
        hideShortSongs = hide
        invalidate()
    }

    /** Registers a MediaStore observer once; safe to call repeatedly. */
    fun startObserving() {
        synchronized(lock) {
            if (observer != null) return
            val handler = Handler(Looper.getMainLooper())
            val debounced = Runnable { invalidate() }
            val contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    handler.removeCallbacks(debounced)
                    handler.postDelayed(debounced, CHANGE_DEBOUNCE_MS)
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver
            )
            observer = contentObserver
        }
    }

    /** Drops every cached query so the next access reads MediaStore again. */
    fun invalidate() {
        synchronized(lock) {
            songsCache = null
            albumsCache = null
            artistsCache = null
            lastAddedCache = null
            albumSongsCache.clear()
            artistDetailsCache.clear()
        }
        _changes.update { it + 1 }
    }

    // An empty result is not cached, so the library loads as soon as permission is granted.
    private fun songsMap(): Map<Long, Song> = synchronized(lock) {
        songsCache?.takeIf { it.isNotEmpty() }
            ?: querySongs().associateBy { it.id }.also { songsCache = it }
    }

    private fun albumsMap(): Map<Long, Album> = synchronized(lock) {
        albumsCache?.takeIf { it.isNotEmpty() }
            ?: queryAlbums().associateBy { it.id }.also { albumsCache = it }
    }

    private fun artistsMap(): Map<Long, Artist> = synchronized(lock) {
        artistsCache?.takeIf { it.isNotEmpty() }
            ?: queryArtists().associateBy { it.id }.also { artistsCache = it }
    }

    val songs: List<Song>
        get() = songsMap().values.toList()

    val albums: List<Album>
        get() = albumsMap().values.toList()

    val artists: List<Artist>
        get() = artistsMap().values.toList()

    /** The most recently added tracks, newest first. */
    val last: List<Song>
        get() = synchronized(lock) {
            lastAddedCache?.takeIf { it.isNotEmpty() }
                ?: queryLastAdded().also { lastAddedCache = it }
        }

    fun findSongById(songId: Long): Song? {
        val result = songsMap()[songId]
        if (result == null) Log.w(TAG, "No song found with ID: $songId")
        return result
    }

    fun findAlbumById(albumId: Long): Album? {
        val result = albumsMap()[albumId]
        if (result == null) Log.w(TAG, "No album found with ID: $albumId")
        return result
    }

    fun findSongsByAlbumId(albumId: Long): List<Song> = synchronized(lock) {
        albumSongsCache[albumId] ?: querySongsByAlbum(albumId).also {
            if (it.isNotEmpty()) albumSongsCache[albumId] = it
        }
    }

    fun findArtistById(artistId: Long): Artist? {
        val result = artistsMap()[artistId]
        if (result == null) Log.w(TAG, "No artist found with ID: $artistId")
        return result
    }

    fun findArtistDetailsByArtistId(artistId: Long): ArtistDetails = synchronized(lock) {
        artistDetailsCache[artistId] ?: queryArtistDetails(artistId).also {
            if (it.songs.isNotEmpty()) artistDetailsCache[artistId] = it
        }
    }

    fun search(query: String): List<Song> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return songs.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.displayName.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true) ||
                it.album.contains(needle, ignoreCase = true)
        }
    }

    // ---- MediaStore queries -------------------------------------------------------------

    private fun querySongs(): List<Song> {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != ? AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("0", (if (hideShortSongs) SHORT_SONG_MS else 0L).toString())
        return safeQuery {
            context.contentResolver.query(songCollection, songProjection, selection, args, songSortOrder)
                ?.use { it.readSongs() }
        }
    }

    private fun queryAlbums(): List<Album> {
        val selection = "${MediaStore.Audio.Albums.ARTIST} IS NOT NULL"
        val sort = "${MediaStore.Audio.Albums.ALBUM} ASC"
        return safeQuery {
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumProjection, selection, null, sort
            )?.use { it.readAlbums() }
        }
    }

    private fun queryArtists(): List<Artist> {
        val sort = "${MediaStore.Audio.Artists.ARTIST} ASC"
        return safeQuery {
            context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, artistProjection, null, null, sort
            )?.use { it.readArtists() }
        }
    }

    private fun querySongsByAlbum(albumId: Long): List<Song> {
        val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        return safeQuery {
            context.contentResolver.query(
                songCollection, songProjection, selection, arrayOf(albumId.toString()), albumTrackOrder
            )?.use { it.readSongs() }
        }
    }

    private fun queryArtistDetails(artistId: Long): ArtistDetails {
        val selection = "${MediaStore.Audio.Media.ARTIST_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = safeQuery {
            context.contentResolver.query(
                songCollection, songProjection, selection, arrayOf(artistId.toString()), songSortOrder
            )?.use { it.readSongs() }
        }
        // Derive the artist's albums from their tracks. The old query compared the album's
        // ARTIST *name* column with the artist *ID*, so it never matched anything.
        val albumsById = albumsMap()
        val albums = songs.asSequence()
            .map { it.albumId }
            .filter { it > 0L }
            .distinct()
            .mapNotNull { albumsById[it] }
            .sortedBy { it.name.lowercase() }
            .toList()
        return ArtistDetails(id = artistId, albums = albums, songs = songs)
    }

    private fun queryLastAdded(): List<Song> {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.IS_MUSIC} != 0")
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Audio.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, RECENTLY_ADDED_LIMIT)
        }
        return safeQuery {
            context.contentResolver.query(songCollection, songProjection, queryArgs, null)
                ?.use { it.readSongs() }
        }.take(RECENTLY_ADDED_LIMIT) // Providers before Android 11 may ignore QUERY_ARG_LIMIT.
    }

    /** MediaStore throws SecurityException before permission is granted; treat that as empty. */
    private inline fun <T> safeQuery(block: () -> List<T>?): List<T> = try {
        block() ?: emptyList()
    } catch (e: SecurityException) {
        Log.w(TAG, "MediaStore query refused: ${e.message}")
        emptyList()
    }
}
