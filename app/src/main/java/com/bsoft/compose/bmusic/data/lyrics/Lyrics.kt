package com.bsoft.compose.bmusic.data.lyrics

/** One timed line of synced lyrics. An empty [text] is an instrumental gap. */
data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics for a song: either timed lines or plain text. */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /**
         * Index of the line being sung at [positionMs]: the last line that has started, or -1
         * before the first line.
         */
        fun indexAt(positionMs: Long): Int {
            var low = 0
            var high = lines.lastIndex
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }
    }

    data class Plain(val text: String) : Lyrics
}

/**
 * Parses LRC lyrics: `[mm:ss.xx]text` lines, several timestamps per line, `[offset:±ms]`, and
 * enhanced LRC word timings (`<mm:ss.xx>`, removed). Text without any timestamp is returned
 * as plain lyrics, so the same parser handles untimed lyrics from tags.
 */
object LrcParser {
    private val timeTag = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\[[a-zA-Z#]+:.*]$""")

    fun parse(raw: String): Lyrics? {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        var offsetMs = 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            val offset = offsetTag.matchEntire(line)
            if (offset != null) {
                offsetMs = offset.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            var rest = line
            val times = mutableListOf<Long>()
            while (true) {
                val match = timeTag.find(rest) ?: break
                times += toMillis(match)
                rest = rest.substring(match.range.last + 1).trimStart()
            }
            when {
                times.isNotEmpty() -> {
                    val lyric = wordTag.replace(rest, "").trim()
                    times.forEach { timed += LyricLine(it, lyric) }
                }
                // [ar:Artist], [ti:Title], [length:...] and similar header tags.
                metaTag.matches(line) -> Unit
                else -> plain += rawLine.trimEnd()
            }
        }

        if (timed.isNotEmpty()) {
            // A positive offset shows lyrics earlier.
            val lines = timed
                .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0L)) }
                .sortedBy { it.timeMs }
            return if (lines.any { it.text.isNotBlank() }) Lyrics.Synced(lines) else null
        }
        val body = plain.joinToString("\n").trim('\n', ' ')
        return if (body.isBlank()) null else Lyrics.Plain(body)
    }

    private fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return (minutes * 60 + seconds) * 1000 + millis
    }
}

/** UTF-16 when the file starts with a byte order mark, otherwise UTF-8. */
internal fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 2) {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return String(bytes, Charsets.UTF_16)
    }
    return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
}
