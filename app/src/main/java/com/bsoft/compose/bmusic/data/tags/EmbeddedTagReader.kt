package com.bsoft.compose.bmusic.data.tags

import com.bsoft.compose.bmusic.data.lyrics.LrcParser
import com.bsoft.compose.bmusic.data.lyrics.LyricLine
import com.bsoft.compose.bmusic.data.lyrics.Lyrics
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.charset.Charset

/** What BMusic uses from a file's tags. */
data class EmbeddedTags(
    val lyrics: Lyrics? = null,
    val replayGain: ReplayGain = ReplayGain(),
) {
    /** For a FLAC file behind an ID3 tag: the first value found wins. */
    fun orElse(other: EmbeddedTags) = EmbeddedTags(lyrics ?: other.lyrics, replayGain.orElse(other.replayGain))

    companion object {
        val NONE = EmbeddedTags()
    }
}

/**
 * Reads lyrics and ReplayGain values embedded in audio files. It uses no Android APIs, so it is
 * unit-tested, and reads only the tag area, never the audio.
 *
 * Lyrics: ID3v2.2–2.4 `USLT` (unsynced, may contain LRC) and `SYLT` (synced, millisecond
 * timestamps) in MP3 and in front of FLAC; FLAC and Ogg Vorbis/Opus comments `SYNCEDLYRICS`,
 * `LYRICS` and `UNSYNCEDLYRICS`; MP4/M4A `©lyr`.
 *
 * ReplayGain: ID3 `TXXX:REPLAYGAIN_*`, Vorbis/Opus `REPLAYGAIN_*` and `R128_*_GAIN`, and MP4
 * `----:com.apple.iTunes:replaygain_*`.
 */
object EmbeddedTagReader {
    /** Largest tag block read into memory (embedded cover art lives in the same block). */
    private const val MAX_BLOCK_BYTES = 16 * 1024 * 1024

    /** Ogg comment headers are on the first pages; stop looking after this many. */
    private const val MAX_OGG_PAGES = 64

    private val LATIN1: Charset = Charsets.ISO_8859_1

    /** The tags of [input]; [EmbeddedTags.NONE] when there are none or the file is not understood. */
    fun read(input: InputStream): EmbeddedTags = try {
        val source = Source(input)
        val head = source.peek(12)
        when {
            head.startsWith("ID3") -> {
                val id3 = readId3(source)
                if (source.peek(4).startsWith("fLaC")) id3.orElse(readFlac(source)) else id3
            }
            head.startsWith("fLaC") -> readFlac(source)
            head.startsWith("OggS") -> readOgg(source)
            head.size >= 8 && String(head, 4, 4, LATIN1) == "ftyp" -> readMp4(source)
            else -> EmbeddedTags.NONE
        }
    } catch (e: IOException) {
        EmbeddedTags.NONE
    } catch (e: IndexOutOfBoundsException) {
        EmbeddedTags.NONE
    } catch (e: IllegalArgumentException) {
        EmbeddedTags.NONE
    }

    // ---- ID3v2 ----------------------------------------------------------------------------

