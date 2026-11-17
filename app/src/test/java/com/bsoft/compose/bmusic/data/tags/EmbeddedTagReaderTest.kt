package com.bsoft.compose.bmusic.data.tags

import com.bsoft.compose.bmusic.data.lyrics.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/** ReplayGain from each tag format; lyrics are covered by EmbeddedLyricsReaderTest. */
class EmbeddedTagReaderTest {
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

    private fun id3v23(vararg frames: ByteArray): ByteArray {
        val body = bytes(*frames, ByteArray(16))
        return bytes("ID3", 3, 0, 0, synchsafe(body.size), body)
    }

    private fun frame(id: String, data: ByteArray) = bytes(id, be32(data.size), 0, 0, data)

    private fun txxx(description: String, value: String) = bytes(0, description, 0, value)

    private fun vorbisBlock(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "v".toByteArray()
        out.write(le32(vendor.size)); out.write(vendor); out.write(le32(comments.size))
        comments.forEach { val c = it.toByteArray(Charsets.UTF_8); out.write(le32(c.size)); out.write(c) }
        return out.toByteArray()
    }

    private fun flac(comments: ByteArray): ByteArray = bytes(
        "fLaC", 0, 0, 0, 34, ByteArray(34),
        0x84, comments.size shr 16, comments.size shr 8, comments.size and 0xFF, comments
    )

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = bytes(*content)
        return bytes(be32(body.size + 8), type, body)
    }

    private fun read(data: ByteArray) = EmbeddedTagReader.read(data.inputStream())

    @Test
    fun `mp3 TXXX ReplayGain frames`() {
        val file = id3v23(
            frame("TIT2", bytes(0, "Song")),
            frame("TXXX", txxx("replaygain_track_gain", "-7.10 dB")),
            frame("TXXX", txxx("REPLAYGAIN_TRACK_PEAK", "0.95")),
            frame("TXXX", txxx("REPLAYGAIN_ALBUM_GAIN", "-8.00 dB")),
        )
        assertEquals(ReplayGain(trackGainDb = -7.1f, trackPeak = 0.95f, albumGainDb = -8f), read(file).replayGain)
    }

    @Test
    fun `mp3 with lyrics and gain returns both`() {
        val file = id3v23(
            frame("USLT", bytes(0, "eng", 0, "Words")),
            frame("TXXX", txxx("REPLAYGAIN_TRACK_GAIN", "-3 dB")),
        )
        val tags = read(file)
        assertEquals(Lyrics.Plain("Words"), tags.lyrics)
        assertEquals(-3f, tags.replayGain.trackGainDb)
    }

    @Test
    fun `flac comments`() {
        val file = flac(vorbisBlock("REPLAYGAIN_TRACK_GAIN=-4.5 dB", "replaygain_album_gain=-5.5 dB"))
        assertEquals(ReplayGain(trackGainDb = -4.5f, albumGainDb = -5.5f), read(file).replayGain)
    }

    @Test
    fun `flac behind id3 merges both tags`() {
        val file = id3v23(frame("TXXX", txxx("REPLAYGAIN_TRACK_GAIN", "-2 dB"))) +
            flac(vorbisBlock("REPLAYGAIN_TRACK_GAIN=-9 dB", "REPLAYGAIN_ALBUM_GAIN=-6 dB", "LYRICS=From FLAC"))
        val tags = read(file)
        assertEquals(ReplayGain(trackGainDb = -2f, albumGainDb = -6f), tags.replayGain) // ID3 wins
        assertEquals(Lyrics.Plain("From FLAC"), tags.lyrics)
    }

    @Test
    fun `opus R128 gain`() {
        val head = bytes("OpusHead", ByteArray(11))
        val tags = bytes("OpusTags", vorbisBlock("R128_TRACK_GAIN=-1280"))
        val page = bytes("OggS", 0, 0, ByteArray(20), 2, head.size, tags.size, head, tags)
        assertEquals(0f, read(page).replayGain.trackGainDb) // -1280/256 + 5
    }

    @Test
    fun `m4a iTunes freeform atoms`() {
        fun freeform(name: String, value: String) = box(
            "----",
            box("mean", ByteArray(4), "com.apple.iTunes".toByteArray()),
            box("name", ByteArray(4), name.toByteArray()),
            box("data", be32(1), be32(0), value.toByteArray()),
        )
        val ilst = box(
            "ilst",
            box("©nam", box("data", be32(1), be32(0), "Title".toByteArray())),
            freeform("replaygain_track_gain", "-6.20 dB"),
            freeform("replaygain_track_peak", "0.990000"),
            box("©lyr", box("data", be32(1), be32(0), "M4A words".toByteArray())),
        )
        val moov = box("moov", box("udta", box("meta", ByteArray(4), box("hdlr", ByteArray(25)), ilst)))
        val tags = read(bytes(box("ftyp", "M4A ".toByteArray(), be32(0)), moov))
        assertEquals(ReplayGain(trackGainDb = -6.2f, trackPeak = 0.99f), tags.replayGain)
        assertEquals(Lyrics.Plain("M4A words"), tags.lyrics)
    }

    @Test
    fun `nothing tagged gives empty values`() {
        assertEquals(EmbeddedTags.NONE, read(ByteArray(0)))
        assertEquals(ReplayGain(), read(id3v23(frame("TIT2", bytes(0, "Song")))).replayGain)
    }
}
