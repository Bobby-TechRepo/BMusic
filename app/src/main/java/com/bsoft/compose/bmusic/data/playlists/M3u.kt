package com.bsoft.compose.bmusic.data.playlists

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** One song line of an M3U playlist, with what its #EXTINF line said about it. */
data class M3uEntry(
    /** As written in the file: an absolute or relative path, or a file:// URI (decoded). */
    val location: String,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
) {
    /** The last path segment, e.g. "song.mp3". */
    val fileName: String get() = location.replace('\\', '/').substringAfterLast('/')
}

/** Reads and writes M3U / M3U8 playlists (extended M3U with #EXTINF). */
object M3u {
    private val streamPrefixes = listOf("http://", "https://", "rtsp://", "rtmp://", "mms://")

    fun parse(text: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var info: Triple<Long?, String?, String?>? = null
        for (rawLine in text.removePrefix("\uFEFF").lines()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> info = parseExtInf(line.substring(8))
                line.startsWith("#") -> Unit // #EXTM3U, #PLAYLIST and other directives
                streamPrefixes.any { line.startsWith(it, ignoreCase = true) } -> info = null // internet radio
                else -> {
                    entries += M3uEntry(
                        location = decodeLocation(line),
                        durationMs = info?.first,
                        artist = info?.second,
                        title = info?.third
                    )
                    info = null
                }
            }
        }
        return entries
    }

    /** An extended M3U with one #EXTINF line per song; write it as UTF-8 (.m3u8). */
    fun format(entries: List<M3uEntry>): String = buildString {
        append("#EXTM3U\n")
        entries.forEach { entry ->
            val seconds = entry.durationMs?.takeIf { it > 0 }?.let { (it + 500) / 1000 } ?: -1
            val display = listOfNotNull(entry.artist?.takeIf { it.isNotBlank() }, entry.title?.takeIf { it.isNotBlank() })
                .joinToString(" - ")
                .replace('\n', ' ')
            append("#EXTINF:").append(seconds).append(',').append(display).append('\n')
            append(entry.location).append('\n')
        }
    }

    /** "123,Artist - Title", possibly with attributes after the duration. */
    private fun parseExtInf(value: String): Triple<Long?, String?, String?> {
        val comma = value.indexOf(',')
        val durationPart = (if (comma >= 0) value.substring(0, comma) else value).trim().substringBefore(' ')
        val seconds = durationPart.toDoubleOrNull()
        val durationMs = seconds?.takeIf { it > 0 }?.let { (it * 1000).toLong() }
        val display = if (comma >= 0) value.substring(comma + 1).trim() else ""
        val separator = display.indexOf(" - ")
        return if (separator > 0) {
            Triple(durationMs, display.substring(0, separator).trim(), display.substring(separator + 3).trim().ifEmpty { null })
        } else {
            Triple(durationMs, null, display.ifEmpty { null })
        }
    }

    private fun decodeLocation(line: String): String {
        if (!line.startsWith("file:", ignoreCase = true)) return line
        val path = line.substring(5).let { if (it.startsWith("//")) it.substring(2) else it }
        return try {
            URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            path
        }
    }
}

/** What the matcher needs to know about a song in the library. */
data class LibraryFile(
    val id: Long,
    val path: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/**
 * Finds M3U entries in the library. Playlists from other apps or computers rarely have the same
 * absolute paths, so it tries in order: the exact path; the same file name (the closest folder
 * structure wins); then the #EXTINF artist and title, preferring a length within 3 seconds.
 */
class M3uMatcher(library: List<LibraryFile>) {
    private val byPath = library.filter { it.path.isNotEmpty() }.associateBy { pathKey(it.path) }
    private val byFileName = library.groupBy { (it.path.ifEmpty { it.displayName }).replace('\\', '/').substringAfterLast('/').lowercase(Locale.ROOT) }
    private val byTitleArtist = library.groupBy { normalize(it.title) + "\u0000" + normalize(it.artist) }

    fun match(entry: M3uEntry): LibraryFile? {
        byPath[pathKey(entry.location)]?.let { return it }

        val sameName = byFileName[entry.fileName.lowercase(Locale.ROOT)].orEmpty()
        if (sameName.size == 1) return sameName.single()
        if (sameName.size > 1) return sameName.maxBy { commonSuffixSegments(it.path, entry.location) }

        val title = entry.title ?: return null
        val candidates = byTitleArtist[normalize(title) + "\u0000" + normalize(entry.artist.orEmpty())].orEmpty()
        if (candidates.isEmpty()) return null
        val duration = entry.durationMs
        return candidates.firstOrNull { duration != null && abs(it.durationMs - duration) <= DURATION_TOLERANCE_MS }
            ?: candidates.first()
    }

    private fun pathKey(path: String) = path.replace('\\', '/').lowercase(Locale.ROOT)

    /** How many trailing folders two paths share, e.g. "Album/song.mp3" = 2. */
    private fun commonSuffixSegments(a: String, b: String): Int {
        val left = pathKey(a).split('/').filter { it.isNotEmpty() && it != ".." && it != "." }.asReversed()
        val right = pathKey(b).split('/').filter { it.isNotEmpty() && it != ".." && it != "." }.asReversed()
        var count = 0
        while (count < left.size && count < right.size && left[count] == right[count]) count++
        return count
    }

    private companion object {
        const val DURATION_TOLERANCE_MS = 3_000L
        val marks = Regex("\\p{Mn}+")

        fun normalize(text: String): String =
            marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
    }
}

/** [base], or "base (2)", "base (3)"… if a playlist already has that name. */
fun uniquePlaylistName(base: String, existing: Collection<String>): String {
    val taken = existing.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
    if (base.lowercase(Locale.ROOT) !in taken) return base
    var n = 2
    while ("$base ($n)".lowercase(Locale.ROOT) in taken) n++
    return "$base ($n)"
}