    private fun readId3(source: Source): EmbeddedTags {
        val header = source.readFully(10)
        val major = header[3].toInt()
        val flags = header[5].u()
        val size = synchsafe(header, 6)
        val footer = if (major == 4 && flags and 0x10 != 0) 10 else 0
        if (major !in 2..4 || size <= 0 || size > MAX_BLOCK_BYTES) {
            source.skipFully(size.toLong().coerceAtLeast(0L) + footer)
            return EmbeddedTags.NONE
        }
        var tag = source.readFully(size)
        source.skipFully(footer.toLong())
        if (major < 4 && flags and 0x80 != 0) tag = removeUnsync(tag)

        var pos = 0
        if (major >= 3 && flags and 0x40 != 0) {
            pos = if (major == 3) 4 + int32(tag, 0) else synchsafe(tag, 0)
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10

        var unsynced: String? = null
        var synced: Lyrics.Synced? = null
        val userText = mutableMapOf<String, String>()
        while (pos + headerLength <= tag.size && tag[pos].toInt() != 0) {
            val id = String(tag, pos, idLength, LATIN1)
            val frameSize = when (major) {
                2 -> int24(tag, pos + 3)
                3 -> int32(tag, pos + 4)
                else -> synchsafe(tag, pos + 4)
            }
            val format = if (major >= 3) tag[pos + 9].u() else 0
            val start = pos + headerLength
            if (frameSize <= 0 || start + frameSize > tag.size) break
            pos = start + frameSize
            var data = tag.copyOfRange(start, start + frameSize)

            when (major) {
                3 -> {
                    if (format and 0xC0 != 0) continue // compressed or encrypted
                    if (format and 0x20 != 0) data = data.copyOfRange(1, data.size) // group ID
                }
                4 -> {
                    if (format and 0x0C != 0) continue // compressed or encrypted
                    if (format and 0x40 != 0) data = data.copyOfRange(1, data.size) // group ID
                    if (format and 0x01 != 0) data = data.copyOfRange(4, data.size) // data length
                    if (format and 0x02 != 0) data = removeUnsync(data)
                }
            }
            when (id) {
                "USLT", "ULT" -> if (unsynced == null) unsynced = parseUslt(data)?.takeIf { it.isNotBlank() }
                "SYLT", "SLT" -> if (synced == null) synced = parseSylt(data)
                "TXXX", "TXX" -> parseTxxx(data)?.let { (key, value) -> userText.putIfAbsent(key, value) }
            }
        }
        // Many taggers store LRC text in USLT, so it can still come out synced.
        return EmbeddedTags(synced ?: unsynced?.let(LrcParser::parse), ReplayGain.fromTags(userText))
    }

    /** TXXX: encoding, description, value. Returns (DESCRIPTION, value). */
    private fun parseTxxx(data: ByteArray): Pair<String, String>? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt()
        val descriptionEnd = terminator(data, 1, encoding)
        if (descriptionEnd < 0) return null
        val description = decode(data, 1, descriptionEnd, encoding).uppercase()
        val value = decode(data, descriptionEnd + terminatorLength(encoding), data.size, encoding)
        return description to value
    }

    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 4) return null
        val encoding = data[0].toInt()
        val descriptorEnd = terminator(data, 4, encoding)
        if (descriptorEnd < 0) return null
        return decode(data, descriptorEnd + terminatorLength(encoding), data.size, encoding)
    }

    private fun parseSylt(data: ByteArray): Lyrics.Synced? {
        if (data.size < 6) return null
        val encoding = data[0].toInt()
        // 1 = MPEG frames, 2 = milliseconds. Frame-based timing needs the bitrate; skip it.
        if (data[4].toInt() != 2) return null
        val step = terminatorLength(encoding)
        var pos = terminator(data, 6, encoding).takeIf { it >= 0 }?.plus(step) ?: return null
        val lines = mutableListOf<LyricLine>()
        while (pos < data.size) {
            val end = terminator(data, pos, encoding)
            if (end < 0 || end + step + 4 > data.size) break
            val text = decode(data, pos, end, encoding).trim('\n', '\r', ' ')
            val time = int32(data, end + step).toLong() and 0xFFFFFFFFL
            lines += LyricLine(time, text)
            pos = end + step + 4
        }
        return if (lines.any { it.text.isNotBlank() }) Lyrics.Synced(lines.sortedBy { it.timeMs }) else null
    }

    /** Index of the string terminator at or after [from], or -1. */
    private fun terminator(data: ByteArray, from: Int, encoding: Int): Int {
        if (encoding == 1 || encoding == 2) {
            var i = from
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
            return -1
        }
        for (i in from until data.size) if (data[i].toInt() == 0) return i
        return -1
    }

    private fun terminatorLength(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decode(data: ByteArray, from: Int, to: Int, encoding: Int): String {
        if (from >= to) return ""
        val charset = when (encoding) {
            1 -> Charsets.UTF_16 // BOM decides the byte order
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> LATIN1
        }
        return String(data, from, to - from, charset).trimEnd('\u0000')
    }

    /** Undoes ID3 unsynchronisation: every 0xFF 0x00 pair becomes 0xFF. */
    private fun removeUnsync(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i].u() == 0xFF && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ---- FLAC and Ogg -------------------------------------------------------------------

    private fun readFlac(source: Source): EmbeddedTags {
        if (!source.readFully(4).startsWith("fLaC")) return EmbeddedTags.NONE
        while (true) {
            val header = source.readFully(4)
            val last = header[0].u() and 0x80 != 0
            val type = header[0].u() and 0x7F
            val length = int24(header, 1)
            if (type == 4) {
                if (length > MAX_BLOCK_BYTES) return EmbeddedTags.NONE
                return vorbisComments(source.readFully(length), 0)
            }
            source.skipFully(length.toLong())
            if (last) return EmbeddedTags.NONE
        }
    }

    /** The comment header is the second Ogg packet (after the identification header). */
    private fun readOgg(source: Source): EmbeddedTags {
        val packet = ByteArrayOutputStream()
        var packetIndex = 0
        repeat(MAX_OGG_PAGES) {
            val header = source.readFully(27)
            if (!header.startsWith("OggS")) return EmbeddedTags.NONE
            val lacing = source.readFully(header[26].u())
            for (lace in lacing) {
                val length = lace.u()
                packet.write(source.readFully(length))
                if (packet.size() > MAX_BLOCK_BYTES) return EmbeddedTags.NONE
                if (length < 255) {
                    if (packetIndex == 1) return oggComments(packet.toByteArray())
                    packetIndex++
                    packet.reset()
                }
            }
        }
        return EmbeddedTags.NONE
    }

    private fun oggComments(packet: ByteArray): EmbeddedTags = when {
        packet.size > 7 && packet[0].toInt() == 3 && String(packet, 1, 6, LATIN1) == "vorbis" -> vorbisComments(packet, 7)
        packet.startsWith("OpusTags") -> vorbisComments(packet, 8)
        else -> EmbeddedTags.NONE
    }

    /** Vorbis comment block: vendor string, then `KEY=value` entries (little-endian lengths). */
    private fun vorbisComments(data: ByteArray, start: Int): EmbeddedTags {
        var pos = start
        val vendorLength = le32(data, pos)
        pos += 4 + vendorLength
        // A real file has a handful of entries; the cap stops a corrupt count from spinning.
        val count = le32(data, pos).coerceIn(0, 10_000)
        pos += 4
        val values = mutableMapOf<String, String>()
        repeat(count) {
            if (pos + 4 > data.size) return@repeat
            val length = le32(data, pos)
            pos += 4
            if (length < 0 || pos + length > data.size) return@repeat
            val entry = String(data, pos, length, Charsets.UTF_8)
            pos += length
            val key = entry.substringBefore('=', "").uppercase()
            if (key.isNotEmpty() && key !in values) values[key] = entry.substringAfter('=')
        }
        val candidates = listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS").mapNotNull { values[it] }.mapNotNull(LrcParser::parse)
        return EmbeddedTags(
            lyrics = candidates.firstOrNull { it is Lyrics.Synced } ?: candidates.firstOrNull(),
            replayGain = ReplayGain.fromTags(values)
        )
    }

    // ---- MP4 ----------------------------------------------------------------------------

    private fun readMp4(source: Source): EmbeddedTags {
        while (true) {
            val header = source.readOrNull(8) ?: return EmbeddedTags.NONE
            var size = int32(header, 0).toLong() and 0xFFFFFFFFL
            val type = String(header, 4, 4, LATIN1)
            var headerLength = 8L
            if (size == 1L) {
                size = int64(source.readFully(8))
                headerLength = 16L
            }
            if (type == "moov") {
                val body = size - headerLength
                if (size == 0L || body <= 0 || body > MAX_BLOCK_BYTES) return EmbeddedTags.NONE
                return moovTags(source.readFully(body.toInt()))
            }
            // size 0 means "to the end of the file": nothing follows.
            if (size == 0L || size < headerLength) return EmbeddedTags.NONE
            source.skipFully(size - headerLength)
        }
    }

    private fun moovTags(moov: ByteArray): EmbeddedTags {
        val udta = child(moov, 0, moov.size, "udta") ?: return EmbeddedTags.NONE
        val meta = child(moov, udta.first, udta.last + 1, "meta") ?: return EmbeddedTags.NONE
        // "meta" is a full box (4 bytes of version/flags) except in some QuickTime files.
        val metaStart = if (meta.first + 8 <= moov.size && String(moov, meta.first + 4, 4, LATIN1) == "hdlr") meta.first else meta.first + 4
        val ilst = child(moov, metaStart, meta.last + 1, "ilst") ?: return EmbeddedTags.NONE

        var lyrics: Lyrics? = null
        val freeform = mutableMapOf<String, String>()
        for ((type, item) in children(moov, ilst.first, ilst.last + 1)) {
            when (type) {
                "\u00A9lyr" -> if (lyrics == null) lyrics = dataText(moov, item)?.let(LrcParser::parse)
                // iTunes-style "----" items: mean (domain), name (key), data (value).
                "----" -> {
                    val name = child(moov, item.first, item.last + 1, "name") ?: continue
                    // mean and name are full boxes: 4 bytes of version/flags before the text.
                    if (name.first + 4 > name.last + 1) continue
                    val key = String(moov, name.first + 4, name.last + 1 - (name.first + 4), Charsets.UTF_8).uppercase()
                    dataText(moov, item)?.let { freeform.putIfAbsent(key, it) }
                }
            }
        }
        return EmbeddedTags(lyrics, ReplayGain.fromTags(freeform))
    }

    /** The text of the "data" box inside an ilst item: 4 bytes type, 4 bytes locale, UTF-8. */
    private fun dataText(moov: ByteArray, item: IntRange): String? {
        val data = child(moov, item.first, item.last + 1, "data") ?: return null
        val textStart = data.first + 8
        if (textStart > data.last + 1) return null
        return String(moov, textStart, data.last + 1 - textStart, Charsets.UTF_8)
    }

    /** Every child box between [from] and [to], as (type, content range). */
    private fun children(data: ByteArray, from: Int, to: Int): List<Pair<String, IntRange>> {
        val boxes = mutableListOf<Pair<String, IntRange>>()
        var pos = from
        while (pos + 8 <= to) {
            val size = int32(data, pos)
            if (size < 8 || pos + size > to) break
            boxes += String(data, pos + 4, 4, LATIN1) to ((pos + 8) until (pos + size))
            pos += size
        }
        return boxes
    }

    /** Content range of the first child box of [type] between [from] and [to]. */
    private fun child(data: ByteArray, from: Int, to: Int, type: String): IntRange? {
        var pos = from
        while (pos + 8 <= to) {
            val size = int32(data, pos)
            val boxType = String(data, pos + 4, 4, LATIN1)
            if (size < 8 || pos + size > to) return null
            if (boxType == type) return (pos + 8) until (pos + size)
            pos += size
        }
        return null
    }

    // ---- Bytes --------------------------------------------------------------------------

    private fun Byte.u() = toInt() and 0xFF

    private fun ByteArray.startsWith(text: String): Boolean =
        size >= text.length && text.indices.all { this[it].u() == text[it].code }

    private fun int32(b: ByteArray, i: Int) = (b[i].u() shl 24) or (b[i + 1].u() shl 16) or (b[i + 2].u() shl 8) or b[i + 3].u()

    private fun int24(b: ByteArray, i: Int) = (b[i].u() shl 16) or (b[i + 1].u() shl 8) or b[i + 2].u()

    private fun le32(b: ByteArray, i: Int) = b[i].u() or (b[i + 1].u() shl 8) or (b[i + 2].u() shl 16) or (b[i + 3].u() shl 24)

    private fun int64(b: ByteArray): Long = b.fold(0L) { acc, byte -> (acc shl 8) or byte.u().toLong() }

    private fun synchsafe(b: ByteArray, i: Int) =
        ((b[i].u() and 0x7F) shl 21) or ((b[i + 1].u() and 0x7F) shl 14) or ((b[i + 2].u() and 0x7F) shl 7) or (b[i + 3].u() and 0x7F)

    /** A forward-only reader that can look ahead a few bytes. */
    private class Source(input: InputStream) {
        private val stream = PushbackInputStream(input, 16)

        fun readFully(count: Int): ByteArray {
            require(count >= 0)
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = stream.read(bytes, offset, count - offset)
                if (read < 0) throw EOFException()
                offset += read
            }
            return bytes
        }

        fun readOrNull(count: Int): ByteArray? = try {
            readFully(count)
        } catch (e: EOFException) {
            null
        }

        /** Up to [count] (at most 16) bytes without consuming them. */
        fun peek(count: Int): ByteArray {
            val bytes = ByteArray(count)
            var filled = 0
            while (filled < count) {
                val read = stream.read(bytes, filled, count - filled)
                if (read < 0) break
                filled += read
            }
            if (filled > 0) stream.unread(bytes, 0, filled)
            return bytes.copyOf(filled)
        }

        fun skipFully(count: Long) {
            var left = count
            while (left > 0) {
                val skipped = stream.skip(left)
                if (skipped > 0) {
                    left -= skipped
                } else {
                    if (stream.read() < 0) throw EOFException()
                    left--
                }
            }
        }
    }
}
