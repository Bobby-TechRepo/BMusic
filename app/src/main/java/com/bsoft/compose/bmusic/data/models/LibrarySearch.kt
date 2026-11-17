package com.bsoft.compose.bmusic.data.models

import com.bsoft.compose.bmusic.data.entities.PlaylistEntity
import java.text.Normalizer
import java.util.Locale

/** What a library search found, best matches first in each group. */
data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/**
 * In-memory search over the loaded library. Case- and accent-insensitive ("beyonce" finds
 * "Beyoncé"); every word of the query must match some field ("adele hello"). Items whose name
 * starts with the query rank first, then items with a word starting with it.
 */
object LibrarySearch {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun search(
        query: String,
        songs: List<Song>,
        albums: List<Album>,
        artists: List<Artist>,
        playlists: List<PlaylistEntity>,
        limit: Int = 50,
    ): SearchResults {
        val needle = normalize(query).trim()
        val terms = needle.split(spaces).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return SearchResults()

        fun matches(vararg fields: String): Boolean {
            val normalized = fields.map(::normalize)
            return terms.all { term -> normalized.any { it.contains(term) } }
        }

        fun rank(name: String): Int {
            val n = normalize(name)
            return when {
                n.startsWith(needle) -> 0
                n.split(spaces).any { it.startsWith(terms.first()) } -> 1
                else -> 2
            }
        }

        return SearchResults(
            songs = songs.filter { matches(it.title, it.artist, it.album) }.sortedBy { rank(it.title) }.take(limit),
            albums = albums.filter { matches(it.name, it.artist) }.sortedBy { rank(it.name) }.take(limit),
            artists = artists.filter { matches(it.name) }.sortedBy { rank(it.name) }.take(limit),
            playlists = playlists.filter { matches(it.name) }.sortedBy { rank(it.name) }.take(limit),
        )
    }
}
