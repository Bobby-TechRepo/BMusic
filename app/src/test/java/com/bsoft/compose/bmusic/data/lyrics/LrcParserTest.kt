package com.bsoft.compose.bmusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    private fun synced(text: String) = LrcParser.parse(text) as Lyrics.Synced

    @Test
    fun `parses timed lines in order`() {
        val lyrics = synced("[00:12.50]Second\n[00:01.00]First\n[01:02.345]Third")
        assertEquals(
            listOf(LyricLine(1_000, "First"), LyricLine(12_500, "Second"), LyricLine(62_345, "Third")),
            lyrics.lines
        )
    }

    @Test
    fun `fraction digits mean tenths, hundredths or thousandths`() {
        val lyrics = synced("[00:01.5]a\n[00:02.05]b\n[00:03.005]c\n[00:04]d\n[00:05:20]e")
        assertEquals(listOf(1_500L, 2_050L, 3_005L, 4_000L, 5_200L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `a line with several timestamps repeats`() {
        val lyrics = synced("[00:10.00][00:30.00] Chorus")
        assertEquals(listOf(LyricLine(10_000, "Chorus"), LyricLine(30_000, "Chorus")), lyrics.lines)
    }

    @Test
    fun `header tags are ignored and offset shifts lines earlier`() {
        val lyrics = synced("[ar:Someone]\n[ti:Song]\n[offset:+500]\n[00:02.00]Hello\n[00:00.20]Start")
        assertEquals(listOf(LyricLine(0, "Start"), LyricLine(1_500, "Hello")), lyrics.lines)
    }

    @Test
    fun `negative offset shifts lines later`() {
        assertEquals(3_000L, synced("[offset:-1000]\n[00:02.00]Hi").lines.single().timeMs)
    }

    @Test
    fun `enhanced word timings are removed`() {
        assertEquals("Hello world", synced("[00:01.00]<00:01.00>Hello <00:01.50>world").lines.single().text)
    }

    @Test
    fun `empty timed lines stay as gaps`() {
        val lyrics = synced("[00:01.00]Sing\n[00:05.00]\n[00:09.00]Again")
        assertEquals("", lyrics.lines[1].text)
    }

    @Test
    fun `text without timestamps is plain lyrics`() {
        val lyrics = LrcParser.parse("\uFEFFFirst line\r\nSecond line\r\n") as Lyrics.Plain
        assertEquals("First line\nSecond line", lyrics.text)
    }

    @Test
    fun `nothing to show gives null`() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("  \n \n"))
        assertNull(LrcParser.parse("[ar:Someone]\n[ti:Song]"))
        assertNull(LrcParser.parse("[00:01.00]\n[00:02.00]"))
    }

    @Test
    fun `indexAt finds the line being sung`() {
        val lyrics = synced("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, lyrics.indexAt(500))
        assertEquals(0, lyrics.indexAt(1_000))
        assertEquals(0, lyrics.indexAt(4_999))
        assertEquals(1, lyrics.indexAt(5_000))
        assertEquals(2, lyrics.indexAt(60_000))
        assertTrue(Lyrics.Synced(emptyList()).indexAt(1_000) == -1)
    }

    @Test
    fun `decodeText handles byte order marks`() {
        assertEquals("[00:01.00]Hi", decodeText("\uFEFF[00:01.00]Hi".toByteArray(Charsets.UTF_8)))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Héllo".toByteArray(Charsets.UTF_16LE)
        assertEquals("Héllo", decodeText(utf16))
    }
}
