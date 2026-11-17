package com.bsoft.compose.bmusic.data.models

import java.text.Collator
import java.util.Locale

/** Ways the Songs tab can be ordered. Names are persisted; do not rename. */
enum class SongSort {
    TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION;

    /** True when an A–Z index makes sense for this order. */
    val alphabetical: Boolean
        get() = this == TITLE || this == ARTIST || this == ALBUM

    /** The text whose first letter heads each A–Z section. */
    fun indexText(song: Song): String = when (this) {
        ARTIST -> song.artist
        ALBUM -> song.album
        else -> song.title
    }

    companion object {
        fun fromName(name: String?): SongSort = entries.firstOrNull { it.name == name } ?: TITLE
    }
}

/**
 * Sorts songs the way people expect: case- and accent-insensitive in the user's language,
 * with ties broken by title. Newest first for [SongSort.DATE_ADDED], longest first for duration.
 */
fun List<Song>.sortedForDisplay(sort: SongSort, locale: Locale = Locale.getDefault()): List<Song> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
    val comparator: Comparator<Song> = when (sort) {
        SongSort.TITLE -> byTitle
        SongSort.ARTIST -> Comparator<Song> { a, b -> collator.compare(a.artist, b.artist) }.then(byTitle)
        SongSort.ALBUM -> Comparator<Song> { a, b -> collator.compare(a.album, b.album) }
            .thenBy { it.track }.then(byTitle)
        SongSort.DATE_ADDED -> compareByDescending<Song> { it.dateAdded }.then(byTitle)
        SongSort.DURATION -> compareByDescending<Song> { it.duration }.then(byTitle)
    }
    return sortedWith(comparator)
}
