package com.bsoft.compose.bmusic.data.models

/**
 * Where a queue entry came from. [prefix] is the stable string written into media IDs and into
 * the persisted playback snapshot, so these values must never change.
 */
sealed class MediaSource(val prefix: String) {
    data object Songs : MediaSource("songs")
    data class Album(val albumId: Long) : MediaSource("album_$albumId")
    data class Artist(val artistId: Long) : MediaSource("artist_$artistId")
    data object RecentlyPlayed : MediaSource("recently_played")
    data object RecentlyAdded : MediaSource("recently_added")
    data object MostPlayed : MediaSource("most_played")
    data object Favourites : MediaSource("favourites")
    data object Rediscover : MediaSource("rediscover")
    data object NeverPlayed : MediaSource("never_played")
    data class Playlist(val playlistId: Long) : MediaSource("playlist_$playlistId")
    /** A folder and everything below it; [key] is [com.bsoft.compose.bmusic.data.models.folderKey]. */
    data class Folder(val key: String) : MediaSource("folder_$key")
    /** A list the UI supplied directly (search results, an ad-hoc selection). */
    data object Custom : MediaSource("custom")
    /** A single item inserted with "Play next"; [token] keeps duplicates unique. */
    data class Queued(val token: String) : MediaSource("queued_$token")

    companion object {
        /** Parses a source prefix such as `album_5` or `recently_played`; null if unknown. */
        fun fromPrefix(prefix: String): MediaSource? = when {
            prefix == Songs.prefix -> Songs
            prefix == RecentlyPlayed.prefix -> RecentlyPlayed
            prefix == RecentlyAdded.prefix -> RecentlyAdded
            prefix == MostPlayed.prefix -> MostPlayed
            prefix == Favourites.prefix -> Favourites
            prefix == Rediscover.prefix -> Rediscover
            prefix == NeverPlayed.prefix -> NeverPlayed
            prefix == Custom.prefix -> Custom
            // Older builds used the bare word "playlist" for UI-supplied lists.
            prefix == "playlist" -> Custom
            prefix.startsWith("album_") -> prefix.removePrefix("album_").toLongOrNull()?.let(::Album)
            prefix.startsWith("artist_") -> prefix.removePrefix("artist_").toLongOrNull()?.let(::Artist)
            prefix.startsWith("playlist_") -> prefix.removePrefix("playlist_").toLongOrNull()?.let(::Playlist)
            prefix.startsWith("folder_") -> Folder(prefix.removePrefix("folder_"))
            prefix.startsWith("queued_") -> Queued(prefix.removePrefix("queued_"))
            else -> null
        }
    }
}

/**
 * Typed form of every media ID the app and its Media3 session exchange.
 *
 * Wire formats (unchanged from earlier builds, so saved queues still restore):
 * - `root`, `songs`, `albums`, `artists` — browse folders
 * - `12345` — a song by MediaStore ID
 * - `<source>#<index>` — "play this source starting at index", e.g. `album_5#2`; also used as
 *   the ID of a browsable album/artist (`album_5#0`)
 * - `<source>#<index>|<songId>` — one entry of an explicit queue, e.g. `playlist_9#3|12345`
 * - `queued_<token>|<songId>` — an entry inserted with "Play next"
 */
sealed interface MediaId {
    data object Root : MediaId
    data object SongsFolder : MediaId
    data object AlbumsFolder : MediaId
    data object ArtistsFolder : MediaId
    data object PlaylistsFolder : MediaId

    /** A single playable song. */
    data class Track(val songId: Long) : MediaId

    /**
     * A position in a [source]. With a [songId] it is one concrete queue entry; without one it
     * means "expand the whole source and start at [index]".
     */
    data class Entry(val source: MediaSource, val index: Int, val songId: Long? = null) : MediaId

    data class Unknown(val raw: String) : MediaId

    fun encode(): String = when (this) {
        Root -> ROOT
        SongsFolder -> SONGS
        AlbumsFolder -> ALBUMS
        ArtistsFolder -> ARTISTS
        PlaylistsFolder -> PLAYLISTS
        is Track -> songId.toString()
        is Entry -> buildString {
            append(source.prefix)
            if (source !is MediaSource.Queued) append('#').append(index)
            songId?.let { append('|').append(it) }
        }
        is Unknown -> raw
    }

    /** The playable song this ID points at, or null for folders and whole-source requests. */
    val songIdOrNull: Long?
        get() = when (this) {
            is Track -> songId
            is Entry -> songId
            else -> null
        }

    companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"

        fun parse(raw: String): MediaId {
            when (raw) {
                ROOT -> return Root
                SONGS -> return SongsFolder
                ALBUMS -> return AlbumsFolder
                ARTISTS -> return ArtistsFolder
                PLAYLISTS -> return PlaylistsFolder
            }
            raw.toLongOrNull()?.let { return if (it > 0L) Track(it) else Unknown(raw) }

            val head = raw.substringBefore('|')
            val songId = if ('|' in raw) raw.substringAfter('|').toLongOrNull()?.takeIf { it > 0L } else null
            val prefix = head.substringBefore('#')
            val index = if ('#' in head) head.substringAfter('#').toIntOrNull()?.coerceAtLeast(0) ?: 0 else 0

            val source = MediaSource.fromPrefix(prefix) ?: return Unknown(raw)
            // "album_5" with no '#' and no song is a browse node for that album: index 0.
            return Entry(source, index, songId)
        }
    }
}
