package com.bsoft.compose.bmusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Builds tiny synthetic files byte by byte, so the parsers are tested without real audio. */
class EmbeddedLyricsReaderTest {

    // ---- helpers --------------------------------------------------------------------------

    private fun bytes(vararg parts: Any): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { part ->
            when (part) {
                is ByteArray -> out.write(part)
                is String -> out.write(part.toByteArray(Charsets.ISO_8859_1))
                is Int -> out.write(part)
                else -> error("unsupported $part")
            }
        }
        return out.toByteArray()
    }

    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
    private fun synchsafe(v: Int) = byteArrayOf((v shr 21 and 0x7F).toByte(), (v shr 14 and 0x7F).toByte(), (v shr 7 and 0x7F).toByte(), (v and 0x7F).toByte())

    private fun id3(major: Int, vararg frames: ByteArray, padding: Int = 16): ByteArray {
        val body = bytes(*frames, ByteArray(padding))
        return bytes("ID3", major, 0, 0, synchsafe(body.size), body)
    }

    private fun frame(major: Int, id: String, data: ByteArray): ByteArray = when (major) {
        2 -> bytes(id, byteArrayOf((data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()), data)
        3 -> bytes(id, be32(data.size), 0, 0, data)
        else -> bytes(id, synchsafe(data.size), 0, 0, data)
    }

    private fun uslt(encoding: Int, text: ByteArray, descriptor: ByteArray = ByteArray(0)): ByteArray {
        val terminator = if (encoding == 1 || encoding == 2) ByteArray(2) else ByteArray(1)
        return bytes(encoding, "eng", descriptor, terminator, text)
    }

    private fun read(data: ByteArray) = EmbeddedLyricsReader.read(data.inputStream())

    private fun vorbisBlock(vararg comments: String): ByteArray {
        val vendor = "test vendor".toByteArray()
        val out = ByteArrayOutputStream()
        out.write(le32(vendor.size)); out.write(vendor)
        out.write(le32(comments.size))
        comments.forEach { val c = it.toByteArray(Charsets.UTF_8); out.write(le32(c.size)); out.write(c) }
        return out.toByteArray()
    }

    private fun flac(vararg blocks: Pair<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        blocks.forEachIndexed { i, (type, data) ->
            val last = if (i == blocks.lastIndex) 0x80 else 0
            out.write(last or type)
            out.write(data.size shr 16); out.write(data.size shr 8); out.write(data.size)
            out.write(data)
        }
        out.write(ByteArray(64)) // "audio"
        return out.toByteArray()
    }

    /** One Ogg page holding the given packets (each under 255 bytes). */
    private fun oggPage(vararg packets: ByteArray): ByteArray {
        val header = bytes("OggS", 0, 0, ByteArray(8), ByteArray(4), ByteArray(4), ByteArray(4), packets.size)
        return bytes(header, packets.map { it.size }.map { byteArrayOf(it.toByte()) }.fold(ByteArray(0)) { a, b -> a + b }, *packets)
    }

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = bytes(*content)
        return bytes(be32(body.size + 8), type, body)
    }

    // ---- ID3 ------------------------------------------------------------------------------

    @Test
    fun `id3v2_3 latin-1 USLT is plain lyrics`() {
        val file = id3(3, frame(3, "TIT2", bytes(0, "Title")), frame(3, "USLT", uslt(0, "Line one\nLine two".toByteArray(Charsets.ISO_8859_1))))
        assertEquals(Lyrics.Plain("Line one\nLine two"), read(file + ByteArray(100)))
    }

    @Test
    fun `id3v2_3 UTF-16 USLT with BOM`() {
        val text = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Ça va".toByteArray(Charsets.UTF_16LE)
        val descriptor = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "d".toByteArray(Charsets.UTF_16LE)
        val file = id3(3, frame(3, "USLT", uslt(1, text, descriptor)))
        assertEquals(Lyrics.Plain("Ça va"), read(file))
    }

    @Test
    fun `id3v2_4 UTF-8 USLT holding LRC becomes synced`() {
        val file = id3(4, frame(4, "USLT", uslt(3, "[00:01.00]Hello\n[00:03.00]World".toByteArray(Charsets.UTF_8))))
        assertEquals(Lyrics.Synced(listOf(LyricLine(1_000, "Hello"), LyricLine(3_000, "World"))), read(file))
    }

    @Test
    fun `id3v2_2 ULT frame`() {
        val file = id3(2, frame(2, "ULT", uslt(0, "Old tag".toByteArray())))
        assertEquals(Lyrics.Plain("Old tag"), read(file))
    }

    @Test
    fun `SYLT in milliseconds wins over USLT`() {
        val sylt = bytes(3, "eng", 2, 1, 0, "First", 0, be32(1_000), "Second", 0, be32(4_500))
        val file = id3(3, frame(3, "USLT", uslt(0, "plain".toByteArray())), frame(3, "SYLT", sylt))
        assertEquals(Lyrics.Synced(listOf(LyricLine(1_000, "First"), LyricLine(4_500, "Second"))), read(file))
    }

    @Test
    fun `SYLT timed in MPEG frames is skipped`() {
        val sylt = bytes(3, "eng", 1, 1, 0, "First", 0, be32(10))
        assertNull(read(id3(3, frame(3, "SYLT", sylt))))
    }

    @Test
    fun `unsynchronised id3v2_3 tag is restored`() {
        // The writer inserts 0x00 after every 0xFF; frame sizes count the original bytes.
        val original = bytes(frame(3, "USLT", uslt(0, byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte()))), ByteArray(8))
        val body = ByteArrayOutputStream().apply {
            original.forEach { b -> write(b.toInt()); if (b == 0xFF.toByte()) write(0) }
        }.toByteArray()
        val file = bytes("ID3", 3, 0, 0x80, synchsafe(body.size), body)
        assertEquals(Lyrics.Plain("aÿb"), read(file))
    }

    @Test
    fun `mp3 without lyrics frames gives null`() {
        assertNull(read(id3(3, frame(3, "TIT2", bytes(0, "Title"))) + ByteArray(100)))
    }

    // ---- FLAC and Ogg ---------------------------------------------------------------------

    @Test
    fun `flac LYRICS comment after other blocks`() {
        val file = flac(0 to ByteArray(34), 6 to ByteArray(500), 4 to vorbisBlock("TITLE=Song", "lyrics=Hello there"))
        assertEquals(Lyrics.Plain("Hello there"), read(file))
    }

    @Test
    fun `flac prefers synced lyrics among comments`() {
        val file = flac(0 to ByteArray(34), 4 to vorbisBlock("UNSYNCEDLYRICS=plain words", "SYNCEDLYRICS=[00:02.00]Timed"))
        assertEquals(Lyrics.Synced(listOf(LyricLine(2_000, "Timed"))), read(file))
    }

    @Test
    fun `flac behind an id3 tag`() {
        val file = id3(3, frame(3, "TIT2", bytes(0, "T"))) + flac(0 to ByteArray(34), 4 to vorbisBlock("LYRICS=Behind ID3"))
        assertEquals(Lyrics.Plain("Behind ID3"), read(file))
    }

    @Test
    fun `flac without comments gives null`() {
        assertNull(read(flac(0 to ByteArray(34))))
    }

    @Test
    fun `ogg vorbis comment header`() {
        val identification = bytes(1, "vorbis", ByteArray(23))
        val comments = bytes(3, "vorbis", vorbisBlock("ARTIST=A", "LYRICS=From Ogg"), 1)
        val file = oggPage(identification) + oggPage(comments)
        assertEquals(Lyrics.Plain("From Ogg"), read(file))
    }

    @Test
    fun `opus tags`() {
        val head = bytes("OpusHead", ByteArray(11))
        val tags = bytes("OpusTags", vorbisBlock("LYRICS=[00:00.50]Opus line"))
        val file = oggPage(head, tags)
        assertEquals(Lyrics.Synced(listOf(LyricLine(500, "Opus line"))), read(file))
    }

    // ---- MP4 ------------------------------------------------------------------------------

    private fun mp4WithLyrics(text: String, fullMeta: Boolean = true): ByteArray {
        val data = box("data", be32(1), be32(0), text.toByteArray(Charsets.UTF_8))
        val ilst = box("ilst", box("©nam", box("data", be32(1), be32(0), "Title".toByteArray())), box("©lyr", data))
        val hdlr = box("hdlr", ByteArray(25))
        val meta = if (fullMeta) box("meta", ByteArray(4), hdlr, ilst) else box("meta", hdlr, ilst)
        val moov = box("moov", box("mvhd", ByteArray(100)), box("udta", meta))
        return bytes(box("ftyp", "M4A ".toByteArray(), be32(0)), box("mdat", ByteArray(2_000)), moov)
    }

    @Test
    fun `m4a lyr atom after the audio`() {
        assertEquals(Lyrics.Plain("From M4A"), read(mp4WithLyrics("From M4A")))
    }

    @Test
    fun `quicktime style meta box`() {
        assertEquals(Lyrics.Plain("QuickTime"), read(mp4WithLyrics("QuickTime", fullMeta = false)))
    }

    @Test
    fun `m4a without lyrics gives null`() {
        val moov = box("moov", box("mvhd", ByteArray(100)))
        assertNull(read(bytes(box("ftyp", "M4A ".toByteArray(), be32(0)), moov)))
    }

    // ---- robustness -----------------------------------------------------------------------

    @Test
    fun `unknown or broken data gives null`() {
        assertNull(read(ByteArray(0)))
        assertNull(read("RIFF....WAVEfmt ".toByteArray()))
        assertNull(read(id3(3, frame(3, "USLT", uslt(0, "cut".toByteArray()))).copyOf(14)))
        assertNull(read(flac(0 to ByteArray(34), 4 to vorbisBlock("LYRICS=x")).copyOf(30)))
    }
}
