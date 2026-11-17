package com.bsoft.compose.bmusic.data.tags

/** The text tags the editor can change. */
enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK, DISC, LYRICS }

/** One song's editable tags, as the user sees them (empty means "no value"). */
data class TagValues(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val disc: String = "",
    val lyrics: String = "",
) {
    operator fun get(field: TagField): String = when (field) {
        TagField.TITLE -> title
        TagField.ARTIST -> artist
        TagField.ALBUM -> album
        TagField.ALBUM_ARTIST -> albumArtist
        TagField.GENRE -> genre
        TagField.YEAR -> year
        TagField.TRACK -> track
        TagField.DISC -> disc
        TagField.LYRICS -> lyrics
    }

    fun with(field: TagField, value: String): TagValues = when (field) {
        TagField.TITLE -> copy(title = value)
        TagField.ARTIST -> copy(artist = value)
        TagField.ALBUM -> copy(album = value)
        TagField.ALBUM_ARTIST -> copy(albumArtist = value)
        TagField.GENRE -> copy(genre = value)
        TagField.YEAR -> copy(year = value)
        TagField.TRACK -> copy(track = value)
        TagField.DISC -> copy(disc = value)
        TagField.LYRICS -> copy(lyrics = value)
    }

    /** Values ready to write: trimmed (lyrics keep their inner line breaks). */
    fun cleaned(): TagValues = TagValues(
        title = title.trim(),
        artist = artist.trim(),
        album = album.trim(),
        albumArtist = albumArtist.trim(),
        genre = genre.trim(),
        year = year.trim(),
        track = track.trim(),
        disc = disc.trim(),
        lyrics = lyrics.trim('\n', '\r', ' '),
    )

    /** The fields whose cleaned value differs from [original]'s. */
    fun changesFrom(original: TagValues): Set<TagField> {
        val now = cleaned()
        val before = original.cleaned()
        return TagField.entries.filterTo(mutableSetOf()) { now[it] != before[it] }
    }

    /** Fields whose value can't be written as typed (see [tagProblem]). */
    fun problems(): Map<TagField, TagProblem> =
        TagField.entries.mapNotNull { field -> tagProblem(field, this[field])?.let { field to it } }.toMap()
}

enum class TagProblem { NOT_A_YEAR, NOT_A_NUMBER, TITLE_REQUIRED }

/**
 * Why [value] can't be saved in [field], or null if it can. Years are 4 digits (an optional full
 * date like 2021-05-14 is accepted, as tags allow it); track and disc are positive whole numbers,
 * optionally "3/12" (number/total).
 */
fun tagProblem(field: TagField, value: String): TagProblem? {
    val text = value.trim()
    return when (field) {
        TagField.TITLE -> if (text.isEmpty()) TagProblem.TITLE_REQUIRED else null
        TagField.YEAR -> if (text.isEmpty() || YEAR.matches(text)) null else TagProblem.NOT_A_YEAR
        TagField.TRACK, TagField.DISC -> if (text.isEmpty() || NUMBER.matches(text)) null else TagProblem.NOT_A_NUMBER
        else -> null
    }
}

/** "3/12" -> "3": what is written to the number field (totals are dropped). */
fun tagNumber(value: String): String = value.trim().substringBefore('/').trimStart('0').ifEmpty {
    if (value.trim().isEmpty()) "" else "0"
}

/** Lower-case file extension of a path or file name, or "". */
fun fileExtension(name: String): String =
    name.substringAfterLast('/').substringAfterLast('.', "").lowercase()

/**
 * Formats the tag editor can write. Opus and raw AAC/ADTS files have no supported tag writer,
 * so they are offered read-only.
 */
val EDITABLE_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "m4a", "mp4", "m4b", "wma", "wav", "aif", "aiff", "aifc", "dsf")

private val YEAR = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")
private val NUMBER = Regex("^0*[1-9]\\d{0,3}(/\\d{1,4})?$")
